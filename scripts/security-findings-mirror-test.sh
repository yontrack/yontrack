#!/usr/bin/env bash
#
# Tests for security-findings-mirror.sh. `gh` and `yontrack` are stubbed on the PATH, so nothing
# here calls GitHub or a Yontrack instance: the stubs answer from canned JSON and record every
# call they receive, which is what the assertions read. `jq` and `yq` are the real ones, and the
# thresholds are read from the real .yontrack/ci.yaml - they are what is being tested.
#
# Usage: scripts/security-findings-mirror-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load security-findings-mirror.sh as a library: defines the functions, runs nothing.
SECURITY_FINDINGS_MIRROR_LIB_ONLY=1
export SECURITY_FINDINGS_MIRROR_LIB_ONLY
# shellcheck source=security-findings-mirror.sh
source "$SCRIPT_DIR/security-findings-mirror.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/security-findings-mirror-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Stubs
# ===========================================================================

# `gh api --paginate`: answers the open alerts with STUB_GH_OPEN and the dismissed ones with
# STUB_GH_DISMISSED, by the `state` of the query. STUB_GH_FAIL makes it exit 1.
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "gh $*" >> "$STUB_CALLS"
if [ -n "${STUB_GH_FAIL:-}" ]; then
    echo "gh: Not Found (HTTP 404)" >&2
    exit 1
fi
case "$*" in
    *state=open*) cat "$STUB_GH_OPEN" ;;
    *state=dismissed*) cat "$STUB_GH_DISMISSED" ;;
esac
STUB
chmod +x "$WORK/bin/gh"

# `yontrack`: records its arguments, answers `graphql` with the version in
# STUB_YONTRACK_VERSION, and fails every call whose arguments contain STUB_YONTRACK_FAIL_ON.
cat > "$WORK/bin/yontrack" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "yontrack $*" >> "$STUB_CALLS"
if [ -n "${STUB_YONTRACK_FAIL_ON:-}" ] && [[ "$*" == *"$STUB_YONTRACK_FAIL_ON"* ]]; then
    echo "Error: the call failed" >&2
    exit 1
fi
case "$*" in
    *graphql*) printf '{"info": {"version": {"display": "%s"}}}\n' "$STUB_YONTRACK_VERSION" ;;
esac
STUB
chmod +x "$WORK/bin/yontrack"

setup_stubs() {
    : > "$WORK/calls"
    : > "$WORK/github_output"
    STUB_CALLS="$WORK/calls"
    STUB_GH_OPEN="$WORK/open.json"
    STUB_GH_DISMISSED="$WORK/dismissed.json"
    STUB_GH_FAIL=""
    STUB_YONTRACK_VERSION="5.9.0"
    STUB_YONTRACK_FAIL_ON=""
    SECURITY_FINDINGS_MIRROR_CONFIG="$WORK/mirror-config.yaml"
    export STUB_CALLS STUB_GH_OPEN STUB_GH_DISMISSED STUB_GH_FAIL STUB_YONTRACK_VERSION \
        STUB_YONTRACK_FAIL_ON SECURITY_FINDINGS_MIRROR_CONFIG
}
calls() { cat "$WORK/calls"; }

# ===========================================================================
# Fixtures - the shape of the code-scanning alerts API
# ===========================================================================

cat > "$WORK/open.json" <<'JSON'
[
  {
    "number": 12,
    "state": "open",
    "html_url": "https://github.com/yontrack/yontrack/security/code-scanning/12",
    "rule": {
      "id": "java/sql-injection",
      "security_severity_level": "high",
      "description": "Query built from user-controlled sources"
    },
    "most_recent_instance": {"location": {"path": "ontrack-service/src/Foo.kt", "start_line": 42}}
  },
  {
    "number": 13,
    "state": "open",
    "html_url": "https://github.com/yontrack/yontrack/security/code-scanning/13",
    "rule": {"id": "js/unused-local-variable", "security_severity_level": null, "description": "Unused variable"},
    "most_recent_instance": {"location": {"path": "ontrack-web-core/a.js", "start_line": 1}}
  }
]
[
  {
    "number": 14,
    "state": "open",
    "html_url": "https://github.com/yontrack/yontrack/security/code-scanning/14",
    "rule": {"id": "js/xss", "security_severity_level": "critical", "description": "Cross-site scripting"},
    "most_recent_instance": {"location": {"path": "ontrack-web-core/b.js", "start_line": 7}}
  }
]
JSON

