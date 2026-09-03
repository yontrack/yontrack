#!/usr/bin/env bash
#
# Publishes a Yontrack release.
#
# Called by .github/workflows/release.yml, which the GOLD promotion dispatches through the
# `github-workflow` notification channel. The logic lives here rather than inline in the
# workflow so that scripts/release-test.sh can exercise it against stubbed clients - and
# because most of what this file does is refuse to run, which is exactly the part worth
# testing.
#
# Usage: scripts/release.sh resolve|docs|wiki|body
#
#   resolve  Finds the build the version names, works out the version to publish, and refuses
#            to publish over anything that already exists. Outputs `version`, `rc_version`,
#            `build`, `build_id`, `run_id` and `sha`; every later step is driven by those.
#   docs     Finds the docs artefact on the CI run recorded on the build, before anything is
#            published. Outputs `artifact_id`.
#   wiki     Checks the release page exists in the wiki and is reachable from the index. A
#            check, not a publication step - the page is written by a human before GOLD.
#   body     Composes the GitHub release body: the wiki link first, the changelog second.
#
# Environment:
#   YONTRACK_URL           Yontrack instance
#   YONTRACK_TOKEN         API token on it
#   RELEASE_BUILD_VERSION  version the GOLD promotion named, i.e. the build's display name
#                          at that point (`5.3.0-rc-100`)
#   RELEASE_VERSION        version to publish under; defaults to the base version
#   RELEASE_PROJECT        Yontrack project (default: yontrack)
#   RELEASE_BRANCH         Yontrack branch (default: main)
#   RELEASE_REPOSITORY     GitHub repository (default: yontrack/yontrack)
#   REL_WIKI_DIR           checkout of the wiki (default: wiki)
#   REL_VERSION            version being published, for the steps after `resolve`
#   REL_RC_VERSION         the rc version, i.e. the GHCR tag to re-tag from
#   REL_BUILD_ID           Yontrack build id, for the changelog boundary
#   REL_RUN_ID             CI run holding the docs artefact
#   REL_SHA                commit the release targets
#
# The Yontrack CLI must already be installed and configured; `gh` must be authenticated.

set -uo pipefail

# Build lookup, shared with scripts/demo-deploy.sh and scripts/demo-smoke.sh so that the
# workflows that deploy, verify and release a version all resolve the same build.
# shellcheck source=yontrack-build.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/yontrack-build.sh"

REL_PROJECT="${RELEASE_PROJECT:-yontrack}"
REL_BRANCH="${RELEASE_BRANCH:-main}"
REL_REPOSITORY="${RELEASE_REPOSITORY:-yontrack/yontrack}"

# Both organisations, both images. They are pushed together, so any one of them already
# carrying the tag means this release ran before.
REL_DOCKER_REPOSITORIES="nemerosa/ontrack nemerosa/ontrack-ui yontrack/yontrack yontrack/yontrack-ui"

rel_log() { echo "$*"; }
rel_fail() { echo "ERROR: $*" >&2; return 1; }

# GitHub sinks, no-ops when running outside Actions.
rel_output() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"; return 0; }
rel_summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$1" >> "$GITHUB_STEP_SUMMARY"; return 0; }

# ---------------------------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------------------------

# The version an rc version is a candidate for: `5.3.0-rc-100` -> `5.3.0`.
#
# This one line is the build-identity decision made concrete (see docs/adr/0006). Nothing is
# rebuilt for a release, so what ships is the rc build, published under the version its
# artefacts are named for.
#
# Only a trailing `-rc-<digits>` is stripped. A feature-branch version carries the branch name
# and a short commit, and turning that into something that merely looks releasable is the
# accident rel_check_unpublished exists to catch - so it is left alone here and rejected by
# rel_valid_version below.
rel_base_version() {
    printf '%s' "$1" | sed -E 's/-rc-[0-9]+$//'
}

# A version is three dot-separated numbers and nothing else. A build with no `release` property
# has its own name - a timestamp-run pair - as its display name, and publishing under that
# would tag Docker Hub with a timestamp.
rel_valid_version() {
    printf '%s' "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'
}

# ---------------------------------------------------------------------------------------------
# Yontrack
# ---------------------------------------------------------------------------------------------

# Runs a GraphQL query against Yontrack and echoes the response body.
#
# Fails when the transport fails, when the status is not 2xx, or when the payload carries
# GraphQL errors - a 200 with an `errors` array is how an expired token looks.
rel_graphql() {
    local query="$1" payload response body status errors
    payload="$(jq -nc --arg q "$query" '{query: $q}')" || return 1

    response="$(curl -sS --max-time 30 -w '\n%{http_code}' \
        -X POST "${YONTRACK_URL%/}/graphql" \
        -H 'Content-Type: application/json' \
        -H "X-Ontrack-Token: ${YONTRACK_TOKEN:-}" \
        -d "$payload" 2>&1)" || {
        rel_fail "Could not reach ${YONTRACK_URL%/}/graphql: $response"
        return 1
    }

    status="${response##*$'\n'}"
    body="${response%$'\n'*}"

    case "$status" in
        2*) ;;
        *) rel_fail "Yontrack answered HTTP $status: $body"; return 1 ;;
    esac

    errors="$(echo "$body" | jq -r '(.errors // []) | map(.message) | join("; ")')"
    [ -n "$errors" ] && { rel_fail "Yontrack answered with errors: $errors"; return 1; }

    echo "$body"
}

