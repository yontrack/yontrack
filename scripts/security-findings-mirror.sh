#!/usr/bin/env bash
#
# Mirrors the security findings of `v6`'s CI onto v6.dev.yontrack.com (#1869).
#
# Every branch's CI reports to self.dev.yontrack.com, which runs 5.x until 6.0 is released and
# cannot take findings. v6.dev runs the last BRONZE build of `v6`, with the licence for the native
# formats, so `v6`'s CI keeps reporting CHML to self.dev as before and, in addition, sends the
# reports themselves to v6.dev - into the `yontrack-ci` project, which the demo seed's reset
# spares (DemoSeed.CI_MIRROR_PROJECT). `yontrack` is taken there by the seed's changelog project.
#
# self.dev stays the instance of record: the mirror never fails a build. Every failure is a
# `::warning::`, since v6.dev may well be half-way through its own deployment.
#
# The mirror goes at the 6.0 cutover, when the stamps move to findings on self.dev (#1875). The
# `guard` command is what keeps that from being forgotten.
#
# Called by ci.yml's `security-images` job and codeql.yml's `report` job. The logic lives here
# rather than inline so that scripts/security-findings-mirror-test.sh can exercise it against a
# stubbed `gh` and `yontrack`.
#
# Usage: scripts/security-findings-mirror.sh COMMAND ...
#
#   enabled REF_NAME          Writes `enabled=true` to $GITHUB_OUTPUT on the `v6` branch, and
#                             `enabled=false` anywhere else.
#   guard REF_NAME VERSION_FILE
#                             On `main` and `release/*` with a 6.x VERSION: warns while
#                             self.dev (the selected CLI configuration) runs 5.x, and fails once
#                             it runs 6.x - the findings must then move to self.dev (#1875).
#                             Passes silently anywhere else.
#   configure URL TOKEN       Configures the CLI for the mirror, in a configuration file of its
#                             own so that self.dev's stays selected for everything else.
#   publish BRANCH BUILD STAMP FORMAT KIND REPORT [RUN INFO FLAGS...]
#                             Creates the branch, the build and the stamp on the mirror if need
#                             be - the stamp with the thresholds of .yontrack/ci.yaml - then
#                             sends REPORT with `validate … findings`.
#   fetch-code REPOSITORY REF OUTPUT
#                             Fetches the open and dismissed CodeQL alerts of REF and writes
#                             them to OUTPUT as a report in the neutral `findings` format.
#   code-findings [FILE]      Converts a code-scanning alerts answer - pages printed back to
#                             back, as `gh api --paginate` does - read from FILE or stdin.
#   thresholds STAMP          Prints the CHML thresholds of STAMP in .yontrack/ci.yaml, as the
#                             configuration of a findings stamp.
#
# Environment:
#   SECURITY_FINDINGS_MIRROR_CONFIG  CLI configuration file of the mirror (default:
#                                    $RUNNER_TEMP/yontrack-findings-mirror.yaml)
#
# Requires jq and yq (mikefarah's, v4) on the PATH, plus `gh` (authenticated through GH_TOKEN)
# for fetch-code and the Yontrack CLI 5.8.0 or later for the rest. ubuntu-latest carries jq, yq
# and gh.

set -uo pipefail

SFM_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The project on the mirror, and the one branch whose CI mirrors into it.
SFM_PROJECT="yontrack-ci"
SFM_ENABLED_BRANCH="v6"
# The issue which switches everything back to self.dev.
SFM_SWITCH_BACK="#1875"
SFM_DATA_TYPE="net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType"
# The stamp is set up through `graphql`, its configuration as a JSON variable: `validation-stamp
# setup generic` splices `--data-config` into the query text, where the quoted keys of a JSON
# object are a syntax error.
# shellcheck disable=SC2016  # GraphQL variables, not shell ones
SFM_SETUP_STAMP='mutation($project: String!, $branch: String!, $validation: String!, $dataType: String, $dataTypeConfig: JSON) { setupValidationStamp(input: {project: $project, branch: $branch, validation: $validation, dataType: $dataType, dataTypeConfig: $dataTypeConfig}) { errors { message } } }'

sfm_fail() { echo "ERROR: $*" >&2; return 1; }
sfm_warn() { echo "::warning title=Security findings mirror::$*"; }

sfm_config() { echo "${SECURITY_FINDINGS_MIRROR_CONFIG:-${RUNNER_TEMP:-/tmp}/yontrack-findings-mirror.yaml}"; }

sfm_enabled() {
    local ref_name="${1:-}" enabled=false
    [ "$ref_name" = "$SFM_ENABLED_BRANCH" ] && enabled=true
    echo "enabled=$enabled"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "enabled=$enabled" >> "$GITHUB_OUTPUT"
    fi
}