cat > "$WORK/dismissed.json" <<'JSON'
[
  {
    "number": 9,
    "state": "dismissed",
    "html_url": "https://github.com/yontrack/yontrack/security/code-scanning/9",
    "dismissed_reason": "false positive",
    "dismissed_comment": "Input is a constant",
    "rule": {"id": "java/path-injection", "security_severity_level": "medium", "description": "Path from user input"},
    "most_recent_instance": {"location": {"path": "ontrack-ui/src/Bar.kt", "start_line": 3}}
  },
  {
    "number": 10,
    "state": "dismissed",
    "html_url": "https://github.com/yontrack/yontrack/security/code-scanning/10",
    "dismissed_reason": "won't fix",
    "dismissed_comment": null,
    "rule": {"id": "java/weak-cryptographic-algorithm", "security_severity_level": "low", "description": "Weak crypto"},
    "most_recent_instance": {"location": {"path": "ontrack-ui/src/Baz.kt", "start_line": 5}}
  }
]
JSON

echo '[]' > "$WORK/empty.json"
echo '{"message": "Not Found"}' > "$WORK/error.json"

# ===========================================================================
# enabled
# ===========================================================================

enabled() {
    : > "$WORK/github_output"
    GITHUB_OUTPUT="$WORK/github_output" sfm_enabled "$1" > /dev/null
    cat "$WORK/github_output"
}

assert_eq "enabled=true" "$(enabled v6)" "enabled: the v6 branch mirrors"
assert_eq "enabled=false" "$(enabled main)" "enabled: main does not"
assert_eq "enabled=false" "$(enabled v6-spring-boot-4)" "enabled: a v6 feature branch does not"
assert_eq "enabled=false" "$(enabled claude/v6-findings-mirror-pipeline)" "enabled: nor does an agent branch"
assert_eq "enabled=false" "$(enabled "")" "enabled: no branch does not"

# ===========================================================================
# guard
# ===========================================================================

echo "5.9" > "$WORK/VERSION-5"
echo "6.0" > "$WORK/VERSION-6"

guard() { PATH="$WORK/bin:$PATH" sfm_guard "$@" 2>&1; }

setup_stubs
out="$(guard main "$WORK/VERSION-5")"; rc=$?
assert_eq "0" "$rc" "guard: a 5.x build on main passes"
assert_eq "" "$out" "guard: and says nothing"
assert_eq "" "$(calls)" "guard: without asking Yontrack"

setup_stubs
out="$(guard v6 "$WORK/VERSION-6")"; rc=$?
assert_eq "0" "$rc" "guard: v6 itself passes - it has the mirror"
assert_eq "" "$(calls)" "guard: v6 does not ask Yontrack"

setup_stubs
out="$(guard claude/v6-something-pipeline "$WORK/VERSION-6")"; rc=$?
assert_eq "0" "$rc" "guard: a feature branch of v6 passes"
assert_eq "" "$(calls)" "guard: a feature branch does not ask Yontrack"

setup_stubs
STUB_YONTRACK_VERSION="5.9.1"
out="$(guard main "$WORK/VERSION-6")"; rc=$?
assert_eq "0" "$rc" "guard: a 6.x build on main while self.dev still runs 5.x only warns - it must not block the 6.0 release"
assert_contains "$out" "::warning" "guard: as a warning annotation"
assert_contains "$out" "#1875" "guard: pointing at the switch-back issue"
assert_contains "$(calls)" "graphql" "guard: asks Yontrack for its version"

setup_stubs
STUB_YONTRACK_VERSION="6.0.2"
out="$(guard main "$WORK/VERSION-6")"; rc=$?
assert_eq "1" "$rc" "guard: a 6.x build on main once self.dev runs 6.x fails"
assert_contains "$out" "::error" "guard: as an error annotation"
assert_contains "$out" "#1875" "guard: pointing at the switch-back issue"

setup_stubs
STUB_YONTRACK_VERSION="6.1.0"
out="$(guard release/6.1 "$WORK/VERSION-6")"; rc=$?
assert_eq "1" "$rc" "guard: a release branch is held to the same rule"

setup_stubs
STUB_YONTRACK_FAIL_ON="graphql"
out="$(guard main "$WORK/VERSION-6")"; rc=$?
assert_eq "0" "$rc" "guard: a Yontrack that cannot be asked only warns"
assert_contains "$out" "::warning" "guard: with a warning"
assert_contains "$out" "#1875" "guard: still pointing at the switch-back issue"

setup_stubs
out="$(guard main "$WORK/does-not-exist")"; rc=$?
assert_eq "1" "$rc" "guard: a missing VERSION file is an error"

# ===========================================================================
# code-findings
# ===========================================================================

