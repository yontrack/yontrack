#!/usr/bin/env bash
#
# Nightly security rescan of the released builds (#1751).
#
# Called by .github/workflows/security-rescan.yml. The logic lives here rather than inline in
# the workflow so that scripts/security-rescan-test.sh can exercise it against a stubbed `gh`
# and a stubbed GraphQL client, the way #1749 and #1750 do.
#
# Why a rescan at all: a CVE published after a release, or a secret committed and then found,
# reaches Yontrack only if something goes looking. Nothing pushes to a released commit, so
# nothing re-runs its pipeline; this workflow is what re-measures it.
#
# Usage: scripts/security-rescan.sh targets|select|stamp-config|setup-stamp|secrets|count-secrets ...
#
#   targets PROJECT              Resolves the released builds to rescan and writes `count`,
#                                `targets` (a one-line JSON array, ready for a matrix) and
#                                `newest_project`, `newest_branch`, `newest_build`,
#                                `newest_version` to $GITHUB_OUTPUT.
#   select [FILE]                The pure half of `targets`: turns a `builds` answer read from
#                                FILE, or from stdin, into the JSON array of targets.
#   stamp-config NAME            Prints `{dataType, dataTypeConfig}` for the validation stamp
#                                NAME as .yontrack/ci.yaml declares it.
#   setup-stamp PROJECT BRANCH NAME
#                                Creates NAME on PROJECT/BRANCH with that data type, or updates
#                                it to it.
#   secrets REPOSITORY           Counts the open secret-scanning alerts of REPOSITORY and prints
#                                `secrets=N`, also written to $GITHUB_OUTPUT.
#   count-secrets [FILE]         The pure half of `secrets`.
#
# Environment:
#   YONTRACK_URL, YONTRACK_TOKEN   the instance the builds are read from and reported to
#   GH_TOKEN                       for `secrets`: a token that can read the secret-scanning
#                                  alerts. That is `SECRET_SCANNING_TOKEN`, and it is confined
#                                  to this workflow (#1747) - ci.yml must never see it.
#   SECURITY_RESCAN_CI_CONFIG      CI configuration to read the stamps from (default:
#                                  .yontrack/ci.yaml at the root of the repository)
#
# Which builds, and why by version:
#
#   * The latest build promoted to RELEASE for the current minor and for the previous one -
#     the two minors users can be running, and the one a patch would target.
#   * Resolved by promotion and by version, never by branch name. Per doc/dev-guide/patch-release.md
#     there is at most one live `release/X.Y` branch, it is cut on demand and usually none
#     exists, so a released build may sit on `main` just as well as on a release branch. The
#     promotion says it was released; the display name says which version it is.
#   * A promoted build whose display name is not a version has no release property, so nothing
#     was published under it and there is no image tag to scan. It is skipped.
#
# What only the count of the secret-scanning alerts is ever printed for: the alert bodies carry
# the secrets themselves. The count is the signal; anything more would put them in a workflow
# log that is far easier to read than the Security tab they belong in.
#
# Requires jq and yq (mikefarah's, v4) on the PATH, plus `gh` for `secrets` and curl for
# everything talking to Yontrack. ubuntu-latest carries all four.

set -uo pipefail

SRS_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

srs_fail() { echo "ERROR: $*" >&2; return 1; }

srs_output() {
    echo "$1=$2"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$1=$2" >> "$GITHUB_OUTPUT"
    fi
}

# ---------------------------------------------------------------------------------------------
# Yontrack
# ---------------------------------------------------------------------------------------------