# Everything the release needs off the build, in one call: the commit it targets and the CI run
# that holds its docs artefact.
#
# The run is read from the GitHub workflow run property, not from run info: `yontrack ci config`
# sets it on every build and records the run id as a *number*, ready for the `gh` API, where a
# URL would have to be parsed. See doc/dev-guide/docs-artifact.md.
rel_build_details() {
    rel_graphql "query {
        build(id: $1) {
            name
            displayName
            gitCommitProperty { value }
            buildGitHubWorkflowRunProperty { value }
        }
    }"
}

# ---------------------------------------------------------------------------------------------
# Guards
# ---------------------------------------------------------------------------------------------

# Indirections, so the tests can drive the guards without a registry or a tag database.

# Non-empty when the tag exists locally. The workflow checks out with `fetch-depth: 0`, so
# every tag the repository has is here.
rel_git_tags() { git tag -l "$1"; }

# The HTTP status of a Docker Hub tag: 200 when published, 404 when not.
rel_docker_status() {
    curl -sS --max-time 30 -o /dev/null -w '%{http_code}' \
        "https://hub.docker.com/v2/repositories/$1/tags/$2"
}

# Refuses a version that has already been published.
#
# Silently overwriting a published tag is the worst failure mode in this design, and it costs
# ten lines to make impossible. In practice it also self-corrects - `writeVersion` derives the
# base version from `git tag -l`, so publishing 5.3.0 makes the next main build compute 5.3.1 -
# but nothing *enforces* that, which is the whole point of checking.
rel_check_unpublished() {
    local version="$1" tag repo status

    tag="$(rel_git_tags "$version")" || return 1
    [ -n "$tag" ] && {
        rel_fail "Git tag $version already exists: this version has already been released."
        return 1
    }

    for repo in $REL_DOCKER_REPOSITORIES; do
        status="$(rel_docker_status "$repo" "$version")" || {
            rel_fail "Could not ask Docker Hub about $repo:$version."
            return 1
        }
        case "$status" in
            404) ;;
            200)
                rel_fail "$repo:$version is already on Docker Hub: this version has already been released."
                return 1
                ;;
            *)
                rel_fail "Docker Hub answered HTTP $status for $repo:$version."
                return 1
                ;;
        esac
    done

    return 0
}

# ---------------------------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------------------------

rel_resolve() {
    local rc_version="${RELEASE_BUILD_VERSION:-}"
    [ -n "$rc_version" ] || { rel_fail "RELEASE_BUILD_VERSION is not set: nothing to release."; return 1; }

    # The GOLD workflow can only pass the version: `${build}` renders the build's display name,
    # which is the release property, and no templating source exposes the raw name. So the
    # version is resolved back to a build here, by the same lookup the demo workflows use.
    local json build_id build_name
    json="$(yontrack_build_by_display_name "$REL_PROJECT" "$REL_BRANCH" "$rc_version")" || return 1
    [ -z "$json" ] && {
        rel_fail "No build named $rc_version in $REL_PROJECT/$REL_BRANCH."
        return 1
    }
    build_id="$(echo "$json" | jq -r '.Id // empty')"
    build_name="$(echo "$json" | jq -r '.Name // empty')"
    [ -z "$build_name" ] && { rel_fail "Could not read a build out of: $json"; return 1; }

    local version="${RELEASE_VERSION:-}"
    [ -n "$version" ] || version="$(rel_base_version "$rc_version")"
    rel_valid_version "$version" || {
        rel_fail "'$version' is not a version. A build with no release property has its own name as its display name, which is not something to publish under."
        return 1
    }

    local details sha run_id
    details="$(rel_build_details "$build_id")" || return 1
    sha="$(echo "$details" | jq -r '.data.build.gitCommitProperty.value.commit // empty')"
    [ -z "$sha" ] && { rel_fail "Build $build_name records no Git commit: there is nothing to tag."; return 1; }

    # Picked by workflow name rather than by position: `configureBuild` writes one entry today,
    # but the property is a list and reading [0] would silently pick the wrong run the day
    # anything else records itself against a build.
    run_id="$(echo "$details" \
        | jq -r '[(.data.build.buildGitHubWorkflowRunProperty.value.workflows // [])[] | select(.name == "CI")][0].runId // empty')"
    [ -z "$run_id" ] && {
        rel_fail "Build $build_name records no CI workflow run: the docs artefact cannot be found without one."
        return 1
    }

    rel_check_unpublished "$version" || return 1

    rel_log "Releasing $rc_version (build $build_name) as $version, from CI run $run_id on $sha."
    rel_output version "$version"
    rel_output rc_version "$rc_version"
    rel_output build "$build_name"
    rel_output build_id "$build_id"
    rel_output run_id "$run_id"
    rel_output sha "$sha"
    rel_summary "Releasing \`$rc_version\` as \`$version\` from [CI run $run_id](${GITHUB_SERVER_URL:-https://github.com}/$REL_REPOSITORY/actions/runs/$run_id)."
    return 0
}