cat "$WORK/open.json" "$WORK/dismissed.json" > "$WORK/all.json"
report="$(sfm_code_findings "$WORK/all.json")"; rc=$?
assert_eq "0" "$rc" "code-findings: succeeds"
assert_eq "codeql" "$(echo "$report" | jq -r '.scanner')" "code-findings: the scanner is CodeQL"
assert_eq "CODE" "$(echo "$report" | jq -r '.kind')" "code-findings: the kind is CODE"
assert_eq "4" "$(echo "$report" | jq '.findings | length')" \
    "code-findings: every page, open and dismissed, without the code-quality alert"
assert_eq "java/sql-injection|ontrack-service/src/Foo.kt|HIGH|high" \
    "$(echo "$report" | jq -r '.findings[0] | "\(.externalId)|\(.location)|\(.severity)|\(.rawSeverity)"')" \
    "code-findings: rule id, path without line, severity"
assert_eq "Query built from user-controlled sources" "$(echo "$report" | jq -r '.findings[0].title')" \
    "code-findings: the rule description is the title"
assert_eq "https://github.com/yontrack/yontrack/security/code-scanning/12" \
    "$(echo "$report" | jq -r '.findings[0].url')" "code-findings: links to the alert"
assert_eq "null" "$(echo "$report" | jq -c '.findings[0].acceptance')" \
    "code-findings: an open alert is not accepted"
assert_eq "CRITICAL" "$(echo "$report" | jq -r '.findings[1].severity')" "code-findings: critical"
assert_eq '{"statement":"false positive: Input is a constant","source":"GitHub code scanning alert #9"}' \
    "$(echo "$report" | jq -c '.findings[2].acceptance')" \
    "code-findings: a dismissal is an acceptance, with its reason and comment"
assert_eq '{"statement":"won'"'"'t fix","source":"GitHub code scanning alert #10"}' \
    "$(echo "$report" | jq -c '.findings[3].acceptance')" \
    "code-findings: a dismissal without comment keeps its reason"
assert_eq "" "$(echo "$report" | jq -r '.findings[] | keys[] | select(IN("externalId","location","severity","rawSeverity","title","url","acceptance") | not)')" \
    "code-findings: no field the neutral format would reject"

report="$(sfm_code_findings "$WORK/empty.json")"; rc=$?
assert_eq "0" "$rc" "code-findings: no alert is a report"
assert_eq "0" "$(echo "$report" | jq '.findings | length')" "code-findings: with no finding"

out="$(sfm_code_findings "$WORK/error.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "code-findings: an error object is not an empty report"

out="$(sfm_code_findings "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "code-findings: a missing file is an error"

# ===========================================================================
# fetch-code
# ===========================================================================

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_fetch_code yontrack/yontrack refs/heads/v6 "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch-code: succeeds"
assert_contains "$(calls)" "--paginate" "fetch-code: follows every page"
assert_contains "$(calls)" "repos/yontrack/yontrack/code-scanning/alerts?ref=refs/heads/v6&state=open&tool_name=CodeQL" \
    "fetch-code: the open CodeQL alerts of the ref"
assert_contains "$(calls)" "repos/yontrack/yontrack/code-scanning/alerts?ref=refs/heads/v6&state=dismissed&tool_name=CodeQL" \
    "fetch-code: and the dismissed ones, which become acceptances"
assert_eq "4" "$(jq '.findings | length' "$WORK/code.json")" "fetch-code: writes the report"

setup_stubs
STUB_GH_FAIL=1
rm -f "$WORK/code.json"
out="$(PATH="$WORK/bin:$PATH" sfm_fetch_code yontrack/yontrack refs/heads/v6 "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch-code: an API error fails"
assert_eq "false" "$([ -f "$WORK/code.json" ] && echo true || echo false)" "fetch-code: and writes no report"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_fetch_code yontrack/yontrack "" "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch-code: refuses to run without a ref"

# ===========================================================================
# thresholds
# ===========================================================================

assert_eq '{"warningLevel":"HIGH","warningValue":1,"failedLevel":"CRITICAL","failedValue":1}' \
    "$(sfm_thresholds SECURITY.IMAGE.BACKEND)" "thresholds: read from .yontrack/ci.yaml, in the flat shape the setup mutation reads"
assert_eq '{"warningLevel":"HIGH","warningValue":1,"failedLevel":"CRITICAL","failedValue":1}' \
    "$(sfm_thresholds SECURITY.CODE)" "thresholds: of SECURITY.CODE too"

out="$(sfm_thresholds SECURITY.NOPE 2>&1)"; rc=$?
assert_eq "1" "$rc" "thresholds: a stamp without CHML thresholds is an error"