# Runs a GraphQL query against Yontrack and echoes the response body.
#
# Same shape as `rel_graphql` in scripts/release.sh: it fails when the transport fails, when the
# status is not 2xx, or when the payload carries GraphQL errors - a 200 with an `errors` array
# is how an expired token looks.
#
# Usage: srs_graphql QUERY [VARIABLES_JSON]
#
# Everything variable goes through the variables, never through string interpolation into the
# query. Beyond the obvious, it is the only way to send a JSON object: the `JSON` scalar reads an
# inline string literal as a text node, so a data type configuration pasted into the query would
# arrive as a string and be rejected, while the same object sent as a variable arrives as an
# object.
#
# The one indirection the tests replace, so that nothing in this file needs a Yontrack instance
# to be exercised.
srs_graphql() {
    local query="$1" variables="${2:-}" payload response body status errors
    [ -n "$variables" ] || variables='{}'
    [ -n "${YONTRACK_URL:-}" ] || { srs_fail "YONTRACK_URL is not set."; return 1; }
    payload="$(jq -nc --arg q "$query" --argjson v "$variables" '{query: $q, variables: $v}')" || return 1

    response="$(curl -sS --max-time 60 -w '\n%{http_code}' \
        -X POST "${YONTRACK_URL%/}/graphql" \
        -H 'Content-Type: application/json' \
        -H "X-Ontrack-Token: ${YONTRACK_TOKEN:-}" \
        -d "$payload" 2>&1)" || {
        srs_fail "Could not reach ${YONTRACK_URL%/}/graphql: $response"
        return 1
    }

    status="${response##*$'\n'}"
    body="${response%$'\n'*}"

    case "$status" in
        2*) ;;
        *) srs_fail "Yontrack answered HTTP $status: $body"; return 1 ;;
    esac

    errors="$(echo "$body" | jq -r '(.errors // []) | map(.message) | join("; ")')"
    [ -n "$errors" ] && { srs_fail "Yontrack answered with errors: $errors"; return 1; }

    echo "$body"
}

# ---------------------------------------------------------------------------------------------
# The builds to rescan
# ---------------------------------------------------------------------------------------------

# The builds promoted to RELEASE across the whole project, most recent first.
#
# `buildProjectFilter` and not `buildBranchFilter`: the search has to span every branch, because
# which branch a release was cut from is exactly what this workflow must not assume. 50 is far
# more than the two minors ever need and still one page.
srs_fetch_released() {
    local project="$1" answer
    # shellcheck disable=SC2016  # deliberate: the $ are GraphQL variables, not shell ones
    answer="$(srs_graphql 'query ReleasedBuilds($project: String!, $filter: BuildSearchForm!) {
        builds(project: $project, buildProjectFilter: $filter) {
            id
            name
            displayName
            branch {
                name
                project {
                    name
                }
            }
        }
    }' "$(jq -nc --arg project "$project" '{
        project: $project,
        filter: {promotionName: "RELEASE", maximumCount: 50}
    }')")" || return 1
    printf '%s' "$answer" | jq -e -c '.data.builds' 2>/dev/null \
        || { srs_fail "Could not read the released builds out of the answer"; return 1; }
}

