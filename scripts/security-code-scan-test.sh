#!/usr/bin/env bash
#
# Tests for security-code-scan.sh. `gh` and `yontrack` are stubbed on the PATH, so nothing here
# calls GitHub or a Yontrack instance: the stubs answer from canned JSON and record every call
# they receive, which is what the assertions read. `jq` is the real one - it is what is being
# tested.
#
# Usage: scripts/security-code-scan-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load security-code-scan.sh as a library: defines the functions, runs nothing.
SECURITY_CODE_SCAN_LIB_ONLY=1
export SECURITY_CODE_SCAN_LIB_ONLY
# shellcheck source=security-code-scan.sh
source "$SCRIPT_DIR/security-code-scan.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/security-code-scan-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Fixtures - the shape GET /repos/{owner}/{repo}/code-scanning/alerts returns
# ===========================================================================

# The open alerts, on two pages, as `gh api --paginate` prints them: straight after one another.
# A code-quality alert carries no `security_severity_level` and is not a security finding.
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

cat "$WORK/open.json" "$WORK/dismissed.json" > "$WORK/all.json"
echo '[]' > "$WORK/empty.json"
: > "$WORK/nothing.json"
echo '[{"number": 1,' > "$WORK/broken.json"
echo '{"message": "no analysis found", "status": "404"}' > "$WORK/error-object.json"

# ===========================================================================
# findings
# ===========================================================================

report="$(scs_findings "$WORK/all.json")"; rc=$?
assert_eq "0" "$rc" "findings: succeeds"
assert_eq "codeql" "$(echo "$report" | jq -r '.scanner')" "findings: the scanner is CodeQL"
assert_eq "CODE" "$(echo "$report" | jq -r '.kind')" "findings: the kind is CODE"
assert_eq "4" "$(echo "$report" | jq '.findings | length')" \
    "findings: every page, open and dismissed, without the code-quality alert"
assert_eq "java/sql-injection|ontrack-service/src/Foo.kt|HIGH|high" \
    "$(echo "$report" | jq -r '.findings[0] | "\(.externalId)|\(.location)|\(.severity)|\(.rawSeverity)"')" \
    "findings: rule id, path without line, severity"
assert_eq "Query built from user-controlled sources" "$(echo "$report" | jq -r '.findings[0].title')" \
    "findings: the rule description is the title"
assert_eq "https://github.com/yontrack/yontrack/security/code-scanning/12" \
    "$(echo "$report" | jq -r '.findings[0].url')" "findings: links to the alert"
assert_eq "null" "$(echo "$report" | jq -c '.findings[0].acceptance')" \
    "findings: an open alert is not accepted"
assert_eq "CRITICAL" "$(echo "$report" | jq -r '.findings[1].severity')" "findings: critical"
assert_eq '{"statement":"false positive: Input is a constant","source":"GitHub code scanning alert #9"}' \
    "$(echo "$report" | jq -c '.findings[2].acceptance')" \
    "findings: a dismissal is an acceptance, with its reason and comment"
assert_eq '{"statement":"won'"'"'t fix","source":"GitHub code scanning alert #10"}' \
    "$(echo "$report" | jq -c '.findings[3].acceptance')" \
    "findings: a dismissal without comment keeps its reason"
assert_eq "" "$(echo "$report" | jq -r '.findings[] | keys[] | select(IN("externalId","location","severity","rawSeverity","title","url","acceptance") | not)')" \
    "findings: no field the neutral format would reject"

report="$(scs_findings < "$WORK/all.json")"; rc=$?
assert_eq "0" "$rc" "findings: reads the alerts from stdin when no file is given"
assert_eq "4" "$(echo "$report" | jq '.findings | length')" "findings: stdin is read like a file"

report="$(scs_findings "$WORK/empty.json")"; rc=$?
assert_eq "0" "$rc" "findings: no alert is a report"
assert_eq "0" "$(echo "$report" | jq '.findings | length')" "findings: with no finding"

