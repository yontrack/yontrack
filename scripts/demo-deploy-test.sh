#!/usr/bin/env bash
#
# Tests for demo-deploy.sh. The Yontrack CLI is stubbed on the PATH, so nothing
# here talks to a real instance: the stub answers from canned JSON and records
# every call it receives, which is what the assertions read.
#
# Usage: scripts/demo-deploy-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load demo-deploy.sh as a library: defines the functions, runs nothing.
DEMO_DEPLOY_LIB_ONLY=1
export DEMO_DEPLOY_LIB_ONLY
# shellcheck source=demo-deploy.sh
source "$SCRIPT_DIR/demo-deploy.sh"

tests_run=0
tests_failed=0

assert_eq() {
    local expected="$1" actual="$2" message="$3"
    tests_run=$((tests_run + 1))
    if [ "$expected" != "$actual" ]; then
        tests_failed=$((tests_failed + 1))
        echo "FAIL: $message"
        echo "      expected: '$expected'"
        echo "      actual:   '$actual'"
    fi
}

assert_contains() {
    local haystack="$1" needle="$2" message="$3"
    tests_run=$((tests_run + 1))
    case "$haystack" in
        *"$needle"*) ;;
        *)
            tests_failed=$((tests_failed + 1))
            echo "FAIL: $message"
            echo "      expected to contain: '$needle'"
            echo "      actual:              '$haystack'"
            ;;
    esac
}

assert_not_contains() {
    local haystack="$1" needle="$2" message="$3"
    tests_run=$((tests_run + 1))
    case "$haystack" in
        *"$needle"*)
            tests_failed=$((tests_failed + 1))
            echo "FAIL: $message"
            echo "      expected NOT to contain: '$needle'"
            echo "      actual:                  '$haystack'"
            ;;
    esac
}

# ===========================================================================
# The stub
# ===========================================================================

STUB_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/demo-deploy-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$STUB_ROOT"' EXIT
mkdir -p "$STUB_ROOT/bin"

# Dispatches on the command line it is given: `build search` answers with the
# latest promoted build, `graphql` answers per operation name. Every invocation
# is appended to calls.log so the assertions can see what was asked for.
cat > "$STUB_ROOT/bin/yontrack" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

echo "$*" >> "$DD_STUB_DIR/calls.log"

emit() {
    local file="$DD_STUB_DIR/$1"
    if [ -f "$file" ]; then
        cat "$file"
    fi
}

if [ "${1:-}" = "build" ] && [ "${2:-}" = "search" ]; then
    case "$*" in
        *--with-display-name*)
            if [ -f "$DD_STUB_DIR/resolve_empty" ]; then exit 0; fi
            emit resolve.json
            ;;
        *)
            if [ -f "$DD_STUB_DIR/latest_fails" ]; then
                echo "no build found" >&2
                exit 1
            fi
            emit latest.json
            ;;
    esac
    exit 0
fi

if [ "${1:-}" = "slot" ] && [ "${2:-}" = "get" ]; then
    if [ -f "$DD_STUB_DIR/slot_fails" ]; then
        echo "no slot for project" >&2
        exit 1
    fi
    emit slot.json
    exit 0
fi

if [ "${1:-}" = "slot" ] && [ "${2:-}" = "pipeline" ] && [ "${3:-}" = "start" ]; then
    if [ -f "$DD_STUB_DIR/start_fails" ]; then
        echo "1) Build is not eligible" >&2
        exit 1
    fi
    emit start.json
    exit 0
fi

echo "unexpected command: $*" >&2
exit 1
STUB
chmod +x "$STUB_ROOT/bin/yontrack"

PATH="$STUB_ROOT/bin:$PATH"
export PATH

