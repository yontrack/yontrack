#!/usr/bin/env bash
#
# Tests for demo-smoke.sh. `curl` and the Yontrack CLI are stubbed on the PATH, so nothing
# here talks to a real instance: the stubs answer from canned JSON and record every call they
# receive, which is what the assertions read. The poll loop's clock and sleep are stubbed too,
# so a test of the ten-minute timeout takes no time at all.
#
# Usage: scripts/demo-smoke-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load demo-smoke.sh as a library: defines the functions, runs nothing.
DEMO_SMOKE_LIB_ONLY=1
export DEMO_SMOKE_LIB_ONLY
# shellcheck source=demo-smoke.sh
source "$SCRIPT_DIR/demo-smoke.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# ===========================================================================
# The stub
# ===========================================================================

STUB_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/demo-smoke-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$STUB_ROOT"' EXIT
mkdir -p "$STUB_ROOT/bin"

# Answers per GraphQL operation, read out of the request body: `info` for the version poll,
# `projects` for the dataset assertion. Emits the body followed by the status code, which is
# the shape `-w '\n%{http_code}'` produces.
cat > "$STUB_ROOT/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

body=""
url=""
prev=""
for arg in "$@"; do
    case "$prev" in
        -d) body="$arg" ;;
    esac
    case "$arg" in
        http*) url="$arg" ;;
    esac
    prev="$arg"
done

echo "$url <- $body" >> "$DSM_STUB_DIR/calls.log"

if [ -f "$DSM_STUB_DIR/transport_fails" ]; then
    echo "curl: (6) Could not resolve host" >&2
    exit 6
fi

status="$(cat "$DSM_STUB_DIR/status" 2>/dev/null || echo 200)"

case "$body" in
    *info*) file=info.json ;;
    *projects*) file=projects.json ;;
    *) file=unexpected.json ;;
esac

if [ -f "$DSM_STUB_DIR/$file" ]; then
    cat "$DSM_STUB_DIR/$file"
else
    echo '{"data":null}'
fi
echo "$status"
STUB
chmod +x "$STUB_ROOT/bin/curl"

# The Yontrack CLI, used only to resolve the deployed version back to a build name.
cat > "$STUB_ROOT/bin/yontrack" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

echo "$*" >> "$DSM_STUB_DIR/calls.log"

if [ "${1:-}" = "build" ] && [ "${2:-}" = "search" ]; then
    if [ -f "$DSM_STUB_DIR/search_fails" ]; then
        echo "cannot reach Yontrack" >&2
        exit 1
    fi
    if [ -f "$DSM_STUB_DIR/search_empty" ]; then
        exit 0
    fi
    cat "$DSM_STUB_DIR/build.json"
    exit 0
fi

echo "unexpected command: $*" >&2
exit 1
STUB
chmod +x "$STUB_ROOT/bin/yontrack"

PATH="$STUB_ROOT/bin:$PATH"
export PATH

DEMO_TOKEN="a-token"
export DEMO_TOKEN

# Resets the stub to a healthy default: the demo reports 5.3.0-rc-100 and the seeded
# project answers with a branch and a build.
setup_stub() {
    DSM_STUB_DIR="$(mktemp -d "$STUB_ROOT/case.XXXXXX")" || { echo "FATAL: mktemp failed" >&2; exit 1; }
    export DSM_STUB_DIR
    : > "$DSM_STUB_DIR/calls.log"

    cat > "$DSM_STUB_DIR/info.json" <<'JSON'
{"data":{"info":{"version":{"full":"5.3.0-rc-100"}}}}
JSON

    cat > "$DSM_STUB_DIR/projects.json" <<'JSON'
{"data":{"projects":[{"name":"petclinic","branches":[{"name":"main","builds":[{"name":"106"}]}]}]}}
JSON

    cat > "$DSM_STUB_DIR/build.json" <<'JSON'
{"Id":"100","Name":"20260901055547-100","DisplayName":"5.3.0-rc-100"}
JSON
}

calls() { cat "$DSM_STUB_DIR/calls.log"; }