out="$(scs_findings "$WORK/nothing.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "findings: an empty answer is an error, not a clean report"

out="$(scs_findings "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "findings: an answer that is not JSON is an error, not a clean report"

out="$(scs_findings "$WORK/error-object.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "findings: an API error object is an error, not a clean report"

out="$(scs_findings "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "findings: a missing file is an error, not a clean report"

# ===========================================================================
# summary
# ===========================================================================

scs_findings "$WORK/all.json" > "$WORK/report.json"
assert_eq "critical=1
high=1
medium=0
low=0
accepted=2" "$(scs_summary "$WORK/report.json")" \
    "summary: the open findings by severity, and the accepted ones apart"

out="$(scs_summary "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "summary: a missing report is an error"

# ===========================================================================
# fetch - against a stubbed gh
# ===========================================================================

# Answers the open alerts with STUB_GH_OPEN and the dismissed ones with STUB_GH_DISMISSED, by the
# `state` of the query. STUB_GH_FAIL makes it exit 1, which is what a 403 or a 404 looks like
# from outside.
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

setup_stubs() {
    rm -f "$WORK/calls" "$WORK/github_output"
    : > "$WORK/calls"
    : > "$WORK/github_output"
    STUB_CALLS="$WORK/calls"
    STUB_GH_OPEN="$WORK/open.json"
    STUB_GH_DISMISSED="$WORK/dismissed.json"
    STUB_GH_FAIL=""
    STUB_YONTRACK_ANSWERS=""
    export STUB_CALLS STUB_GH_OPEN STUB_GH_DISMISSED STUB_GH_FAIL STUB_YONTRACK_ANSWERS
}
calls() { cat "$WORK/calls"; }

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack refs/heads/main "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch: succeeds"
assert_contains "$(calls)" "--paginate" "fetch: follows every page"
assert_contains "$(calls)" "repos/yontrack/yontrack/code-scanning/alerts?ref=refs/heads/main&state=open&tool_name=CodeQL&per_page=100" \
    "fetch: the open CodeQL alerts of the ref - not the Trivy alerts sharing it - in the largest pages"
assert_contains "$(calls)" "repos/yontrack/yontrack/code-scanning/alerts?ref=refs/heads/main&state=dismissed&tool_name=CodeQL&per_page=100" \
    "fetch: and the dismissed ones, which become acceptances"
assert_eq "4" "$(jq '.findings | length' "$WORK/code.json")" "fetch: writes the report"

setup_stubs
STUB_GH_OPEN="$WORK/empty.json"
STUB_GH_DISMISSED="$WORK/empty.json"
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack refs/heads/main "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch: succeeds with no alert"
assert_eq "0" "$(jq '.findings | length' "$WORK/code.json")" "fetch: no alert is an empty report"

setup_stubs
STUB_GH_FAIL=1
rm -f "$WORK/code.json"
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack refs/heads/main "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: an API error fails"
assert_eq "false" "$([ -f "$WORK/code.json" ] && echo true || echo false)" "fetch: and writes no report"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch "" refs/heads/main "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses to run without a repository"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack "" "$WORK/code.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses to run without a ref"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack refs/heads/main "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses to run without an output file"

# ===========================================================================
# resolve-build - against a stubbed yontrack
# ===========================================================================

# Answers `build search` with the next answer of STUB_YONTRACK_ANSWERS, one per call, and
# repeats the last one when they run out: `none` (not found - nothing printed, exit 0, which is
# what --accept-not-found does), `error` (exit 1) or `found`.
cat > "$WORK/bin/yontrack" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "yontrack $*" >> "$STUB_CALLS"
n="$(grep -c '^yontrack build search' "$STUB_CALLS")"
read -r -a answers <<< "$STUB_YONTRACK_ANSWERS"
index=$(( n - 1 ))
[ "$index" -ge "${#answers[@]}" ] && index=$(( ${#answers[@]} - 1 ))
case "${answers[$index]}" in
    none) exit 0 ;;
    error) echo "Error: connection refused" >&2; exit 1 ;;
    found)
        cat <<'JSON'
{
  "Id": "4242",
  "Name": "1234",
  "DisplayName": "5.3.0-rc-1234",
  "Branch": {
    "Id": "12",
    "Name": "main",
    "DisplayName": "main",
    "Project": {"Id": "1", "Name": "yontrack"}
  }
}
JSON
        ;;
