#!/usr/bin/env bash
#
# Deploys a build to the demo environment by starting a pipeline on its slot.
#
# Called by .github/workflows/demo-deploy.yml, nightly and on demand. The logic
# lives here rather than inline in the workflow so that scripts/demo-deploy-test.sh
# can exercise it against a stubbed CLI.
#
# Usage: scripts/demo-deploy.sh
#
# Environment:
#   DEMO_BUILD        display name of the build to deploy; defaults to the
#                     latest build carrying the promotion
#   DEMO_MANUAL       "true" for a manual dispatch, which always deploys
#   DEMO_PROJECT      Yontrack project (default: yontrack)
#   DEMO_BRANCH       branch to resolve builds from (default: main)
#   DEMO_PROMOTION    promotion the build must carry (default: BRONZE)
#   DEMO_ENVIRONMENT  environment holding the slot (default: demo.dev.yontrack.com)
#
# The Yontrack CLI must already be installed and configured.

set -uo pipefail

DD_PROJECT="${DEMO_PROJECT:-yontrack}"
DD_BRANCH="${DEMO_BRANCH:-main}"
DD_PROMOTION="${DEMO_PROMOTION:-BRONZE}"
DD_ENVIRONMENT="${DEMO_ENVIRONMENT:-demo.dev.yontrack.com}"

# The `release` property holds the version, which is what a human types when
# they name a build. The build's own name is a timestamp-run pair.
DD_RELEASE_PROPERTY="net.nemerosa.ontrack.extension.general.ReleasePropertyType"

dd_log() { echo "$*"; }
dd_fail() { echo "ERROR: $*" >&2; return 1; }

