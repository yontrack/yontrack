#!/usr/bin/env bash
#
# Smoke-tests the demo deployment.
#
# Called by .github/workflows/demo-smoke.yml, which the demo slot's RUNNING workflow
# dispatches once it has marked the deployment done. The logic lives here rather than
# inline in the workflow so that scripts/demo-smoke-test.sh can exercise it against a
# stubbed HTTP client.
#
# Usage: scripts/demo-smoke.sh resolve|poll|assert
#
#   resolve  Finds the Yontrack build the deployed version names, and writes its name to
#            $GITHUB_OUTPUT as `build`. DEMO.SMOKE is reported against that name.
#   poll     Waits until the demo answers with the version that was deployed. The slot
#            marks itself deployed when the gitops PR merges, which is well before ArgoCD
#            has synced and the pods are serving, so this is the first and most valuable
#            check of the lot: "the version I asked for is the version answering".
#   assert   Checks that the seeded demo dataset is there. Deliberately thin - the heavy
#            suites already ran for BRONZE, and a fat smoke suite becomes the flaky thing
#            that blocks releases.
#
# Environment:
#   DEMO_URL             demo instance (default: https://demo.dev.yontrack.com)
#   DEMO_TOKEN           API token on that instance
#   DEMO_VERSION         version that was deployed (resolve and poll)
#   DEMO_PROJECT         Yontrack project holding the build (default: yontrack)
#   DEMO_BRANCH          Yontrack branch holding the build (default: main)
#   DEMO_SEEDED_PROJECT  seeded project to assert (default: petclinic)
#   DEMO_POLL_TIMEOUT    how long to wait for the version, in seconds (default: 600).
#                        0 makes `poll` a single check rather than a wait - the loop
#                        always attempts once before testing the deadline, which is
#                        what .github/workflows/demo-screenshots.yml relies on.
#   DEMO_POLL_INTERVAL   seconds between two attempts (default: 15)
#
# Everything goes through /graphql. The ingress only routes /graphql and /hook to the
# backend - /rest/* lands on the Next UI and answers 404 - so the REST info endpoint is
# not reachable from outside the cluster. `query { info { version { full } } }` carries
# the same value, needs the same token, and exercises the ingress, the backend and
# authentication in one call.

set -uo pipefail

# Build lookup, shared with scripts/demo-deploy.sh so that the workflow that deploys a version
# and this one, which validates it, resolve the same build.
# shellcheck source=yontrack-build.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/yontrack-build.sh"

# `dsm_`/`DSM_` throughout, not `ds_`/`DS_`: scripts/dev-stack.sh already owns that prefix in
# this directory, down to a `ds_log` that does something else.
DSM_URL="${DEMO_URL:-https://demo.dev.yontrack.com}"
DSM_URL="${DSM_URL%/}"
DSM_SEEDED_PROJECT="${DEMO_SEEDED_PROJECT:-petclinic}"
DSM_YONTRACK_PROJECT="${DEMO_PROJECT:-yontrack}"
DSM_YONTRACK_BRANCH="${DEMO_BRANCH:-main}"
DSM_TIMEOUT="${DEMO_POLL_TIMEOUT:-600}"
DSM_INTERVAL="${DEMO_POLL_INTERVAL:-15}"

dsm_log() { echo "$*"; }
dsm_fail() { echo "ERROR: $*" >&2; return 1; }

# Indirections so the tests can drive the poll loop without waiting for it.
dsm_now() { date +%s; }
dsm_sleep() { sleep "$1"; }

# Runs a GraphQL query and echoes the response body.
#
# Fails when the transport fails, when the status is not 2xx, or when the payload
# carries GraphQL errors - a 200 with an `errors` array is how an expired token and a
# query against a half-started backend both look.
dsm_graphql() {
    local query="$1" payload response body status errors
    payload="$(jq -nc --arg q "$query" '{query: $q}')" || return 1

    response="$(curl -sS --max-time 30 -w '\n%{http_code}' \
        -X POST "$DSM_URL/graphql" \
        -H 'Content-Type: application/json' \
        -H "X-Ontrack-Token: ${DEMO_TOKEN:-}" \
        -d "$payload" 2>&1)" || {
        dsm_fail "Could not reach $DSM_URL/graphql: $response"
        return 1
    }

    status="${response##*$'\n'}"
    body="${response%$'\n'*}"

    case "$status" in
        2??) ;;
        *)
            dsm_fail "$DSM_URL/graphql answered $status: $(echo "$body" | head -c 200)"
            return 1
            ;;
    esac

    errors="$(echo "$body" | jq -r '.errors // empty | tostring' 2>/dev/null)"
    if [ -n "$errors" ]; then
        dsm_fail "GraphQL errors from $DSM_URL: $errors"
        return 1
    fi

    echo "$body"
}

# The full version the demo reports, or nothing when it cannot be asked.
dsm_version() {
    local body
    body="$(dsm_graphql 'query { info { version { full } } }')" || return 1
    echo "$body" | jq -r '.data.info.version.full // empty'
}

