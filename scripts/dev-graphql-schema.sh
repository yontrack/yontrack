#!/usr/bin/env bash
#
# Downloads the GraphQL schema (SDL) of the local dev stack into the given file.
#
#   scripts/dev-graphql-schema.sh <output-file>
#
# The management port is read from .yontrack-dev/instance.env, written by
# `scripts/dev-stack.sh up` -- every checkout gets its own ports, so 8800 is only
# right for the main working copy. YONTRACK_DEV_MGMT_PORT, when set, wins.
#
# The `graphql` actuator endpoint is not exposed by default (#1772); the dev
# stack exposes it explicitly. Against any other instance, add `graphql` to
# MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE.

set -euo pipefail

output="${1:?Usage: $0 <output-file>}"

toplevel="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
instance_env="$toplevel/.yontrack-dev/instance.env"

port="${YONTRACK_DEV_MGMT_PORT:-}"
if [[ -z "$port" && -f "$instance_env" ]]; then
    port="$(sed -n 's/^YONTRACK_DEV_MGMT_PORT=//p' "$instance_env")"
fi
if [[ -z "$port" ]]; then
    echo "ERROR: no management port - start the stack with scripts/dev-stack.sh up, or set YONTRACK_DEV_MGMT_PORT." >&2
    exit 1
fi

url="http://localhost:$port/manage/graphql"
tmp="$(mktemp "$output.XXXXXX")"
trap 'rm -f "$tmp"' EXIT

# Downloads to a temporary file first, so that a stack which is down, or which
# does not expose the endpoint, never leaves an error page in place of the schema.
if ! curl -fsS "$url" --output "$tmp"; then
    echo "ERROR: cannot get the schema from $url - is the dev stack up (scripts/dev-stack.sh status)?" >&2
    exit 1
fi

cat "$tmp" > "$output"
echo "Schema from $url written to $output"
