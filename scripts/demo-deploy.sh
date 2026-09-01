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
# The Yontrack CLI must already be installed and configured. Needs 5.2.0 or
# later for the `slot` commands and for `build search --with-display-name`.

set -uo pipefail

DD_PROJECT="${DEMO_PROJECT:-yontrack}"
DD_BRANCH="${DEMO_BRANCH:-main}"
DD_PROMOTION="${DEMO_PROMOTION:-BRONZE}"
DD_ENVIRONMENT="${DEMO_ENVIRONMENT:-demo.dev.yontrack.com}"

dd_log() { echo "$*"; }
dd_fail() { echo "ERROR: $*" >&2; return 1; }

# Deploy unless the demo already runs this exact build. A manual dispatch always
# deploys: "reset my demo" is a legitimate thing to ask for.
dd_should_deploy() {
    local resolved="$1" deployed="$2" manual="$3"
    [ "$manual" = "true" ] && return 0
    [ -z "$deployed" ] && return 0
    [ "$resolved" != "$deployed" ]
}

# Turns a literal into a regex matching exactly it, and nothing else.
#
# `--with-display-name` is matched case-insensitively and *partially* by the
# server, so an unanchored "5.3.0-rc-4" would also match 5.3.0-rc-45 and deploy
# the wrong build. Anchoring alone is not enough either: the dots in a version
# are regex wildcards.
dd_exact_pattern() {
    printf '^%s$' "$(printf '%s' "$1" | sed 's/[][^$.*+?(){}|\\]/\\&/g')"
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

# A build named explicitly, as {Id,Name,DisplayName}.
#
# A build's display name is its release property when it has one and its own
# name otherwise, so this matches whichever of the two a human would quote.
dd_resolve_named() {
    yontrack build search \
        --project "$DD_PROJECT" \
        --branch "$DD_BRANCH" \
        --with-display-name "$(dd_exact_pattern "$1")" \
        --count 1 \
        --output json
}

# The demo slot and whatever it last deployed.
dd_slot() {
    yontrack slot get \
        --project "$DD_PROJECT" \
        --environment "$DD_ENVIRONMENT" \
        --output json
}

# Starts a deployment of the build on the slot, as {id,number}.
dd_start_pipeline() {
    yontrack slot pipeline start \
        --project "$DD_PROJECT" \
        --environment "$DD_ENVIRONMENT" \
        --build "$1" \
        --output json
}

# GitHub sinks, no-ops when running outside Actions.
dd_output() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"; return 0; }
dd_summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$1" >> "$GITHUB_STEP_SUMMARY"; return 0; }

dd_main() {
    local wanted="${DEMO_BUILD:-}" manual="${DEMO_MANUAL:-false}"
    local build_json build_id build_name build_display

    if [ -n "$wanted" ]; then
        dd_log "Resolving build '$wanted' in $DD_PROJECT/$DD_BRANCH"
        build_json="$(dd_resolve_named "$wanted")" || return 1
        [ -z "$build_json" ] && { dd_fail "No build '$wanted' in $DD_PROJECT/$DD_BRANCH"; return 1; }
    else
        dd_log "Resolving the latest $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"
        build_json="$(dd_latest_promoted)" || {
            dd_fail "No $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"
            return 1
        }
        [ -z "$build_json" ] && { dd_fail "No $DD_PROMOTION build in $DD_PROJECT/$DD_BRANCH"; return 1; }
    fi

    build_id="$(echo "$build_json" | jq -r '.Id')"
    build_name="$(echo "$build_json" | jq -r '.Name')"
    build_display="$(echo "$build_json" | jq -r '.DisplayName')"
    [ -z "$build_name" ] || [ "$build_name" = "null" ] && {
        dd_fail "Could not read a build out of: $build_json"
        return 1
    }
    dd_log "Build to deploy: $build_display (id $build_id)"

    local slot_json slot_id deployed deployed_name
    slot_json="$(dd_slot)" || return 1
    slot_id="$(echo "$slot_json" | jq -r '.id // empty')"
    [ -z "$slot_id" ] && {
        dd_fail "No slot for project $DD_PROJECT in environment $DD_ENVIRONMENT"
        return 1
    }
    deployed="$(echo "$slot_json" | jq -r '.lastDeployedPipeline.build.id // empty')"
    deployed_name="$(echo "$slot_json" | jq -r '.lastDeployedPipeline.build.displayName // empty')"
    dd_log "Demo slot $slot_id currently runs ${deployed_name:-nothing}"

    if ! dd_should_deploy "$build_id" "$deployed" "$manual"; then
        dd_log "The demo already runs $build_display - nothing to do."
        dd_output deployed false
        dd_summary "Demo already runs \`$build_display\` - skipped."
        return 0
    fi

    dd_log "Starting a deployment of $build_display on $slot_id"
    local start_json pipeline_id pipeline_number
    # The CLI fails on its own when the payload carries errors, or when no
    # pipeline came back without one either.
    start_json="$(dd_start_pipeline "$build_name")" || return 1

    # Starting the pipeline is only the beginning: the slot's own workflow then
    # has to auto-version the gitops repository and mark the deployment done.
    # Naming the pipeline here is the only handle this run leaves on that, so a
    # deployment that stalls later can still be traced back.
    pipeline_id="$(echo "$start_json" | jq -r '.id // empty')"
    pipeline_number="$(echo "$start_json" | jq -r '.number // empty')"

    dd_log "Deployment of $build_display started: pipeline #$pipeline_number ($pipeline_id)"
    dd_output deployed true
    dd_output build "$build_display"
    dd_output pipeline "$pipeline_id"
    dd_summary "Deploying \`$build_display\` to the demo - pipeline #$pipeline_number."
    return 0
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${DEMO_DEPLOY_LIB_ONLY:-}" ]; then
    return 0
fi

dd_main