# Waits until the demo reports the expected version.
#
# Every failure mode in between - ingress up but backend down, old pod still serving,
# rollout half-done - looks like "not that version yet", so they all share one loop and
# one deadline.
dsm_wait_for_version() {
    local expected="$1"
    local deadline attempt=0 actual last=""
    deadline=$(( $(dsm_now) + DSM_TIMEOUT ))

    while true; do
        attempt=$(( attempt + 1 ))
        actual="$(dsm_version)" || actual=""
        if [ -n "$actual" ] && [ "$actual" = "$expected" ]; then
            dsm_log "The demo reports $actual after $attempt attempt(s)."
            return 0
        fi
        last="${actual:-unreachable}"
        dsm_log "Attempt $attempt: the demo reports $last, waiting for $expected."
        if [ "$(dsm_now)" -ge "$deadline" ]; then
            dsm_fail "The demo still reports $last after ${DSM_TIMEOUT}s, expected $expected."
            return 1
        fi
        dsm_sleep "$DSM_INTERVAL"
    done
}

# Checks that the seed left a usable dataset behind.
#
# One project with one branch and one build is enough: the seed either ran to completion
# or it failed loudly, so this is a check that it ran at all, not an inventory of it.
dsm_assert_seeded_project() {
    local body name branch build
    body="$(dsm_graphql "query {
        projects(name: \"$DSM_SEEDED_PROJECT\") {
            name
            branches(count: 1) {
                name
                builds(count: 1) { name }
            }
        }
    }")" || return 1

    name="$(echo "$body" | jq -r '.data.projects[0].name // empty')"
    [ -z "$name" ] && { dsm_fail "The seeded project $DSM_SEEDED_PROJECT is not on the demo."; return 1; }

    branch="$(echo "$body" | jq -r '.data.projects[0].branches[0].name // empty')"
    [ -z "$branch" ] && { dsm_fail "The seeded project $DSM_SEEDED_PROJECT has no branch."; return 1; }

    build="$(echo "$body" | jq -r '.data.projects[0].branches[0].builds[0].name // empty')"
    [ -z "$build" ] && { dsm_fail "Branch $DSM_SEEDED_PROJECT/$branch has no build."; return 1; }

    dsm_log "The seeded project $DSM_SEEDED_PROJECT answers: $branch/$build."
    return 0
}

# The Yontrack build name the deployed version stands for.
#
# DEMO.SMOKE is reported with `yontrack validate --build`, which takes the build *name* - the
# opaque timestamp-run pair. The slot workflow cannot pass it: `${build}` renders the build's
# display name, which is the release property, and no templating source exposes the raw name.
# So the version is resolved back to a build here, the same way demo-deploy.sh resolves the
# one it deploys - and by the same code, so the two cannot drift onto different builds.
dsm_resolve_build() {
    local version="$1" json name
    json="$(yontrack_build_by_display_name "$DSM_YONTRACK_PROJECT" "$DSM_YONTRACK_BRANCH" "$version")" || return 1
    [ -z "$json" ] && {
        dsm_fail "No build named $version in $DSM_YONTRACK_PROJECT/$DSM_YONTRACK_BRANCH."
        return 1
    }
    name="$(echo "$json" | jq -r '.Name // empty')"
    [ -z "$name" ] && { dsm_fail "Could not read a build out of: $json"; return 1; }
    echo "$name"
}

# GitHub sink, a no-op when running outside Actions.
dsm_output() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"; return 0; }

dsm_require_token() {
    [ -n "${DEMO_TOKEN:-}" ] || { dsm_fail "DEMO_TOKEN is not set: the demo cannot be queried."; return 1; }
}

dsm_resolve() {
    local version="${DEMO_VERSION:-}" name
    [ -n "$version" ] || { dsm_fail "DEMO_VERSION is not set: nothing to resolve."; return 1; }
    name="$(dsm_resolve_build "$version")" || return 1
    dsm_log "Version $version is build $name in $DSM_YONTRACK_PROJECT/$DSM_YONTRACK_BRANCH."
    dsm_output build "$name"
}

dsm_poll() {
    local expected="${DEMO_VERSION:-}"
    [ -n "$expected" ] || { dsm_fail "DEMO_VERSION is not set: nothing to wait for."; return 1; }
    dsm_require_token || return 1
    dsm_log "Waiting for $DSM_URL to report version $expected (timeout ${DSM_TIMEOUT}s)"
    dsm_wait_for_version "$expected"
}

dsm_assert() {
    dsm_require_token || return 1
    dsm_log "Asserting the seeded demo at $DSM_URL"
    dsm_assert_seeded_project
}

dsm_main() {
    case "${1:-}" in
        resolve) dsm_resolve ;;
        poll) dsm_poll ;;
        assert) dsm_assert ;;
        *)
            dsm_fail "Usage: $0 resolve|poll|assert"
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${DEMO_SMOKE_LIB_ONLY:-}" ]; then
    return 0
fi

dsm_main "$@"
