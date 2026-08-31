#!/usr/bin/env bash
#
# Local development stack for Yontrack.
#
# Brings up the three tiers a working Yontrack needs -- the Docker middleware,
# the Spring Boot backend and the Next.js frontend -- with a single command,
# and tears them down again with another.
#
# Several checkouts can run their own stack at the same time. Each checkout is
# an *instance*, identified by a slug derived from its directory and allocated
# a *slot* that offsets every published port by `slot * 100`, so two agents
# working in two worktrees never collide.
#
#   scripts/dev-stack.sh up                  start everything, wait until ready
#   scripts/dev-stack.sh down [--clean]      stop everything (--clean drops data)
#   scripts/dev-stack.sh restart [tier]      restart backend or frontend
#   scripts/dev-stack.sh status              health of this checkout's instance
#   scripts/dev-stack.sh ls                  every instance running on this machine
#   scripts/dev-stack.sh logs [tier] [-f]    read the logs
#
# See DEVELOPMENT.md and docs/adr/0004-parallel-dev-stack-instances.md.

set -uo pipefail

# ===========================================================================
# Pure functions
#
# Everything below this point is free of Docker, Gradle and npm, and is
# covered by scripts/dev-stack-test.sh.
# ===========================================================================

# Slot 0 is reserved for the main checkout, which therefore keeps the ports
# everyone already has in their bookmarks. Linked worktrees hash into 1-9.
DS_SLOT_MIN=1
DS_SLOT_MAX=9

# Turns a checkout path into a short, filesystem- and Docker-safe name.
ds_slug() {
    local name
    name="$(basename "$1")"
    name="$(printf '%s' "$name" \
        | tr '[:upper:]' '[:lower:]' \
        | sed -e 's/[^a-z0-9]\{1,\}/-/g' -e 's/^-*//' -e 's/-*$//')"
    [ -n "$name" ] || name="instance"
    printf '%s' "$name"
}

# Hashes the *absolute* path, so two checkouts that happen to share a basename
# (every `5.0` directory, for instance) still get different slots.
ds_path_hash() {
    printf '%s' "$1" | cksum | cut -d' ' -f1
}

ds_slot_from_path() {
    local range=$((DS_SLOT_MAX - DS_SLOT_MIN + 1))
    printf '%s' $((DS_SLOT_MIN + ($(ds_path_hash "$1") % range)))
}

ds_offset() {
    printf '%s' $(($1 * 100))
}

ds_port() {
    printf '%s' $(($1 + $(ds_offset "$2")))
}

ds_project() {
    printf '%s' "yontrack-dev-$1"
}