# Merging `v6` into `main` does not bring self.dev to 6.0 - only the 6.0.0 release does, its
# slot taking RELEASE builds alone. Hence two stages. Before self.dev runs 6.x, a warning: failing
# would hold back the very release which brings it there. After, an error: from then on, a 6.x
# build reporting CHML to self.dev is the switch-back being forgotten.
sfm_guard() {
    local ref_name="${1:-}" version_file="${2:-}" major answer self_version self_major
    case "$ref_name" in
        main | release/*) ;;
        *) return 0 ;;
    esac
    [ -f "$version_file" ] || { sfm_fail "No VERSION file at $version_file"; return 1; }
    major="$(cut -d. -f1 < "$version_file" | tr -d '[:space:]')"
    [[ "$major" =~ ^[0-9]+$ ]] || { sfm_fail "Cannot read a major version from $version_file"; return 1; }
    [ "$major" -ge 6 ] || return 0

    if ! answer="$(yontrack graphql --query '{ info { version { display } } }' 2>/dev/null)" \
        || ! self_version="$(printf '%s' "$answer" | jq -e -r '.info.version.display' 2>/dev/null)"; then
        sfm_warn "A $major.x build, and the version of the Yontrack it reports to could not be read. Once that instance runs 6.x, the security stamps move to findings there: see $SFM_SWITCH_BACK."
        return 0
    fi
    self_major="${self_version%%.*}"
    if [[ "$self_major" =~ ^[0-9]+$ ]] && [ "$self_major" -ge 6 ]; then
        echo "::error title=Security findings::A $major.x build on $ref_name, and the Yontrack it reports to runs $self_version: the security stamps must move to findings there. See $SFM_SWITCH_BACK."
        return 1
    fi
    sfm_warn "A $major.x build on $ref_name reports CHML only, since the Yontrack it reports to runs $self_version. Once it runs 6.x, the security stamps move to findings there: see $SFM_SWITCH_BACK."
}

sfm_configure() {
    local url="${1:-}" token="${2:-}"
    if [ -z "$url" ] || [ -z "$token" ]; then
        sfm_warn "No URL or no token for the mirror: nothing will be mirrored."
        return 0
    fi
    yontrack --config "$(sfm_config)" config create mirror "$url" --token "$token" --override > /dev/null \
        || sfm_warn "The CLI could not be configured for the mirror at $url: nothing will be mirrored."
    return 0
}

# Prints the CHML thresholds of a stamp in .yontrack/ci.yaml - `warningLevel`, `warningValue`,
# `failedLevel`, `failedValue` - which is also the shape `setupValidationStamp` reads the
# configuration of a findings stamp in (`fromConfigForm`). Read from there rather than restated,
# so the mirror trips where self.dev's stamp does.
sfm_thresholds() {
    local stamp="${1:-}"
    [ -n "$stamp" ] || { sfm_fail "No stamp"; return 1; }
    STAMP="$stamp" yq -o=json '.configuration.defaults.branch.validations[strenv(STAMP)].chml' \
        "$SFM_REPO_ROOT/.yontrack/ci.yaml" \
        | jq -e -c '
            if .warningLevel == null or .warningValue == null
                or .failedLevel == null or .failedValue == null then
                error("no CHML thresholds")
            else
                {warningLevel, warningValue, failedLevel, failedValue}
            end
        ' 2>/dev/null || { sfm_fail "No CHML thresholds for $stamp in .yontrack/ci.yaml"; return 1; }
}

# Direct calls, never `ci config`: the environments, slots and notifications of .yontrack/ci.yaml
# must not land on v6.dev.
sfm_publish() {
    local branch="${1:-}" build="${2:-}" stamp="${3:-}" format="${4:-}" kind="${5:-}" report="${6:-}"
    local config thresholds
    if [ $# -ge 6 ]; then shift 6; else shift $#; fi
    if [ -z "$branch" ] || [ -z "$build" ] || [ -z "$stamp" ] || [ -z "$format" ] || [ -z "$kind" ]; then
        sfm_warn "$stamp not mirrored: missing branch, build, stamp, format or kind."
        return 0
    fi
    if [ ! -f "$report" ]; then
        sfm_warn "$stamp not mirrored: no report at $report."
        return 0
    fi
    config="$(sfm_config)"
    if [ ! -f "$config" ]; then
        sfm_warn "$stamp not mirrored: the CLI is not configured for the mirror."
        return 0
    fi
    thresholds="$(sfm_thresholds "$stamp")" || { sfm_warn "$stamp not mirrored: no thresholds."; return 0; }

    if ! {
        yontrack --config "$config" branch setup --project "$SFM_PROJECT" --branch "$branch" > /dev/null \
            && yontrack --config "$config" build setup --project "$SFM_PROJECT" --branch "$branch" --build "$build" > /dev/null \
            && yontrack --config "$config" graphql --fail-on-user-errors --query "$SFM_SETUP_STAMP" \
                --vars-json "$(jq -n -c \
                    --arg project "$SFM_PROJECT" --arg branch "$branch" --arg validation "$stamp" \
                    --arg dataType "$SFM_DATA_TYPE" --argjson dataTypeConfig "$thresholds" \
                    '{$project, $branch, $validation, $dataType, $dataTypeConfig}')" > /dev/null \
            && yontrack --config "$config" validate --project "$SFM_PROJECT" --branch "$branch" --build "$build" \
                --validation "$stamp" findings --format "$format" --kind "$kind" --report "$report" "$@" > /dev/null
    }; then
        sfm_warn "$stamp could not be mirrored onto $SFM_PROJECT/$branch/$build."
        return 0
    fi
    echo "$stamp mirrored onto $SFM_PROJECT/$branch/$build."
}

# The neutral `findings` report of a code-scanning alerts answer.
#
#   * Security alerts only, by `rule.security_severity_level`, as SECURITY.CODE counts them.
#   * The rule id and the path without its line: a finding must survive the code moving.
#   * A dismissed alert is an accepted finding, its reason and comment as the statement. That is
#     why the alerts API is read rather than CodeQL's SARIF, which knows nothing of dismissals.
#
# An answer that cannot be read is an error, never an empty report.
#
# The fetching and the checking of the answer repeat scs_fetch and scs_count of
# security-code-scan.sh on purpose: the mirror goes as a whole at #1875, without touching what
# SECURITY.CODE counts on self.dev.
sfm_code_findings() {
    local input="${1:--}"
    if [ "$input" != "-" ] && [ ! -f "$input" ]; then
        sfm_fail "No alerts file at $input"
        return 1
    fi
    jq -e -s -c '
        if length == 0 or any(.[]; type != "array") then
            error("not a list of code-scanning alerts")
        else
            {
                scanner: "codeql",
                kind: "CODE",
                findings: [
                    .[][]
                    | select(.rule.security_severity_level != null)
                    | {
                        externalId: .rule.id,
                        location: (.most_recent_instance.location.path // ""),
                        severity: (.rule.security_severity_level | ascii_upcase),
                        rawSeverity: .rule.security_severity_level,
                        title: (.rule.description // .rule.id),
                        url: .html_url
                    } + (
                        if .state == "dismissed" then
                            {acceptance: {
                                statement: ([.dismissed_reason, .dismissed_comment]
                                    | map(select(. != null and . != "")) | join(": ")),
                                source: "GitHub code scanning alert #\(.number)"
                            }}
                        else {} end
                    )
                ]
            }
        end
    ' "$input" || { sfm_fail "Could not read the code-scanning alerts"; return 1; }
}

sfm_fetch_code() {
    local repository="${1:-}" ref="${2:-}" output="${3:-}" alerts state page report
    [ -n "$repository" ] || { sfm_fail "No repository"; return 1; }
    [ -n "$ref" ] || { sfm_fail "No ref"; return 1; }
    [ -n "$output" ] || { sfm_fail "No output file"; return 1; }

    alerts=""
    for state in open dismissed; do
        echo "Fetching the $state CodeQL alerts of $repository at $ref" >&2
        page="$(gh api --paginate \
            "repos/$repository/code-scanning/alerts?ref=$ref&state=$state&tool_name=CodeQL&per_page=100")" \
            || { sfm_fail "Could not fetch the $state code-scanning alerts of $ref"; return 1; }
        alerts+="$page"$'\n'
    done
    report="$(printf '%s' "$alerts" | sfm_code_findings)" || return 1
    printf '%s\n' "$report" > "$output"
}

sfm_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        enabled) sfm_enabled "$@" ;;
        guard) sfm_guard "$@" ;;
        configure) sfm_configure "$@" ;;
        publish) sfm_publish "$@" ;;
        fetch-code) sfm_fetch_code "$@" ;;
        code-findings) sfm_code_findings "$@" ;;
        thresholds) sfm_thresholds "$@" ;;
        *)
            echo "Usage: $0 enabled|guard|configure|publish|fetch-code|code-findings|thresholds ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${SECURITY_FINDINGS_MIRROR_LIB_ONLY:-}" ]; then
    sfm_main "$@"
    exit $?
fi