# Resets the stub to a healthy default: latest BRONZE is build 100
# (5.3.0-rc-100), the demo currently runs build 90, and starting a pipeline
# succeeds.
setup_stub() {
    DD_STUB_DIR="$(mktemp -d "$STUB_ROOT/case.XXXXXX")" || { echo "FATAL: mktemp failed" >&2; exit 1; }
    export DD_STUB_DIR
    : > "$DD_STUB_DIR/calls.log"

    cat > "$DD_STUB_DIR/latest.json" <<'JSON'
{"Id":"100","Name":"20260901055547-100","DisplayName":"5.3.0-rc-100"}
JSON

    cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{
  "id": "slot-demo-yontrack",
  "lastDeployedPipeline": {
    "id": "pipeline-0",
    "number": 6,
    "build": {"id": "90", "name": "20260801010101-90", "displayName": "5.3.0-rc-90"}
  }
}
JSON

    cat > "$DD_STUB_DIR/start.json" <<'JSON'
{"id": "pipeline-1", "number": 7, "build": null}
JSON

    cat > "$DD_STUB_DIR/resolve.json" <<'JSON'
{"Id":"77","Name":"20260801010101-77","DisplayName":"5.3.0-rc-77"}
JSON
}

calls() { cat "$DD_STUB_DIR/calls.log"; }

# --- dd_should_deploy (pure) ----------------------------------------------

dd_should_deploy 100 90 false && r=deploy || r=skip
assert_eq "deploy" "$r" "dd_should_deploy: a new build on a nightly deploys"

dd_should_deploy 100 100 false && r=deploy || r=skip
assert_eq "skip" "$r" "dd_should_deploy: an unchanged build on a nightly is skipped"

dd_should_deploy 100 100 true && r=deploy || r=skip
assert_eq "deploy" "$r" "dd_should_deploy: an unchanged build is still deployed when dispatched manually"

dd_should_deploy 100 "" false && r=deploy || r=skip
assert_eq "deploy" "$r" "dd_should_deploy: a slot that has never deployed anything deploys"

# --- dd_exact_pattern ------------------------------------------------------

# `--with-display-name` is matched case-insensitively and *partially* by the
# server, so a bare version would match more builds than the one asked for.

assert_eq '^5\.3\.0-rc-45$' "$(dd_exact_pattern 5.3.0-rc-45)" \
    "dd_exact_pattern anchors the pattern and escapes the dots"

assert_eq '^20260801010101-55$' "$(dd_exact_pattern 20260801010101-55)" \
    "dd_exact_pattern leaves a plain build name alone apart from the anchors"

# The dots are the ones that matter in practice: unescaped, 5.3.0-rc-4 would
# match 5x3x0-rc-4, and unanchored it would match 5.3.0-rc-45.
assert_contains "$(dd_exact_pattern 5.3.0-rc-4)" '5\.3\.0-rc-4' \
    "dd_exact_pattern escapes every dot"

assert_eq '^a\*b\+c\[d\]$' "$(dd_exact_pattern 'a*b+c[d]')" \
    "dd_exact_pattern escapes the other regex metacharacters too"

# --- acceptance: the nightly deploys the latest promoted build -------------

setup_stub
out="$(dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "nightly: succeeds"
assert_contains "$(calls)" "slot pipeline start" "nightly: starts a pipeline"
assert_contains "$(calls)" "--build 20260901055547-100" "nightly: starts it for the latest promoted build"
assert_contains "$(calls)" "--environment demo.dev.yontrack.com" "nightly: targets the demo environment"
assert_contains "$(calls)" "--with-promotion BRONZE" "nightly: resolves against the BRONZE promotion"
assert_contains "$out" "5.3.0-rc-100" "nightly: reports the build it deployed"

# The pipeline it started has to be named in the log: it is the only handle on
# what happens next, and a deployment that stalls downstream is otherwise
# invisible from the workflow run.
assert_contains "$out" "pipeline-1" "nightly: logs the pipeline id"
assert_contains "$out" "#7" "nightly: logs the pipeline number"

# --- the pipeline is exposed as a step output ------------------------------

setup_stub
GITHUB_OUTPUT="$DD_STUB_DIR/github_output"
export GITHUB_OUTPUT
: > "$GITHUB_OUTPUT"
out="$(dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "outputs: succeeds"
assert_contains "$(cat "$GITHUB_OUTPUT")" "pipeline=pipeline-1" "outputs: exposes the pipeline id"
assert_contains "$(cat "$GITHUB_OUTPUT")" "deployed=true" "outputs: flags that it deployed"
assert_contains "$(cat "$GITHUB_OUTPUT")" "build=5.3.0-rc-100" "outputs: names the build"
unset GITHUB_OUTPUT