# Used when the hashed slot turns out to be taken. Never returns 0: that slot
# belongs to the main checkout and is not up for grabs.
ds_next_slot() {
    local next=$(($1 + 1))
    if [ "$next" -gt "$DS_SLOT_MAX" ] || [ "$next" -lt "$DS_SLOT_MIN" ]; then
        next=$DS_SLOT_MIN
    fi
    printf '%s' "$next"
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${DEV_STACK_LIB_ONLY:-}" ]; then
    return 0
fi

# ===========================================================================
# Instance resolution
# ===========================================================================

set -e

DS_TOPLEVEL="$(git rev-parse --show-toplevel)"
DS_COMPOSE_FILE="$DS_TOPLEVEL/compose/docker-compose-dev.yml"
DS_STATE_DIR="$DS_TOPLEVEL/.yontrack-dev"
DS_INSTANCE_ENV="$DS_STATE_DIR/instance.env"
DS_READY_TIMEOUT="${YONTRACK_DEV_TIMEOUT:-300}"

ds_log()  { printf '  %s\n' "$*"; }
ds_step() { printf '\n==> %s\n' "$*"; }
ds_warn() { printf '  ! %s\n' "$*" >&2; }
ds_fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# True when this is the main working copy rather than a linked worktree.
ds_is_main_checkout() {
    [ "$(git rev-parse --absolute-git-dir)" = \
      "$(git rev-parse --path-format=absolute --git-common-dir)" ]
}

ds_port_free() {
    if command -v lsof >/dev/null 2>&1; then
        ! lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
    else
        ! (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
    fi
}

# Every pid listening on a port, used as the backstop when a pidfile lies.
ds_port_listeners() {
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null || true
    fi
}

# Every host port an instance publishes, for the given slot.
ds_slot_ports() {
    local slot="$1"
    for base in 3000 8080 8800 8008 5432 9200 5672 15672; do
        ds_port "$base" "$slot"
        printf ' '
    done
}

ds_slot_is_free() {
    local port
    for port in $(ds_slot_ports "$1"); do
        ds_port_free "$port" || return 1
    done
    return 0
}

# Picks the slot for this checkout: the one already recorded if there is one,
# otherwise 0 for the main copy or a hash of the path for a worktree, bumped
# along until the ports are actually free.
ds_resolve_slot() {
    if [ -f "$DS_INSTANCE_ENV" ]; then
        # shellcheck disable=SC1090
        local recorded
        recorded="$(grep '^DS_SLOT=' "$DS_INSTANCE_ENV" | cut -d= -f2)"
        if [ -n "$recorded" ]; then
            printf '%s' "$recorded"
            return 0
        fi
    fi

    local slot
    if ds_is_main_checkout; then
        slot=0
    else
        slot="$(ds_slot_from_path "$DS_TOPLEVEL")"
    fi

    local attempts=0
    while ! ds_slot_is_free "$slot"; do
        attempts=$((attempts + 1))
        if [ "$attempts" -gt "$DS_SLOT_MAX" ]; then
            ds_fail "no free slot: every slot 0-$DS_SLOT_MAX has ports in use." \
                    "Run 'scripts/dev-stack.sh ls' to see what is running."
        fi
        slot="$(ds_next_slot "$slot")"
    done
    printf '%s' "$slot"
}

# Fills in every DS_* variable the rest of the script reads.
ds_resolve_instance() {
    DS_SLUG="${YONTRACK_DEV_NAME:-$(ds_slug "$DS_TOPLEVEL")}"
    DS_PROJECT="$(ds_project "$DS_SLUG")"
    DS_SLOT="$(ds_resolve_slot)"

    DS_PORT_UI="$(ds_port 3000 "$DS_SLOT")"
    DS_PORT_APP="$(ds_port 8080 "$DS_SLOT")"
    DS_PORT_MGMT="$(ds_port 8800 "$DS_SLOT")"
    DS_PORT_KEYCLOAK="$(ds_port 8008 "$DS_SLOT")"
    DS_PORT_POSTGRES="$(ds_port 5432 "$DS_SLOT")"
    DS_PORT_ELASTIC="$(ds_port 9200 "$DS_SLOT")"
    DS_PORT_RABBIT="$(ds_port 5672 "$DS_SLOT")"
    DS_PORT_RABBIT_MGMT="$(ds_port 15672 "$DS_SLOT")"
    DS_PORT_KIBANA="$(ds_port 5601 "$DS_SLOT")"
    DS_PORT_INFLUXDB="$(ds_port 8086 "$DS_SLOT")"

    DS_URL_UI="http://localhost:$DS_PORT_UI"
    DS_URL_APP="http://localhost:$DS_PORT_APP"
    DS_URL_MGMT="http://localhost:$DS_PORT_MGMT/manage"
    DS_URL_ISSUER="http://localhost:$DS_PORT_KEYCLOAK/realms/ontrack"

    DS_BACKEND_PID="$DS_STATE_DIR/backend.pid"
    DS_FRONTEND_PID="$DS_STATE_DIR/frontend.pid"
    DS_BACKEND_LOG="$DS_STATE_DIR/backend.log"
    DS_FRONTEND_LOG="$DS_STATE_DIR/frontend.log"
}

# Records the instance so that later commands -- and anything else that wants
# to talk to this stack, such as a test run -- can discover its ports.
ds_write_instance_env() {
    mkdir -p "$DS_STATE_DIR"
    cat > "$DS_INSTANCE_ENV" <<EOF
# Generated by scripts/dev-stack.sh -- do not edit, do not commit.
DS_SLUG=$DS_SLUG
DS_SLOT=$DS_SLOT
DS_PROJECT=$DS_PROJECT
YONTRACK_DEV_UI_PORT=$DS_PORT_UI
YONTRACK_DEV_APP_PORT=$DS_PORT_APP
YONTRACK_DEV_MGMT_PORT=$DS_PORT_MGMT
YONTRACK_DEV_KEYCLOAK_PORT=$DS_PORT_KEYCLOAK
YONTRACK_DEV_POSTGRES_PORT=$DS_PORT_POSTGRES
YONTRACK_DEV_ELASTIC_PORT=$DS_PORT_ELASTIC
YONTRACK_DEV_RABBIT_PORT=$DS_PORT_RABBIT
YONTRACK_DEV_UI_URL=$DS_URL_UI
YONTRACK_DEV_APP_URL=$DS_URL_APP
EOF
}

# ===========================================================================
# Docker middleware
# ===========================================================================

ds_compose() {
    YONTRACK_DEV_POSTGRES_PORT="$DS_PORT_POSTGRES" \
    YONTRACK_DEV_ELASTIC_PORT="$DS_PORT_ELASTIC" \
    YONTRACK_DEV_RABBIT_PORT="$DS_PORT_RABBIT" \
    YONTRACK_DEV_RABBIT_MGMT_PORT="$DS_PORT_RABBIT_MGMT" \
    YONTRACK_DEV_KEYCLOAK_PORT="$DS_PORT_KEYCLOAK" \
    YONTRACK_DEV_KIBANA_PORT="$DS_PORT_KIBANA" \
    YONTRACK_DEV_INFLUXDB_PORT="$DS_PORT_INFLUXDB" \
    YONTRACK_DEV_KEYCLOAK_URL="http://localhost:$DS_PORT_KEYCLOAK" \
        docker compose -p "$DS_PROJECT" -f "$DS_COMPOSE_FILE" "$@"
}

ds_infra_up() {
    ds_step "Middleware (Docker project $DS_PROJECT)"
    ds_compose up -d --wait --wait-timeout "$DS_READY_TIMEOUT" \
        || ds_fail "the middleware did not become healthy. Inspect it with:
  scripts/dev-stack.sh logs infra"
    ds_log "Postgres      localhost:$DS_PORT_POSTGRES"
    ds_log "Elasticsearch localhost:$DS_PORT_ELASTIC"
    ds_log "RabbitMQ      localhost:$DS_PORT_RABBIT"
    ds_log "Keycloak      http://localhost:$DS_PORT_KEYCLOAK"
}

# ===========================================================================
# Process supervision
#
# Each tier runs in its own process group via setsid, so that killing it takes
# the whole tree with it -- Gradle's bootRun forks a child JVM that would
# otherwise be orphaned and keep holding the port.
# ===========================================================================

ds_pid_alive() {
    local pidfile="$1"
    [ -f "$pidfile" ] || return 1
    local pid
    pid="$(cat "$pidfile")"
    [ -n "$pid" ] || return 1
    kill -0 "$pid" 2>/dev/null
}

ds_spawn() {
    local pidfile="$1" logfile="$2"
    shift 2
    mkdir -p "$DS_STATE_DIR"
    : > "$logfile"
    nohup "$@" >> "$logfile" 2>&1 &
    echo $! > "$pidfile"
}

# Depth-first, so children are signalled before the parent that could respawn
# them. Walking the tree rather than killing a process group: macOS has no
# setsid, and a background job here shares this script's process group, so a
# group kill would take the script down with it.
ds_kill_tree() {
    local pid="$1" child
    for child in $(pgrep -P "$pid" 2>/dev/null || true); do
        ds_kill_tree "$child"
    done
    kill -TERM "$pid" 2>/dev/null || true
    local waited=0
    while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 20 ]; do
        sleep 1
        waited=$((waited + 1))
    done
    kill -KILL "$pid" 2>/dev/null || true
}

ds_kill_tier() {
    local pidfile="$1" label="$2"
    shift 2
    if ds_pid_alive "$pidfile"; then
        local pid
        pid="$(cat "$pidfile")"
        ds_log "stopping $label (pid $pid)"
        ds_kill_tree "$pid"
    fi
    rm -f "$pidfile"
    # The pidfile can be stale -- a reboot, a process killed by hand -- and
    # Gradle's forked JVM is not always in our process tree, so the ports are
    # the authority on whether the tier is really down.
    local port stragglers
    for port in "$@"; do
        stragglers="$(ds_port_listeners "$port")"
        if [ -n "$stragglers" ]; then
            ds_warn "$label still holding port $port, killing $(echo "$stragglers" | tr '\n' ' ')"
            # shellcheck disable=SC2086
            kill -KILL $stragglers 2>/dev/null || true
        fi
    done
}

ds_wait_http() {
    local url="$1" label="$2" logfile="${3:-}"
    local waited=0
    while [ "$waited" -lt "$DS_READY_TIMEOUT" ]; do
        if curl -sf -o /dev/null "$url"; then
            ds_log "$label ready"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    local hint=""
    [ -n "$logfile" ] && hint="
  Log: $logfile"
    # Deliberately not tearing anything down: whatever failed has to stay up
    # for whoever is going to read the log.
    ds_fail "$label did not answer on $url within ${DS_READY_TIMEOUT}s.
  Everything is still running so you can diagnose it.$hint"
}

# ===========================================================================
# Backend
# ===========================================================================

ds_backend_up() {
    if ds_pid_alive "$DS_BACKEND_PID" && ! ds_port_free "$DS_PORT_APP"; then
        ds_log "already running on $DS_URL_APP"
        return 0
    fi
    ds_kill_tier "$DS_BACKEND_PID" "backend" "$DS_PORT_APP" "$DS_PORT_MGMT"

    ds_spawn "$DS_BACKEND_PID" "$DS_BACKEND_LOG" \
        env \
            SPRING_PROFILES_ACTIVE=dev \
            SERVER_PORT="$DS_PORT_APP" \
            MANAGEMENT_SERVER_PORT="$DS_PORT_MGMT" \
            SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$DS_PORT_POSTGRES/ontrack" \
            SPRING_ELASTICSEARCH_URIS="http://localhost:$DS_PORT_ELASTIC" \
            SPRING_RABBITMQ_PORT="$DS_PORT_RABBIT" \
            SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="$DS_URL_ISSUER" \
            MANAGEMENT_ENDPOINT_ACCOUNT_ACCESS=unrestricted \
            ONTRACK_CONFIG_URL="$DS_URL_UI" \
            ONTRACK_CONFIG_CONFIGURATION_TEST=false \
            ONTRACK_CONFIG_SEARCH_INDEX_IMMEDIATE=true \
            ONTRACK_CONFIG_TEMPLATING_ERRORS=LOGGING_STACK \
            ONTRACK_EXTENSION_JIRA_CLIENT_TYPE=mock \
        "$DS_TOPLEVEL/gradlew" -p "$DS_TOPLEVEL" --no-daemon :ontrack-ui:bootRun

    ds_log "starting (first run compiles, this can take a few minutes)"
    ds_wait_http "$DS_URL_MGMT/health" "backend" "$DS_BACKEND_LOG"
    ds_log "API  $DS_URL_APP"
    ds_log "Mgmt $DS_URL_MGMT"
}

# ===========================================================================
# Frontend
#
# The full environment is injected here rather than read from .env.local:
# that file is gitignored, so a fresh worktree does not have one. None of
# these values are secret -- the client id and secret are the ones in the
# committed development realm.
# ===========================================================================

ds_frontend_up() {
    if ds_pid_alive "$DS_FRONTEND_PID" && ! ds_port_free "$DS_PORT_UI"; then
        ds_log "already running on $DS_URL_UI"
        return 0
    fi
    ds_kill_tier "$DS_FRONTEND_PID" "frontend" "$DS_PORT_UI"

    local web="$DS_TOPLEVEL/ontrack-web-core"
    if [ ! -d "$web/node_modules" ]; then
        ds_log "installing npm dependencies (first run in this checkout)"
        (cd "$web" && npm ci) || ds_fail "npm ci failed in $web"
    fi

    ds_spawn "$DS_FRONTEND_PID" "$DS_FRONTEND_LOG" \
        env \
            NEXTAUTH_URL="$DS_URL_UI" \
            NEXTAUTH_URL_INTERNAL="$DS_URL_UI" \
            NEXTAUTH_SECRET="yontrack-local-development-secret" \
            NEXTAUTH_ISSUER="$DS_URL_ISSUER" \
            NEXTAUTH_CLIENT_ID="ontrack-client" \
            NEXTAUTH_CLIENT_SECRET="ontrack-client-secret" \
            YONTRACK_UI_MANAGE_ACCOUNT_URL="$DS_URL_ISSUER/account" \
            ONTRACK_URL="$DS_URL_APP" \
        bash -c 'cd "$1" && exec npm run dev -- -p "$2"' _ "$web" "$DS_PORT_UI"

    ds_wait_http "$DS_URL_UI" "frontend" "$DS_FRONTEND_LOG"
}

# ===========================================================================
# Commands
# ===========================================================================

ds_summary() {
    cat <<EOF

Yontrack dev stack '$DS_SLUG' is up (slot $DS_SLOT).

  UI          $DS_URL_UI
  API         $DS_URL_APP
  Management  $DS_URL_MGMT
  Keycloak    http://localhost:$DS_PORT_KEYCLOAK  (admin/admin)

  Backend log   $DS_BACKEND_LOG
  Frontend log  $DS_FRONTEND_LOG
  Ports         $DS_INSTANCE_ENV

EOF
}

ds_cmd_up() {
    ds_resolve_instance
    ds_write_instance_env
    ds_infra_up
    ds_step "Backend"
    ds_backend_up
    ds_step "Frontend"
    ds_frontend_up
    ds_summary
}

ds_cmd_down() {
    local clean=0
    [ "${1:-}" = "--clean" ] && clean=1
    ds_resolve_instance
    ds_step "Stopping '$DS_SLUG' (slot $DS_SLOT)"
    ds_kill_tier "$DS_FRONTEND_PID" "frontend" "$DS_PORT_UI"
    ds_kill_tier "$DS_BACKEND_PID" "backend" "$DS_PORT_APP" "$DS_PORT_MGMT"
    if [ "$clean" -eq 1 ]; then
        ds_log "removing containers and volumes"
        ds_compose down --volumes
    else
        ds_log "removing containers, keeping volumes"
        ds_compose down
    fi
    ds_log "done"
}

ds_cmd_restart() {
    local tier="${1:-}"
    ds_resolve_instance
    case "$tier" in
        backend)
            ds_step "Restarting backend"
            ds_kill_tier "$DS_BACKEND_PID" "backend" "$DS_PORT_APP" "$DS_PORT_MGMT"
            ds_backend_up
            ;;
        frontend)
            ds_step "Restarting frontend"
            ds_kill_tier "$DS_FRONTEND_PID" "frontend" "$DS_PORT_UI"
            ds_frontend_up
            ;;
        *)
            ds_fail "usage: dev-stack.sh restart [backend|frontend]"
            ;;
    esac
}