esac
STUB
chmod +x "$WORK/bin/yontrack"

resolve() {
    PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
        SECURITY_CODE_SCAN_BUILD_ATTEMPTS=3 SECURITY_CODE_SCAN_BUILD_DELAY=0 \
        scs_resolve_build "$@" 2>&1
}

setup_stubs
STUB_YONTRACK_ANSWERS="found"
out="$(resolve yontrack 0123abcd)"; rc=$?
assert_eq "0" "$rc" "resolve: succeeds when the build exists"
assert_contains "$(calls)" "--project yontrack" "resolve: searches the project it was given"
assert_contains "$(calls)" "--commit 0123abcd" "resolve: searches by commit, as the CI jobs link to their build"
assert_contains "$(calls)" "--accept-not-found" "resolve: tells not found apart from an error"
assert_contains "$(calls)" "--output json" "resolve: reads the branch and the build from the JSON output"
assert_eq "found=true
project=yontrack
branch=main
build=1234" "$(cat "$WORK/github_output")" "resolve: writes the build name - not its display name - and its branch"
assert_eq "1" "$(grep -c '^yontrack build search' "$WORK/calls")" "resolve: asks once when the build is there"

setup_stubs
STUB_YONTRACK_ANSWERS="none none found"
out="$(resolve yontrack 0123abcd)"; rc=$?
assert_eq "0" "$rc" "resolve: waits for a build CI has not registered yet"
assert_eq "3" "$(grep -c '^yontrack build search' "$WORK/calls")" "resolve: asks again until the build appears"
assert_contains "$(cat "$WORK/github_output")" "found=true" "resolve: reports the build once it appears"

setup_stubs
STUB_YONTRACK_ANSWERS="none"
out="$(resolve yontrack 0123abcd)"; rc=$?
assert_eq "0" "$rc" "resolve: a build that never appears does not fail"
assert_eq "3" "$(grep -c '^yontrack build search' "$WORK/calls")" "resolve: gives up after the attempts it was given"
assert_eq "found=false" "$(cat "$WORK/github_output")" "resolve: says the build was not found"
assert_contains "$out" "0123abcd" "resolve: names the commit it could not find"

setup_stubs
STUB_YONTRACK_ANSWERS="error found"
out="$(resolve yontrack 0123abcd)"; rc=$?
assert_eq "0" "$rc" "resolve: rides out a transient Yontrack error"
assert_contains "$(cat "$WORK/github_output")" "build=1234" "resolve: finds the build after the error"

setup_stubs
STUB_YONTRACK_ANSWERS="error"
out="$(resolve yontrack 0123abcd)"; rc=$?
assert_eq "1" "$rc" "resolve: a Yontrack that keeps failing is an error, not a missing build"
assert_eq "" "$(cat "$WORK/github_output")" "resolve: an error writes nothing"

setup_stubs
out="$(resolve "" 0123abcd)"; rc=$?
assert_eq "1" "$rc" "resolve: refuses to run without a project"

setup_stubs
out="$(resolve yontrack "")"; rc=$?
assert_eq "1" "$rc" "resolve: refuses to run without a commit"
assert_eq "" "$(calls)" "resolve: checks its arguments before calling Yontrack"

# ===========================================================================
# main
# ===========================================================================

out="$(scs_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "findings|summary|fetch|resolve-build" "no command: prints the usage"

out="$(scs_main findings "$WORK/empty.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "main: dispatches findings"
assert_contains "$out" '"findings":[]' "main: findings prints the report"

# --- report ----------------------------------------------------------------

report_tests