# The poll loop's clock and sleep, driven by the tests rather than by the wall clock, so a
# test of the ten-minute timeout costs nothing. Both override the definitions demo-smoke.sh
# provides, and are called from there rather than from here.
DSM_TEST_NOW=0
# shellcheck disable=SC2329  # called by dsm_wait_for_version in the sourced library
dsm_now() { echo "$DSM_TEST_NOW"; }
# shellcheck disable=SC2329  # called by dsm_wait_for_version in the sourced library
dsm_sleep() { DSM_TEST_NOW=$(( DSM_TEST_NOW + $1 )); }

# --- dsm_version -------------------------------------------------------------

setup_stub
assert_eq "5.3.0-rc-100" "$(dsm_version)" "dsm_version reads the full version out of the payload"
assert_contains "$(calls)" "https://demo.dev.yontrack.com/graphql" \
    "dsm_version asks the demo's GraphQL endpoint"

# /rest/* is not routed to the backend by the chart's ingress - it lands on the Next UI and
# answers 404 - so the smoke test must never reach for it.
assert_not_contains "$(calls)" "/rest/" "dsm_version does not use the REST API"

setup_stub
echo '{"errors":[{"message":"Authentication is required"}]}' > "$DSM_STUB_DIR/info.json"
out="$(dsm_version 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_version fails when the payload carries GraphQL errors"
assert_contains "$out" "Authentication is required" "dsm_version surfaces the GraphQL error"

setup_stub
echo 503 > "$DSM_STUB_DIR/status"
out="$(dsm_version 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_version fails on a non-2xx status"
assert_contains "$out" "503" "dsm_version reports the status it got"

setup_stub
touch "$DSM_STUB_DIR/transport_fails"
out="$(dsm_version 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_version fails when the host cannot be reached"

# --- dsm_wait_for_version ----------------------------------------------------

# The whole point of the poll: the slot marks itself deployed when the gitops PR merges,
# so the demo answers with the *previous* version for a while.

# DSM_TIMEOUT, DSM_INTERVAL and DSM_SEEDED_PROJECT are read by the sourced library, so they look
# unused from here.
setup_stub
DSM_TEST_NOW=0
# shellcheck disable=SC2034
DSM_TIMEOUT=600
# shellcheck disable=SC2034
DSM_INTERVAL=15
out="$(dsm_wait_for_version 5.3.0-rc-100 2>&1)"; rc=$?
assert_eq "0" "$rc" "dsm_wait_for_version succeeds when the demo already runs the version"
assert_contains "$out" "after 1 attempt" "dsm_wait_for_version stops at the first match"

setup_stub
DSM_TEST_NOW=0
out="$(dsm_wait_for_version 5.3.0-rc-101 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_wait_for_version fails when the demo keeps reporting the old version"
assert_contains "$out" "5.3.0-rc-100" "dsm_wait_for_version says what the demo actually reports"
assert_contains "$out" "expected 5.3.0-rc-101" "dsm_wait_for_version says what it was waiting for"

# A demo that never comes up at all must fail the same way, not hang: this is the
# "deliberately broken demo fails it rather than timing out silently" case.
setup_stub
touch "$DSM_STUB_DIR/transport_fails"
DSM_TEST_NOW=0
out="$(dsm_wait_for_version 5.3.0-rc-100 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_wait_for_version fails when the demo never answers"
assert_contains "$out" "unreachable" "dsm_wait_for_version says the demo was unreachable"

# The deadline is a deadline: with a zero timeout the loop still tries once, then gives up.
setup_stub
DSM_TEST_NOW=0
DSM_TIMEOUT=0
out="$(dsm_wait_for_version 5.3.0-rc-999 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_wait_for_version gives up once the deadline has passed"
assert_eq "1" "$(grep -c graphql "$DSM_STUB_DIR/calls.log")" \
    "dsm_wait_for_version still makes one attempt when the deadline is already reached"
# shellcheck disable=SC2034
DSM_TIMEOUT=600

# --- dsm_assert_seeded_project -----------------------------------------------

setup_stub
out="$(dsm_assert_seeded_project 2>&1)"; rc=$?
assert_eq "0" "$rc" "dsm_assert_seeded_project passes on a seeded demo"
assert_contains "$out" "main/106" "dsm_assert_seeded_project names the branch and build it found"

setup_stub
echo '{"data":{"projects":[]}}' > "$DSM_STUB_DIR/projects.json"
out="$(dsm_assert_seeded_project 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_assert_seeded_project fails when the project is missing"
assert_contains "$out" "petclinic" "dsm_assert_seeded_project names the project it looked for"