ds_cmd_status() {
    ds_resolve_instance
    printf "Instance '%s'  slot %s  project %s\n\n" "$DS_SLUG" "$DS_SLOT" "$DS_PROJECT"
    local running
    running="$(ds_compose ps --services --filter status=running 2>/dev/null | tr '\n' ' ')"
    printf "  middleware  %s\n" "${running:-(none running)}"
    if curl -sf -o /dev/null "$DS_URL_MGMT/health"; then
        printf "  backend     up    %s\n" "$DS_URL_APP"
    else
        printf "  backend     down  %s\n" "$DS_URL_APP"
    fi
    if curl -sf -o /dev/null "$DS_URL_UI"; then
        printf "  frontend    up    %s\n" "$DS_URL_UI"
    else
        printf "  frontend    down  %s\n" "$DS_URL_UI"
    fi
}

# Docker is the registry: it knows every instance on this machine, including
# ones belonging to worktrees that have since been deleted.
ds_cmd_ls() {
    printf '%-28s %s\n' "INSTANCE" "STATUS"
    docker compose ls --all --format json \
        | python3 -c '
import json, sys
for p in json.load(sys.stdin):
    if p["Name"].startswith("yontrack-dev-"):
        print("%-28s %s" % (p["Name"], p["Status"]))
' 2>/dev/null || ds_warn "could not list Docker projects"
}