# Turns the `builds` array into the JSON array of targets, newest first.
#
# Versions are compared field by field as numbers: `sort_by(.version)` would put 5.4.9 after
# 5.4.10, and a text comparison of minors would call 5.10 older than 5.4.
#
# An answer that cannot be read is an error, never an empty list: a failed call must not read as
# "nothing has been released", which would exit the workflow successfully having scanned nothing.
srs_select() {
    local input="${1:--}"
    if [ "$input" != "-" ] && [ ! -f "$input" ]; then
        srs_fail "No builds file at $input"
        return 1
    fi
    # `-s` slurps: an empty answer is a list of no documents rather than no output at all, which
    # is the difference between an error and jq 1.6 quietly exiting 0 on an empty file.
    jq -e -c -s '
        if length != 1 or (.[0] | type) != "array" then
            error("not a list of builds")
        else
            [
                .[0][]
                | select((.displayName // "") | test("^[0-9]+\\.[0-9]+\\.[0-9]+$"))
                | . as $build
                | (.displayName | split(".") | map(tonumber)) as $v
                | {
                    project: $build.branch.project.name,
                    branch: $build.branch.name,
                    build: $build.name,
                    version: $build.displayName,
                    major: $v[0],
                    minor: $v[1],
                    patch: $v[2]
                  }
            ]
            # The highest patch of each minor...
            | group_by([.major, .minor])
            | map(sort_by(.patch) | last)
            # ... and of those, the current minor and the one before it.
            | sort_by([.major, .minor])
            | reverse
            | .[0:2]
            | map({project, branch, build, version})
        end
    ' "$input" || { srs_fail "Could not read the released builds"; return 1; }
}

srs_targets() {
    local project="${1:-}" builds targets count
    [ -n "$project" ] || { srs_fail "No project"; return 1; }

    echo "Looking for the builds of $project promoted to RELEASE" >&2
    builds="$(srs_fetch_released "$project")" || return 1
    targets="$(printf '%s' "$builds" | srs_select)" || return 1
    count="$(printf '%s' "$targets" | jq 'length')" || return 1

    if [ "$count" = "0" ]; then
        echo "No released build to rescan."
    else
        printf '%s' "$targets" | jq -r '.[] | "Rescanning \(.version) - build \(.build) on \(.project)/\(.branch)"'
    fi

    srs_output count "$count"
    # One line, as a matrix expression needs: `jq -c` never wraps, and a version and a build name
    # hold no newline.
    srs_output targets "$targets"
    if [ "$count" != "0" ]; then
        # The newest target carries SECURITY.SECRETS on its own - the open alert count is a
        # property of the repository today, not of a release, and reporting it on both targets
        # would say the same number twice.
        srs_output newest_project "$(printf '%s' "$targets" | jq -r '.[0].project')"
        srs_output newest_branch "$(printf '%s' "$targets" | jq -r '.[0].branch')"
        srs_output newest_build "$(printf '%s' "$targets" | jq -r '.[0].build')"
        srs_output newest_version "$(printf '%s' "$targets" | jq -r '.[0].version')"
    fi
}

# ---------------------------------------------------------------------------------------------
# Validation stamps on the branch being rescanned
# ---------------------------------------------------------------------------------------------

# The data type of a stamp, as .yontrack/ci.yaml declares it.
#
# Read from the CI configuration rather than restated here so that there is one definition of
# the thresholds: a rescan reporting against different ones from CI would make two runs of the
# same stamp incomparable.
#
# The alias table mirrors what the server resolves: a CI configuration names a data type either
# by an alias or by its fully qualified class name, and `CIConfigurationParserImpl` passes an
# unknown name straight through as a class name. Same rule here.
srs_stamp_config() {
    local name="${1:-}" config="${SECURITY_RESCAN_CI_CONFIG:-$SRS_REPO_ROOT/.yontrack/ci.yaml}"
    local block type data_type
    [ -n "$name" ] || { srs_fail "No validation stamp"; return 1; }
    [ -f "$config" ] || { srs_fail "No CI configuration at $config"; return 1; }

    block="$(yq -o=json -I=0 ".configuration.defaults.branch.validations.\"$name\"" "$config")" \
        || { srs_fail "Could not read $config"; return 1; }
    if [ -z "$block" ] || [ "$block" = "null" ]; then
        srs_fail "$config declares no validation stamp $name"
        return 1
    fi

    # `description` is documentation, not a data type - the CI configuration parser drops it the
    # same way before deciding what the remaining key means.
    if [ "$(printf '%s' "$block" | jq -r 'del(.description) | keys | length')" != "1" ]; then
        srs_fail "$name declares no single data type in $config"
        return 1
    fi
    type="$(printf '%s' "$block" | jq -r 'del(.description) | keys[0]')" || return 1

    case "$type" in
        chml) data_type="net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType" ;;
        number) data_type="net.nemerosa.ontrack.extension.general.validation.ThresholdNumberValidationDataType" ;;
        metrics) data_type="net.nemerosa.ontrack.extension.general.validation.MetricsValidationDataType" ;;
        percentage) data_type="net.nemerosa.ontrack.extension.general.validation.ThresholdPercentageValidationDataType" ;;
        tests) data_type="net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType" ;;
        *) data_type="$type" ;;
    esac

    printf '%s' "$block" | jq -c --arg type "$type" --arg dataType "$data_type" \
        '{dataType: $dataType, dataTypeConfig: .[$type]}'
}