# A build id goes into the mutation as a literal, because `yontrack graphql`
# passes every --var as a string and the mutation wants an Int. Checking it is
# a plain integer is what keeps that interpolation safe.
dd_numeric() {
    case "${1:-}" in
        '' | *[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

# Deploy unless the demo already runs this exact build. A manual dispatch always
# deploys: "reset my demo" is a legitimate thing to ask for.
dd_should_deploy() {
    local resolved="$1" deployed="$2" manual="$3"
    [ "$manual" = "true" ] && return 0
    [ -z "$deployed" ] && return 0
    [ "$resolved" != "$deployed" ]
}

# Latest build on the branch carrying the promotion, as {Id,Name,DisplayName}.
dd_latest_promoted() {
    yontrack build search \
        --project "$DD_PROJECT" \
        --branch "$DD_BRANCH" \
        --with-promotion "$DD_PROMOTION" \
        --count 1 \
        --output json
}

# Resolves an explicit build, by display name first and by build name second —
# the same order Yontrack's own display-name lookup uses.
# The `$...` tokens inside the query are GraphQL variables, passed by --var, and
# must reach the server unexpanded - hence the single quotes.
# shellcheck disable=SC2016
dd_resolve_named() {
    local name="$1"
    yontrack graphql -q '
        query ResolveBuild($project: String!, $branch: String!, $name: String!) {
            byDisplayName: builds(project: $project, buildProjectFilter: {
                branchName: $branch,
                property: "'"$DD_RELEASE_PROPERTY"'",
                propertyValue: $name,
                maximumCount: 1
            }) { id name displayName }
            byName: builds(project: $project, buildProjectFilter: {
                branchName: $branch,
                buildName: $name,
                buildExactMatch: true,
                maximumCount: 1
            }) { id name displayName }
        }' \
        -v project="$DD_PROJECT" \
        -v branch="^${DD_BRANCH}\$" \
        -v name="$name"
}

# The demo slot for the project, with whatever it last deployed.
# The `$...` tokens inside the query are GraphQL variables, passed by --var, and
# must reach the server unexpanded - hence the single quotes.
# shellcheck disable=SC2016
dd_slot() {
    yontrack graphql -q '
        query DemoSlot($environment: String!, $project: String!) {
            environmentByName(name: $environment) {
                slots(projects: [$project]) {
                    id
                    lastDeployedPipeline { build { id displayName } }
                }
            }
        }' \
        -v environment="$DD_ENVIRONMENT" \
        -v project="$DD_PROJECT"
}

# Starts a deployment of the build on the slot.
# The `$...` tokens inside the query are GraphQL variables, passed by --var, and
# must reach the server unexpanded - hence the single quotes.
# shellcheck disable=SC2016
dd_start_pipeline() {
    local slot_id="$1" build_id="$2"
    yontrack graphql -q '
        mutation StartDemoPipeline($slotId: String!) {
            startSlotPipeline(input: {slotId: $slotId, buildId: '"$build_id"'}) {
                pipeline { id number }
                errors { message }
            }
        }' \
        -v slotId="$slot_id"
}

# GitHub sinks, no-ops when running outside Actions.
dd_output() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"; return 0; }
dd_summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$1" >> "$GITHUB_STEP_SUMMARY"; return 0; }

dd_main() {
    local wanted="${DEMO_BUILD:-}" manual="${DEMO_MANUAL:-false}"
    local build_json build_id build_name

    if [ -n "$wanted" ]; then
        dd_log "Resolving build '$wanted' in $DD_PROJECT/$DD_BRANCH"
        local resolved
        resolved="$(dd_resolve_named "$wanted")" || return 1
        build_json="$(echo "$resolved" | jq -c '(.byDisplayName[0] // .byName[0]) // empty')"
        [ -z "$build_json" ] && { dd_fail "No build '$wanted' in $DD_PROJECT/$DD_BRANCH"; return 1; }
        build_id="$(echo "$build_json" | jq -r '.id')"
        build_name="$(echo "$build_json" | jq -r '.displayName')"
    else
        dd_log "Resolving the latest $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"
        build_json="$(dd_latest_promoted)" || {
            dd_fail "No $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"
            return 1
        }
        [ -z "$build_json" ] && { dd_fail "No $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"; return 1; }
        build_id="$(echo "$build_json" | jq -r '.Id')"
        build_name="$(echo "$build_json" | jq -r '.DisplayName')"
    fi

    dd_numeric "$build_id" || { dd_fail "Not a build id: '$build_id'"; return 1; }
    dd_log "Build to deploy: $build_name (id $build_id)"

    local slot_json slot_id deployed deployed_name
    slot_json="$(dd_slot)" || return 1
    slot_id="$(echo "$slot_json" | jq -r '.environmentByName.slots[0].id // empty')"
    [ -z "$slot_id" ] && {
        dd_fail "No slot for project $DD_PROJECT in environment $DD_ENVIRONMENT"
        return 1
    }
    deployed="$(echo "$slot_json" | jq -r '.environmentByName.slots[0].lastDeployedPipeline.build.id // empty')"
    deployed_name="$(echo "$slot_json" | jq -r '.environmentByName.slots[0].lastDeployedPipeline.build.displayName // empty')"
    dd_log "Demo slot $slot_id currently runs ${deployed_name:-nothing}"

    if ! dd_should_deploy "$build_id" "$deployed" "$manual"; then
        dd_log "The demo already runs $build_name - nothing to do."
        dd_output deployed false
        dd_summary "Demo already runs \`$build_name\` - skipped."
        return 0
    fi

    dd_log "Starting a deployment of $build_name on $slot_id"
    local start_json errors
    start_json="$(dd_start_pipeline "$slot_id" "$build_id")" || return 1
    errors="$(echo "$start_json" | jq -r '(.startSlotPipeline.errors // []) | map(.message) | join("; ")')"
    [ -n "$errors" ] && { dd_fail "Could not start the deployment: $errors"; return 1; }

    dd_log "Deployment of $build_name started."
    dd_output deployed true
    dd_output build "$build_name"
    dd_summary "Deploying \`$build_name\` to the demo."
    return 0
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${DEMO_DEPLOY_LIB_ONLY:-}" ]; then
    return 0
fi

dd_main