setup_stub
echo '{"data":{"projects":[{"name":"petclinic","branches":[]}]}}' > "$DSM_STUB_DIR/projects.json"
out="$(dsm_assert_seeded_project 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_assert_seeded_project fails when the project has no branch"

# An empty project is what a seed that deleted everything and then died looks like, so it
# must not pass for "the project is there".
setup_stub
echo '{"data":{"projects":[{"name":"petclinic","branches":[{"name":"main","builds":[]}]}]}}' \
    > "$DSM_STUB_DIR/projects.json"
out="$(dsm_assert_seeded_project 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_assert_seeded_project fails when the branch has no build"

setup_stub
DSM_SEEDED_PROJECT="another-project"
out="$(dsm_assert_seeded_project 2>&1)"
assert_contains "$(calls)" "another-project" "dsm_assert_seeded_project asks for the configured project"
# shellcheck disable=SC2034
DSM_SEEDED_PROJECT="petclinic"

# --- dsm_resolve_build -------------------------------------------------------

# `yontrack validate --build` takes the build *name*, and the slot workflow can only pass the
# version, so the version has to be resolved back to a build before anything can be reported.

setup_stub
assert_eq "20260901055547-100" "$(dsm_resolve_build 5.3.0-rc-100)" \
    "dsm_resolve_build returns the build name, not the display name"

# Shared with demo-deploy.sh: the display name is matched partially and case-insensitively by
# the server, so an unanchored version would resolve to a different build.
setup_stub
out="$(dsm_resolve_build 5.3.0-rc-10 2>&1)"
assert_contains "$(calls)" '--with-display-name ^5\.3\.0-rc-10$' \
    "dsm_resolve_build asks for an exact match on the display name"

setup_stub
touch "$DSM_STUB_DIR/search_empty"
out="$(dsm_resolve_build 5.3.0-rc-999 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_resolve_build fails when the version names no build"
assert_contains "$out" "5.3.0-rc-999" "dsm_resolve_build names the version it looked for"

setup_stub
touch "$DSM_STUB_DIR/search_fails"
out="$(dsm_resolve_build 5.3.0-rc-100 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_resolve_build fails when Yontrack cannot be reached"

setup_stub
echo '{"unexpected":"shape"}' > "$DSM_STUB_DIR/build.json"
out="$(dsm_resolve_build 5.3.0-rc-100 2>&1)"; rc=$?
assert_eq "1" "$rc" "dsm_resolve_build fails rather than validating something unnamed"

# --- the entry points -------------------------------------------------------

setup_stub
GITHUB_OUTPUT="$DSM_STUB_DIR/github_output"
export GITHUB_OUTPUT
out="$(DEMO_VERSION=5.3.0-rc-100 dsm_resolve 2>&1)"; rc=$?
assert_eq "0" "$rc" "resolve: passes when the version names a build"
assert_contains "$(cat "$GITHUB_OUTPUT")" "build=20260901055547-100" \
    "resolve: hands the build name to the next step"
unset GITHUB_OUTPUT

setup_stub
out="$(DEMO_VERSION='' dsm_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: refuses to run without a version"


setup_stub
DSM_TEST_NOW=0
out="$(DEMO_VERSION=5.3.0-rc-100 dsm_poll 2>&1)"; rc=$?
assert_eq "0" "$rc" "poll: passes when the demo runs the deployed version"

setup_stub
out="$(DEMO_VERSION='' dsm_poll 2>&1)"; rc=$?
assert_eq "1" "$rc" "poll: refuses to run without a version to wait for"
assert_not_contains "$(calls)" "graphql" "poll: asks nothing when it has no version"

setup_stub
out="$(DEMO_TOKEN='' DEMO_VERSION=5.3.0-rc-100 dsm_poll 2>&1)"; rc=$?
assert_eq "1" "$rc" "poll: refuses to run without a token"
assert_contains "$out" "DEMO_TOKEN" "poll: says which variable is missing"

setup_stub
out="$(dsm_assert 2>&1)"; rc=$?
assert_eq "0" "$rc" "assert: passes on a seeded demo"

setup_stub
out="$(dsm_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "resolve|poll|assert" "no command: prints the usage"

# --- report ----------------------------------------------------------------

report_tests