# Creates the stamp on the branch, or updates it to the data type CI declares.
#
# This is what makes a rescan of a build on a release branch work. A `release/X.Y` branch cut
# before these stamps existed has a `.yontrack/ci.yaml` that does not declare them, so its
# branch has no such stamp. Validating anyway does not fail outright - the project carries the
# `AutoValidationStampProperty` that `AutoProjectCIConfigExtension` sets on every CI
# configuration, with `autoCreateIfNotPredefined`, so `getOrCreateValidationStamp` creates one -
# but it creates it with *no data type*, and `ValidationDataTypeServiceImpl.validateData` then
# throws `ValidationRunDataStatusRequiredBecauseNoDataTypeException` for a run that carries data
# and no explicit status. The rescan would fail on exactly the branch it exists for.
#
# Forcing a `--status` instead would be worse: a status given explicitly wins over the computed
# one, so every rescan would report a status it decided itself rather than one the thresholds
# computed, and the stamp would mean two different things on two branches.
#
# `setupValidationStamp` rather than `createValidationStamp`: it is create-or-update, so the
# common case - the target build is on `main`, where the stamp already exists with exactly this
# configuration - is a no-op write, and the rescan needs no branch of its own for it.
srs_setup_stamp() {
    local project="${1:-}" branch="${2:-}" name="${3:-}" config answer errors
    [ -n "$project" ] || { srs_fail "No project"; return 1; }
    [ -n "$branch" ] || { srs_fail "No branch"; return 1; }
    [ -n "$name" ] || { srs_fail "No validation stamp"; return 1; }

    config="$(srs_stamp_config "$name")" || return 1
    echo "Setting up $name on $project/$branch" >&2

    # shellcheck disable=SC2016  # deliberate: the $ are GraphQL variables, not shell ones
    answer="$(srs_graphql 'mutation SetupValidationStamp(
        $project: String!,
        $branch: String!,
        $validation: String!,
        $dataType: String,
        $dataTypeConfig: JSON
    ) {
        setupValidationStamp(input: {
            project: $project,
            branch: $branch,
            validation: $validation,
            dataType: $dataType,
            dataTypeConfig: $dataTypeConfig
        }) {
            errors {
                message
            }
        }
    }' "$(printf '%s' "$config" | jq -c \
        --arg project "$project" --arg branch "$branch" --arg validation "$name" '{
            project: $project,
            branch: $branch,
            validation: $validation,
            dataType: .dataType,
            dataTypeConfig: .dataTypeConfig
        }')")" || return 1

    errors="$(printf '%s' "$answer" | jq -r '(.data.setupValidationStamp.errors // []) | map(.message) | join("; ")')" \
        || { srs_fail "Could not read the answer to setupValidationStamp: $answer"; return 1; }
    [ -n "$errors" ] && { srs_fail "Could not set up $name on $project/$branch: $errors"; return 1; }
    return 0
}

# ---------------------------------------------------------------------------------------------
# Secret-scanning alerts
# ---------------------------------------------------------------------------------------------

# Prints `secrets=N` for an answer to the secret-scanning alerts API - one page or several
# printed back to back, as `gh api --paginate` does.
#
# `jq -s` slurps however many arrays the answer holds, and each of them must be an array: an
# error object - a 403 from a token that cannot read the alerts, a 404 from a repository with
# secret scanning off - is not a page of zero alerts. Only the length is ever emitted; the alert
# objects are never printed.
srs_count_secrets() {
    local input="${1:--}"
    if [ "$input" != "-" ] && [ ! -f "$input" ]; then
        srs_fail "No alerts file at $input"
        return 1
    fi
    jq -e -r -s '
        if length == 0 or any(.[]; type != "array") then
            error("not a list of secret-scanning alerts")
        else
            "secrets=\([.[][]] | length)"
        end
    ' "$input" || { srs_fail "Could not read the secret-scanning alerts"; return 1; }
}

# The open secret-scanning alerts of the repository, as a count and nothing else.
#
# The answer is held in a variable and never echoed, never written to a file and never passed
# through anything that logs: `gh` is given the token through the environment, and what leaves
# this function is one number.
srs_secrets() {
    local repository="${1:-}" alerts count
    [ -n "$repository" ] || { srs_fail "No repository"; return 1; }

    echo "Counting the open secret-scanning alerts of $repository" >&2
    # `gh`'s own diagnostics are left on stderr - they carry the HTTP status, never an alert -
    # but its stdout is captured and never echoed.
    alerts="$(gh api --paginate \
        "repos/$repository/secret-scanning/alerts?state=open&per_page=100")" \
        || { srs_fail "Could not fetch the secret-scanning alerts of $repository"; return 1; }

    count="$(printf '%s' "$alerts" | srs_count_secrets)" || return 1
    srs_output secrets "${count#secrets=}"
}

srs_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        targets) srs_targets "$@" ;;
        select) srs_select "$@" ;;
        stamp-config) srs_stamp_config "$@" ;;
        setup-stamp) srs_setup_stamp "$@" ;;
        secrets) srs_secrets "$@" ;;
        count-secrets) srs_count_secrets "$@" ;;
        *)
            echo "Usage: $0 targets|select|stamp-config|setup-stamp|secrets|count-secrets ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${SECURITY_RESCAN_LIB_ONLY:-}" ]; then
    srs_main "$@"
    exit $?
fi