# A payload carrying neither a pipeline nor an error used to be checked here.
# `slot pipeline start` now rejects it itself, and is tested for it in the CLI,
# so asserting it again from a stub would only be testing the stub.

# --- acceptance: a second nightly with no new build exits early ------------

setup_stub
cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{
  "id": "slot-demo-yontrack",
  "lastDeployedPipeline": {
    "id": "pipeline-0",
    "number": 6,
    "build": {"id": "100", "name": "20260901055547-100", "displayName": "5.3.0-rc-100"}
  }
}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "unchanged nightly: succeeds"
assert_not_contains "$(calls)" "slot pipeline start" "unchanged nightly: starts no pipeline"
assert_contains "$out" "already" "unchanged nightly: says why it stopped"

# --- acceptance: a manual dispatch deploys even when unchanged -------------

setup_stub
cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{
  "id": "slot-demo-yontrack",
  "lastDeployedPipeline": {
    "id": "pipeline-0",
    "number": 6,
    "build": {"id": "100", "name": "20260901055547-100", "displayName": "5.3.0-rc-100"}
  }
}
JSON
out="$(DEMO_MANUAL=true dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "manual redeploy: succeeds"
assert_contains "$(calls)" "--build 20260901055547-100" "manual redeploy: redeploys the same build"

# --- acceptance: a manual dispatch with an explicit display name -----------

setup_stub
out="$(DEMO_MANUAL=true DEMO_BUILD=5.3.0-rc-77 dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "explicit build: succeeds"
assert_contains "$(calls)" "--with-display-name" "explicit build: resolves the display name"
assert_contains "$(calls)" "--build 20260801010101-77" "explicit build: deploys the named build"
assert_not_contains "$(calls)" "--with-promotion" "explicit build: does not fall back to the latest promoted build"

# A build with no release property has its own name as its display name, so the
# same lookup finds it - the server matches `withDisplayName` against the
# release property falling back to the build name.
setup_stub
cat > "$DD_STUB_DIR/resolve.json" <<'JSON'
{"Id":"55","Name":"20260801010101-55","DisplayName":"20260801010101-55"}
JSON
out="$(DEMO_MANUAL=true DEMO_BUILD=20260801010101-55 dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "explicit build by name: succeeds"
assert_contains "$(calls)" "--build 20260801010101-55" "explicit build by name: falls back to the build name"

# --- failure modes ---------------------------------------------------------

setup_stub
touch "$DD_STUB_DIR/resolve_empty"
out="$(DEMO_MANUAL=true DEMO_BUILD=nope dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "unknown build: fails"
assert_not_contains "$(calls)" "slot pipeline start" "unknown build: starts no pipeline"

setup_stub
rm -f "$DD_STUB_DIR/latest.json"
touch "$DD_STUB_DIR/latest_fails"
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no promoted build: fails rather than deploying nothing"

# A refused deployment is the CLI's failure to report now: `slot pipeline start`
# checks the payload errors itself and exits non-zero.
setup_stub
touch "$DD_STUB_DIR/start_fails"
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "refused deployment: fails the job"
assert_contains "$out" "Build is not eligible" "refused deployment: surfaces the message"

# Likewise a missing slot: `slot get` fails rather than returning an empty list
# for the caller to notice.
setup_stub
touch "$DD_STUB_DIR/slot_fails"
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no slot: fails"
assert_not_contains "$(calls)" "slot pipeline start" "no slot: starts no pipeline"

setup_stub
cat > "$DD_STUB_DIR/latest.json" <<'JSON'
{"unexpected": "shape"}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "unreadable build: fails rather than deploying something unnamed"
assert_not_contains "$(calls)" "slot pipeline start" "unreadable build: starts no pipeline"

# --- report ----------------------------------------------------------------

echo
if [ "$tests_failed" -eq 0 ]; then
    echo "OK: $tests_run assertions passed"
    exit 0
else
    echo "FAILED: $tests_failed of $tests_run assertions failed"
    exit 1
fi