ds_cmd_logs() {
    local tier="${1:-backend}" follow="${2:-}"
    ds_resolve_instance
    case "$tier" in
        backend)  [ "$follow" = "-f" ] && tail -f "$DS_BACKEND_LOG" || cat "$DS_BACKEND_LOG" ;;
        frontend) [ "$follow" = "-f" ] && tail -f "$DS_FRONTEND_LOG" || cat "$DS_FRONTEND_LOG" ;;
        infra)    [ "$follow" = "-f" ] && ds_compose logs -f || ds_compose logs ;;
        *)        ds_fail "usage: dev-stack.sh logs [backend|frontend|infra] [-f]" ;;
    esac
}

ds_usage() {
    sed -n '3,22p' "$0" | sed 's/^#\{1,2\} \{0,1\}//'
}

main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        up)      ds_cmd_up "$@" ;;
        down)    ds_cmd_down "$@" ;;
        restart) ds_cmd_restart "$@" ;;
        status)  ds_cmd_status "$@" ;;
        ls)      ds_cmd_ls "$@" ;;
        logs)    ds_cmd_logs "$@" ;;
        ""|-h|--help|help) ds_usage ;;
        *)       ds_fail "unknown command '$command'. Try: dev-stack.sh --help" ;;
    esac
}

main "$@"
