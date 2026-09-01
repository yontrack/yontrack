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
    if [ -f "$DD_STUB_DIR/latest_fails" ]; then
        echo "no build found" >&2
        exit 1
    fi
    emit latest.json
    exit 0
fi

if [ "${1:-}" = "graphql" ]; then
    query="$*"
    case "$query" in
        *ResolveBuild*)      emit resolve.json ;;
        *DemoSlot*)          emit slot.json ;;
        *StartDemoPipeline*) emit start.json ;;
        *)                   echo "unexpected query: $query" >&2; exit 1 ;;
    esac
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
  "environmentByName": {
    "slots": [
      {
        "id": "slot-demo-yontrack",
        "lastDeployedPipeline": {
          "build": {"id": "90", "displayName": "5.3.0-rc-90"}
        }
      }
    ]
  }
}
JSON

    cat > "$DD_STUB_DIR/start.json" <<'JSON'
{"startSlotPipeline": {"pipeline": {"id": "pipeline-1", "number": 7}, "errors": null}}
JSON

    cat > "$DD_STUB_DIR/resolve.json" <<'JSON'
{
  "byDisplayName": [{"id": "77", "name": "20260801010101-77", "displayName": "5.3.0-rc-77"}],
  "byName": []
}
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

# --- dd_numeric ------------------------------------------------------------

dd_numeric 123 && r=ok || r=no
assert_eq "ok" "$r" "dd_numeric accepts a plain integer"

dd_numeric "12x" && r=ok || r=no
assert_eq "no" "$r" "dd_numeric rejects a non-integer"

dd_numeric "" && r=ok || r=no
assert_eq "no" "$r" "dd_numeric rejects an empty string"

dd_numeric '1 OR 1=1' && r=ok || r=no
assert_eq "no" "$r" "dd_numeric rejects anything that could be injected into the query"

# --- acceptance: the nightly deploys the latest promoted build -------------

setup_stub
out="$(dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "nightly: succeeds"
assert_contains "$(calls)" "StartDemoPipeline" "nightly: starts a pipeline"
assert_contains "$(calls)" "buildId: 100" "nightly: starts it for the latest promoted build"
assert_contains "$(calls)" "slotId=slot-demo-yontrack" "nightly: targets the demo slot"
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

# A payload carrying neither a pipeline nor an error means nothing was started,
# whatever the HTTP status said.
setup_stub
cat > "$DD_STUB_DIR/start.json" <<'JSON'
{"startSlotPipeline": {"pipeline": null, "errors": []}}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no pipeline in the payload: fails rather than reporting success"

# --- acceptance: a second nightly with no new build exits early ------------

setup_stub
cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{
  "environmentByName": {
    "slots": [
      {
        "id": "slot-demo-yontrack",
        "lastDeployedPipeline": {
          "build": {"id": "100", "displayName": "5.3.0-rc-100"}
        }
      }
    ]
  }
}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "unchanged nightly: succeeds"
assert_not_contains "$(calls)" "StartDemoPipeline" "unchanged nightly: starts no pipeline"
assert_contains "$out" "already" "unchanged nightly: says why it stopped"

# --- acceptance: a manual dispatch deploys even when unchanged -------------

setup_stub
cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{
  "environmentByName": {
    "slots": [
      {
        "id": "slot-demo-yontrack",
        "lastDeployedPipeline": {
          "build": {"id": "100", "displayName": "5.3.0-rc-100"}
        }
      }
    ]
  }
}
JSON
out="$(DEMO_MANUAL=true dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "manual redeploy: succeeds"
assert_contains "$(calls)" "buildId: 100" "manual redeploy: redeploys the same build"

# --- acceptance: a manual dispatch with an explicit display name -----------

setup_stub
out="$(DEMO_MANUAL=true DEMO_BUILD=5.3.0-rc-77 dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "explicit build: succeeds"
assert_contains "$(calls)" "ResolveBuild" "explicit build: resolves the display name"
assert_contains "$(calls)" "buildId: 77" "explicit build: deploys the named build"
assert_not_contains "$(calls)" "--with-promotion" "explicit build: does not fall back to the latest promoted build"

# An explicit name is matched on the build name too, the way Yontrack's own
# display-name lookup falls back.
setup_stub
cat > "$DD_STUB_DIR/resolve.json" <<'JSON'
{
  "byDisplayName": [],
  "byName": [{"id": "55", "name": "20260801010101-55", "displayName": "20260801010101-55"}]
}
JSON
out="$(DEMO_MANUAL=true DEMO_BUILD=20260801010101-55 dd_main 2>&1)"; rc=$?
assert_eq "0" "$rc" "explicit build by name: succeeds"
assert_contains "$(calls)" "buildId: 55" "explicit build by name: falls back to the build name"

# --- failure modes ---------------------------------------------------------

setup_stub
cat > "$DD_STUB_DIR/resolve.json" <<'JSON'
{"byDisplayName": [], "byName": []}
JSON
out="$(DEMO_MANUAL=true DEMO_BUILD=nope dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "unknown build: fails"
assert_not_contains "$(calls)" "StartDemoPipeline" "unknown build: starts no pipeline"

setup_stub
rm -f "$DD_STUB_DIR/latest.json"
touch "$DD_STUB_DIR/latest_fails"
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no promoted build: fails rather than deploying nothing"

setup_stub
cat > "$DD_STUB_DIR/start.json" <<'JSON'
{"startSlotPipeline": {"pipeline": null, "errors": [{"message": "Build is not eligible"}]}}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "payload errors: fails the job"
assert_contains "$out" "Build is not eligible" "payload errors: surfaces the message"

setup_stub
cat > "$DD_STUB_DIR/slot.json" <<'JSON'
{"environmentByName": {"slots": []}}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no slot: fails"
assert_not_contains "$(calls)" "StartDemoPipeline" "no slot: starts no pipeline"

setup_stub
cat > "$DD_STUB_DIR/latest.json" <<'JSON'
{"Id":"not-a-number","Name":"weird","DisplayName":"weird"}
JSON
out="$(dd_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "non-numeric build id: fails before building a query"
assert_not_contains "$(calls)" "StartDemoPipeline" "non-numeric build id: starts no pipeline"

# --- report ----------------------------------------------------------------

echo
if [ "$tests_failed" -eq 0 ]; then
    echo "OK: $tests_run assertions passed"
    exit 0
else
    echo "FAILED: $tests_failed of $tests_run assertions failed"
    exit 1
fi