# The docs artefact, checked before anything is published.
#
# 90 days of artefact retention is a real release deadline: a build left waiting for GOLD longer
# than that loses its documentation. Publishing a release with no docs is worse than not
# publishing, and re-running CI is the deliberate, human-triggered exception to "nothing is
# rebuilt at release time" - so the message names the one thing that fixes it.
rel_docs() {
    local run_id="${REL_RUN_ID:-}" version="${REL_VERSION:-}" sha="${REL_SHA:-}"
    [ -n "$run_id" ] || { rel_fail "REL_RUN_ID is not set: no run to look in."; return 1; }

    local artifacts id
    artifacts="$(gh api "repos/$REL_REPOSITORY/actions/runs/$run_id/artifacts?per_page=100")" || {
        rel_fail "Could not list the artefacts of run $run_id."
        return 1
    }

    # An expired artefact and one that was never uploaded are the same problem to whoever has
    # to fix it, and they have the same fix.
    id="$(echo "$artifacts" \
        | jq -r '[.artifacts[]? | select(.name == "docs-site" and .expired == false)][0].id // empty')"
    [ -z "$id" ] && {
        rel_fail "docs artifact expired for $version; re-run CI on $sha to regenerate"
        return 1
    }

    rel_log "Docs artefact $id found on run $run_id."
    rel_output artifact_id "$id"
    return 0
}

# The wiki release page: written by a human before GOLD, checked here.
#
# `WIKI` is a validation on the build like the other three, but it records a check rather than a
# publication - the page is what makes the GitHub release worth opening, and the release links
# to it, so a missing or unreachable page makes the release a dead end.
rel_wiki() {
    local version="${REL_VERSION:-}" dir="${REL_WIKI_DIR:-wiki}"
    [ -n "$version" ] || { rel_fail "REL_VERSION is not set: no page to look for."; return 1; }

    local page="Release-$version.md"
    [ -f "$dir/$page" ] || {
        rel_fail "The wiki has no $page. Write the release notes before granting GOLD."
        return 1
    }

    # The link target, not the version: the archive pages name versions in prose, so matching
    # the version alone would pass on a sentence mentioning it.
    grep -qF "](Release-$version)" "$dir/Home.md" || {
        rel_fail "$page is not linked from Home.md: a page nobody can reach from the index is a page nobody reads."
        return 1
    }

    rel_log "The wiki has $page and links it from the index."
    return 0
}

# The changelog since the last released build.
#
# `--to` is given explicitly because this workflow has no build context of its own: it must not
# register a build, and without `--to` the CLI reads the id out of the environment a build
# registration would have left behind.
rel_changelog() {
    yontrack build changelog export \
        --to "${REL_BUILD_ID:-}" \
        --from-promotion RELEASE \
        --format markdown
}

# The GitHub release body: the wiki link first, the changelog second.
#
# End-user prose lives in the wiki, internals live in the release where developers look for
# them, and neither is duplicated. The order is the convention: whoever opens the release sees
# the readable page first.
rel_body() {
    local version="${REL_VERSION:-}" changelog
    [ -n "$version" ] || { rel_fail "REL_VERSION is not set: nothing to describe."; return 1; }

    # A changelog that cannot be computed must not sink the release. The first release after
    # this pipeline lands has no previous RELEASE build to measure from, and the part a human
    # wrote - the wiki page - is there either way.
    changelog="$(rel_changelog 2>/dev/null)" || changelog=""

    printf '## Release notes\n\n'
    printf '%s/%s/wiki/Release-%s\n\n' "${GITHUB_SERVER_URL:-https://github.com}" "$REL_REPOSITORY" "$version"
    printf '## Changes\n\n'
    if [ -n "$changelog" ]; then
        printf '%s\n' "$changelog"
    else
        printf '_No changelog available._\n'
    fi
    return 0
}

rel_main() {
    case "${1:-}" in
        resolve) rel_resolve ;;
        docs) rel_docs ;;
        wiki) rel_wiki ;;
        body) rel_body ;;
        *)
            rel_fail "Usage: $0 resolve|docs|wiki|body"
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${RELEASE_LIB_ONLY:-}" ]; then
    return 0
fi

rel_main "$@"