# ===========================================================================
# configure
# ===========================================================================

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_configure https://v6.dev.yontrack.com s3cr3t 2>&1)"; rc=$?
assert_eq "0" "$rc" "configure: succeeds"
assert_eq "yontrack --config $WORK/mirror-config.yaml config create mirror https://v6.dev.yontrack.com --token s3cr3t --override" \
    "$(calls)" "configure: into a configuration file of its own, leaving self.dev's alone"
assert_not_contains "$out" "s3cr3t" "configure: never prints the token"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_configure https://v6.dev.yontrack.com "" 2>&1)"; rc=$?
assert_eq "0" "$rc" "configure: a missing token never fails the build"
assert_contains "$out" "::warning" "configure: it warns"
assert_eq "" "$(calls)" "configure: and configures nothing"

setup_stubs
STUB_YONTRACK_FAIL_ON="config create"
out="$(PATH="$WORK/bin:$PATH" sfm_configure https://v6.dev.yontrack.com s3cr3t 2>&1)"; rc=$?
assert_eq "0" "$rc" "configure: a failed configuration never fails the build"
assert_contains "$out" "::warning" "configure: it warns"

# ===========================================================================
# publish
# ===========================================================================

echo '{"SchemaVersion": 2, "Results": []}' > "$WORK/trivy.json"
configured() { echo "configurations: []" > "$WORK/mirror-config.yaml"; }
FQCN="net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType"

setup_stubs
configured
out="$(PATH="$WORK/bin:$PATH" sfm_publish v6 20260925-42 SECURITY.IMAGE.BACKEND trivy IMAGE "$WORK/trivy.json" \
    --source-type github-workflow --trigger-data abc123 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: succeeds"
C="yontrack --config $WORK/mirror-config.yaml"
assert_eq "$C branch setup --project yontrack-ci --branch v6
$C build setup --project yontrack-ci --branch v6 --build 20260925-42
$C graphql --fail-on-user-errors --query $SFM_SETUP_STAMP --vars-json {\"project\":\"yontrack-ci\",\"branch\":\"v6\",\"validation\":\"SECURITY.IMAGE.BACKEND\",\"dataType\":\"$FQCN\",\"dataTypeConfig\":{\"warningLevel\":\"HIGH\",\"warningValue\":1,\"failedLevel\":\"CRITICAL\",\"failedValue\":1}}
$C validate --project yontrack-ci --branch v6 --build 20260925-42 --validation SECURITY.IMAGE.BACKEND findings --format trivy --kind IMAGE --report $WORK/trivy.json --source-type github-workflow --trigger-data abc123" \
    "$(calls)" "publish: the project, branch, build and stamp on the mirror, then the findings, with the run info"
assert_not_contains "$(calls)" "ci config" "publish: never the CI config - its slots must not land on v6.dev"
assert_not_contains "$(calls)" "setup generic" \
    "publish: never \`validation-stamp setup generic\`, which splices its JSON into the query text and fails on it"

setup_stubs
configured
STUB_YONTRACK_FAIL_ON="build setup"
out="$(PATH="$WORK/bin:$PATH" sfm_publish v6 20260925-42 SECURITY.IMAGE.BACKEND trivy IMAGE "$WORK/trivy.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: a mirror that fails never fails the build"
assert_contains "$out" "::warning" "publish: it warns"
assert_contains "$out" "SECURITY.IMAGE.BACKEND" "publish: naming the stamp"
assert_not_contains "$(calls)" "validate" "publish: and stops at the failed step"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_publish v6 20260925-42 SECURITY.IMAGE.BACKEND trivy IMAGE "$WORK/missing.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: no report - a scan that did not happen - never fails the build"
assert_contains "$out" "::warning" "publish: it warns"
assert_eq "" "$(calls)" "publish: and mirrors nothing"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" sfm_publish v6 "" SECURITY.IMAGE.BACKEND trivy IMAGE "$WORK/trivy.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: no build never fails the build"
assert_contains "$out" "::warning" "publish: it warns"
assert_eq "" "$(calls)" "publish: and mirrors nothing"

setup_stubs
rm -f "$WORK/mirror-config.yaml"
out="$(PATH="$WORK/bin:$PATH" sfm_publish v6 20260925-42 SECURITY.IMAGE.BACKEND trivy IMAGE "$WORK/trivy.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "publish: a mirror never configured never fails the build"
assert_contains "$out" "::warning" "publish: it warns"
assert_eq "" "$(calls)" "publish: and calls nothing - configure has already said why"

# ===========================================================================
# main
# ===========================================================================

out="$(sfm_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "enabled|guard|configure|publish|fetch-code|code-findings|thresholds" "no command: prints the usage"

out="$(sfm_main thresholds SECURITY.CODE 2>&1)"; rc=$?
assert_eq "0" "$rc" "main: dispatches thresholds"

# --- report ----------------------------------------------------------------

report_tests
