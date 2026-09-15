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

# One page: every security severity, plus code-quality alerts, which carry a `severity` but a
# null or absent `security_severity_level` and must not be counted.
cat > "$WORK/page1.json" <<'JSON'
[
  {"number": 1, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "java/sql-injection", "severity": "error", "security_severity_level": "critical"}},
  {"number": 2, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "js/xss", "severity": "error", "security_severity_level": "high"}},
  {"number": 3, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "js/xss", "severity": "error", "security_severity_level": "high"}},
  {"number": 4, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "actions/untrusted-checkout", "severity": "warning", "security_severity_level": "medium"}},
  {"number": 5, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "java/unused-variable", "severity": "note", "security_severity_level": null}},
  {"number": 6, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "js/useless-assignment", "severity": "warning"}}
]
JSON

# A second page, as `gh api --paginate` prints it: straight after the first one.
cat > "$WORK/page2.json" <<'JSON'
[
  {"number": 7, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "java/path-injection", "severity": "error", "security_severity_level": "critical"}},
  {"number": 8, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "js/weak-crypto", "severity": "warning", "security_severity_level": "low"}},
  {"number": 9, "state": "open", "tool": {"name": "CodeQL"},
   "rule": {"id": "js/weak-crypto", "severity": "warning", "security_severity_level": "low"}}
]
JSON

cat "$WORK/page1.json" "$WORK/page2.json" > "$WORK/paginated.json"
echo '[]' > "$WORK/empty.json"
: > "$WORK/nothing.json"
echo '[{"number": 1,' > "$WORK/broken.json"
echo '{"message": "no analysis found", "status": "404"}' > "$WORK/error-object.json"

# ===========================================================================
# count
# ===========================================================================

out="$(scs_count "$WORK/page1.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds on one page"
assert_eq "critical=1
high=2
medium=1
low=0" "$out" "count: counts by security severity, and ignores alerts without one"

out="$(scs_count "$WORK/paginated.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds on several pages"
assert_eq "critical=2
high=2
medium=1
low=2" "$out" "count: adds up every page"

out="$(scs_count < "$WORK/paginated.json")"; rc=$?
assert_eq "0" "$rc" "count: reads the alerts from stdin when no file is given"
assert_contains "$out" "low=2" "count: stdin is counted like a file"

out="$(scs_count "$WORK/empty.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds when there is no open alert"
assert_eq "critical=0
high=0
medium=0
low=0" "$out" "count: no alert counts as zero"

out="$(scs_count "$WORK/nothing.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: an empty answer is an error, not zero alerts"

out="$(scs_count "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: an answer that is not JSON is an error, not zero alerts"

out="$(scs_count "$WORK/error-object.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: an API error object is an error, not zero alerts"

out="$(scs_count "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: a missing file is an error, not zero alerts"

# ===========================================================================
# fetch - against a stubbed gh
# ===========================================================================

# Prints the canned pages named by STUB_GH_PAGES back to back, as `gh api --paginate` does.
# STUB_GH_FAIL makes it exit 1, which is what a 403 or a 404 looks like from outside.
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "gh $*" >> "$STUB_CALLS"
if [ -n "${STUB_GH_FAIL:-}" ]; then
    echo "gh: Not Found (HTTP 404)" >&2
    exit 1
fi
for page in $STUB_GH_PAGES; do
    cat "$page"
done
STUB
chmod +x "$WORK/bin/gh"

setup_stubs() {
    rm -f "$WORK/calls" "$WORK/github_output"
    : > "$WORK/calls"
    : > "$WORK/github_output"
    STUB_CALLS="$WORK/calls"
    STUB_GH_PAGES="$WORK/page1.json $WORK/page2.json"
    STUB_GH_FAIL=""
    STUB_YONTRACK_ANSWERS=""
    export STUB_CALLS STUB_GH_PAGES STUB_GH_FAIL STUB_YONTRACK_ANSWERS
}
calls() { cat "$WORK/calls"; }

setup_stubs
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    scs_fetch yontrack/yontrack refs/heads/main 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch: succeeds"
assert_contains "$(calls)" "--paginate" "fetch: follows every page"
assert_contains "$(calls)" "repos/yontrack/yontrack/code-scanning/alerts?" "fetch: asks the code-scanning alerts API"
assert_contains "$(calls)" "ref=refs/heads/main" "fetch: asks for the ref it was given"
assert_contains "$(calls)" "state=open" "fetch: counts only the open alerts, so a dismissal lowers the count"
assert_contains "$(calls)" "tool_name=CodeQL" "fetch: counts only CodeQL, not the Trivy alerts sharing the ref"
assert_contains "$(calls)" "per_page=100" "fetch: asks for the largest pages"
assert_eq "critical=2
high=2
medium=1
low=2" "$(cat "$WORK/github_output")" "fetch: writes the counts to GITHUB_OUTPUT"
assert_contains "$out" "critical=2" "fetch: prints the counts in the log"

setup_stubs
STUB_GH_PAGES="$WORK/empty.json"
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    scs_fetch yontrack/yontrack refs/heads/main 2>&1)"; rc=$?
assert_eq "0" "$rc" "fetch: succeeds with no alert"
assert_contains "$(cat "$WORK/github_output")" "critical=0" "fetch: no alert writes zeros"

setup_stubs
STUB_GH_FAIL=1
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    scs_fetch yontrack/yontrack refs/heads/main 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: an API error fails"
assert_eq "" "$(cat "$WORK/github_output")" "fetch: an API error writes no counts"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch "" refs/heads/main 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses to run without a repository"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" scs_fetch yontrack/yontrack "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "fetch: refuses to run without a ref"

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
assert_contains "$out" "count|fetch|resolve-build" "no command: prints the usage"

out="$(scs_main count "$WORK/empty.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "main: dispatches count"
assert_contains "$out" "low=0" "main: count prints the counts"

# --- report ----------------------------------------------------------------

report_tests
