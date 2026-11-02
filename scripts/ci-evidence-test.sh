#!/usr/bin/env bash
#
# Tests for scripts/ci-evidence.sh and its scripts/ci-evidence.py (#2128).
#
# Usage: scripts/ci-evidence-test.sh
#
# The CLI is a stub recording its arguments, one per line, in $CLI_LOG, and `trivy` a stub printing
# a version: everything else is the real thing, the zip and the HTML being read back by the
# assertions.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/ci-evidence-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

CLI_LOG="$WORK/cli.log"
export CLI_LOG
cat > "$WORK/cli" <<'STUB'
#!/usr/bin/env bash
if [ "${1:-}" = "graphql" ]; then
    echo '{"builds":[{"gitCommitProperty":{"value":{"commit":"cafe0123"}}}]}'
    exit 0
fi
printf '%s\n' "$@" > "$CLI_LOG"
exit "${CLI_RC:-0}"
STUB
chmod +x "$WORK/cli"
export CI_EVIDENCE_CLI="$WORK/cli"
export GITHUB_SERVER_URL=https://github.example
export GITHUB_REPOSITORY=yontrack/yontrack
export GITHUB_RUN_ID=42
export GITHUB_SHA=abc123
export YONTRACK_BUILD_NAME=main-7
RUN_URL="https://github.example/yontrack/yontrack/actions/runs/42"

EVIDENCE="$WORK/evidence"
export CI_EVIDENCE_DIR="$EVIDENCE"

# Runs ci-evidence.sh from $WORK/repo, where the fixtures are.
REPO="$WORK/repo"
mkdir -p "$REPO"
evidence() {
    rm -f "$CLI_LOG"
    (cd "$REPO" && "$SCRIPT_DIR/ci-evidence.sh" "$@") > "$WORK/out.txt" 2>&1
}
cli_args() { cat "$CLI_LOG" 2>/dev/null; }
# The value following a flag in what the CLI received, every occurrence of it.
cli_flag() { grep -A1 -x -- "$1" "$CLI_LOG" 2>/dev/null | grep -v -x -e "$1" -e '--'; }
zip_names() { python3 -c 'import sys,zipfile; print("\n".join(sorted(zipfile.ZipFile(sys.argv[1]).namelist())))' "$1"; }
zip_member() { python3 -c 'import sys,zipfile; sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read(sys.argv[2]))' "$1" "$2"; }
sha256() { python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"; }

write_suite() {
    mkdir -p "$(dirname "$REPO/$1")"
    cat > "$REPO/$1"
}

# ===============================================================================================
# JUnit: a raw zip and an HTML report, passed to the CLI with their metadata
# ===============================================================================================

write_suite mod-a/build/test-results/test/TEST-A.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="net.A" tests="3" skipped="1" failures="1" errors="0" time="1.5">
  <testcase name="passes" classname="net.A" time="0.5"/>
  <testcase name="is skipped" classname="net.A" time="0"><skipped/></testcase>
  <testcase name="fails" classname="net.A" time="1"><failure message="expected 1 but was 2" type="AssertionError">at net.A.fails(A.kt:12)</failure></testcase>
  <system-out><![CDATA[SYSTEM-OUT-MUST-STAY-IN-THE-ZIP]]></system-out>
  <system-err><![CDATA[SYSTEM-ERR-MUST-STAY-IN-THE-ZIP]]></system-err>
</testsuite>
XML
write_suite mod-b/build/test-results/test/TEST-A.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuites><testsuite name="net.B" tests="2" skipped="0" failures="0" errors="1">
  <testcase name="passes too" classname="net.B" time="0.25"/>
  <testcase name="errors" classname="net.B" time="0.25"><error message="boom">at net.B.errors(B.kt:3)</error></testcase>
</testsuite></testsuites>
XML
# Next to the pattern, and not matched by it: `*` stops at a directory.
write_suite mod-a/build/test-results/test/nested/TEST-N.xml <<'XML'
<testsuite name="net.Nested" tests="1" skipped="0" failures="0" errors="0"><testcase name="n" classname="net.Nested"/></testsuite>
XML
# In a hidden directory: the CLI walks them, so the glob has to.
write_suite .hidden/build/test-results/test/TEST-H.xml <<'XML'
<testsuite name="net.Hidden" tests="1" skipped="0" failures="0" errors="0"><testcase name="h" classname="net.Hidden"/></testsuite>
XML

PATTERN='**/build/test-results/test/*.xml'
evidence junit BUILD "$PATTERN" --tool gradle --tool-version 9.7.1 -- \
    validate --validation BUILD junit --pattern "$PATTERN" --fail-when-no-results --run-time 12
assert_eq 0 "$?" "junit: the step succeeds"
ZIP="$EVIDENCE/BUILD/BUILD-junit.zip"
HTML="$EVIDENCE/BUILD/BUILD-report.html"

assert_eq "$ZIP
$HTML" "$(cli_flag --evidence)" "junit: both evidences are passed, under their stable names"
assert_contains "$(cli_args)" "--fail-when-no-results" "junit: the validation is sent as asked"
assert_eq gradle "$(cli_flag --evidence-tool)" "junit: the tool is passed"
assert_eq 9.7.1 "$(cli_flag --evidence-tool-version)" "junit: the version is passed"
assert_eq "$RUN_URL" "$(cli_flag --evidence-source-url)" "junit: the source is the workflow run"
assert_not_contains "$(cli_args)" "--evidence-optional" "junit: evidence is never optional"

assert_eq ".hidden/build/test-results/test/TEST-H.xml
mod-a/build/test-results/test/TEST-A.xml
mod-b/build/test-results/test/TEST-A.xml" "$(zip_names "$ZIP")" \
    "zip: exactly what the pattern matches, with the same semantics as the CLI's, two files of one name kept apart"
assert_eq "$(cat "$REPO/mod-a/build/test-results/test/TEST-A.xml")" \
    "$(zip_member "$ZIP" mod-a/build/test-results/test/TEST-A.xml)" "zip: the XML is unmodified, system-out included"

report="$(cat "$HTML")"
assert_contains "$report" "<title>BUILD" "report: names the stamp"
assert_contains "$report" "main-7" "report: names the build"
assert_contains "$report" "abc123" "report: names the commit"
assert_contains "$report" "$RUN_URL" "report: links the workflow run"
assert_contains "$report" "$(sha256 "$ZIP")" "report: carries the SHA-256 of its raw input"
# 3 suites over 6 tests: 1 skipped, 1 failure, 1 error. Read from the suite headers, as the stamp is.
assert_contains "$report" 'data-total="passed">3<' "report: the passed count is the stamp's"
assert_contains "$report" 'data-total="skipped">1<' "report: the skipped count is the stamp's"
assert_contains "$report" 'data-total="failed">2<' "report: failures and errors count as failed, as on the stamp"
assert_contains "$report" "net.A" "report: lists the suites"
assert_contains "$report" "is skipped" "report: lists each test"
assert_contains "$report" "expected 1 but was 2" "report: carries the failure message"
assert_contains "$report" "at net.A.fails(A.kt:12)" "report: carries the stack trace"
assert_contains "$report" "at net.B.errors(B.kt:3)" "report: carries an error's stack trace"
assert_not_contains "$report" "MUST-STAY-IN-THE-ZIP" "report: no system-out or system-err"

# ===============================================================================================
# Untrusted values
# ===============================================================================================

write_suite hostile/TEST-X.xml <<'XML'
<testsuite name="&lt;script&gt;alert('suite')&lt;/script&gt;" tests="1" skipped="0" failures="1" errors="0">
  <testcase name="&lt;img src=x onerror=alert(1)&gt;" classname="&quot;&gt;&lt;b&gt;class"><failure message="&lt;i&gt;message&lt;/i&gt;">&lt;script&gt;alert('trace')&lt;/script&gt;</failure></testcase>
</testsuite>
XML
GITHUB_REPOSITORY='y/"><script>alert(1)</script>' evidence junit HOSTILE 'hostile/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation HOSTILE --run-time 1
report="$(cat "$EVIDENCE/HOSTILE/HOSTILE-report.html")"
assert_not_contains "$report" "<script" "escaping: no script survives, from a test or from the run URL"
assert_not_contains "$report" "<img" "escaping: markup in a test name stays text"
assert_not_contains "$report" "<i>message" "escaping: markup in a failure message stays text"
assert_not_contains "$report" "<b>class" "escaping: markup in a class name stays text"
assert_contains "$report" "&lt;img src=x onerror=alert(1)&gt;" "escaping: the test name is still readable"
assert_not_contains "$report" "<link" "self-contained: no stylesheet to load"
assert_not_contains "$report" "src=\"" "self-contained: nothing to load"
assert_not_contains "$report" "url(" "self-contained: nothing to load from the style"

# A run URL which is not https stays text: the one link of the page cannot run anything.
GITHUB_SERVER_URL='javascript:alert(1)//' evidence junit HOSTILE 'hostile/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation HOSTILE --run-time 1
assert_not_contains "$(cat "$EVIDENCE/HOSTILE/HOSTILE-report.html")" 'href="javascript' \
    "escaping: a run URL which is not https is not a link"

# ===============================================================================================
# Only what exists is passed, and a gap turns the step red
# ===============================================================================================

# No report at all: the bare FAILED of today, no evidence, and the step is not what is red.
evidence junit NONE 'nothing-here/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation NONE --status FAILED --run-time 1
assert_eq 0 "$?" "no report: the step does not add a failure of its own"
assert_contains "$(cli_args)" "FAILED" "no report: the validation is still sent"
assert_not_contains "$(cli_args)" "--evidence" "no report: no evidence flag at all, not even the metadata"

# A stamp that says it passed with no report behind it is the audit gap: a wrong glob would otherwise
# never show. Only a validation that says FAILED may go without.
evidence junit PASSED 'nothing-here/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation PASSED --status PASSED --run-time 1
assert_eq 1 "$?" "PASSED with no report: the step is red"
assert_contains "$(cli_args)" "PASSED" "PASSED with no report: the stamp is still sent"
assert_contains "$(cat "$WORK/out.txt")" "::error" "PASSED with no report: the gap is named in the log"
# With --fail-when-no-results and no file, the CLI itself records FAILED.
evidence junit EMPTYRUN 'nothing-here/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation EMPTYRUN junit --pattern 'nothing-here/*.xml' --fail-when-no-results
assert_eq 0 "$?" "no results: the CLI records FAILED, so the step adds nothing"
assert_not_contains "$(cli_args)" "--evidence" "no results: and no evidence"

# A report that cannot be read: the raw file goes, the stamp is sent, and the step fails.
write_suite broken/TEST-B.xml <<'XML'
<testsuite name="truncated" tests="1"><testcase
XML
evidence junit BROKEN 'broken/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation BROKEN --status FAILED --run-time 1
assert_eq 1 "$?" "render fails: the step is red"
assert_contains "$(cli_args)" "BROKEN" "render fails: the validation is still sent"
assert_eq "$EVIDENCE/BROKEN/BROKEN-junit.zip" "$(cli_flag --evidence)" "render fails: only what exists is passed"
assert_eq false "$([ -e "$EVIDENCE/BROKEN/BROKEN-report.html" ] && echo true || echo false)" "render fails: no partial HTML is left behind"
assert_contains "$(cat "$WORK/out.txt")" "::error" "render fails: the gap is named in the log"

# No Python at all: nothing can be produced, the stamp must still go.
mkdir -p "$WORK/nopython"
for tool in bash sed jq dirname rm mkdir cat cp basename; do ln -sf "$(command -v "$tool")" "$WORK/nopython/$tool"; done
rm -f "$CLI_LOG"
(cd "$REPO" && PATH="$WORK/nopython" "$(command -v bash)" "$SCRIPT_DIR/ci-evidence.sh" junit BROKEN 'mod-a/build/test-results/test/*.xml' \
    --tool gradle --tool-version 1 -- validate --validation NOPYTHON --run-time 1) > "$WORK/out.txt" 2>&1
assert_eq 1 "$?" "no python: the step is red"
assert_contains "$(cli_args)" "NOPYTHON" "no python: the validation is still sent"
assert_not_contains "$(cli_args)" "--evidence" "no python: nothing to attach, so nothing is passed"

# A version which cannot be read is a gap too: the metadata is part of the evidence.
evidence junit NOVERSION 'mod-a/build/test-results/test/*.xml' --tool unheard-of -- \
    validate --validation NOVERSION --run-time 1
assert_eq 1 "$?" "no version: the step is red"
assert_contains "$(cli_args)" "--evidence" "no version: the evidence still goes"
assert_not_contains "$(cli_args)" "--evidence-tool-version" "no version: no empty flag"

# The CLI refusing an upload, or failing altogether, is what the step says.
CLI_RC=3 evidence junit REFUSED 'mod-a/build/test-results/test/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation REFUSED --run-time 1
assert_eq 3 "$?" "refused: the CLI's failure is the step's"

# A second report on one stamp in one job starts afresh: nothing stale is attached.
touch "$EVIDENCE/REFUSED/stale.txt"
evidence junit REFUSED 'mod-a/build/test-results/test/*.xml' --tool gradle --tool-version 1 -- \
    validate --validation REFUSED --run-time 1
assert_eq false "$([ -e "$EVIDENCE/REFUSED/stale.txt" ] && echo true || echo false)" "the directory of a stamp is emptied first"

# ===============================================================================================
# Metadata and header, from the pins and from the instance
# ===============================================================================================

evidence junit PINNED 'mod-a/build/test-results/test/*.xml' --tool gradle -- validate --validation PINNED --run-time 1
assert_eq "$(sed -n 's/^distributionUrl=.*gradle-\(.*\)-bin\.zip$/\1/p' "$SCRIPT_DIR/../gradle/wrapper/gradle-wrapper.properties")" \
    "$(cli_flag --evidence-tool-version)" "gradle: the version is the wrapper's"
evidence junit PINNED 'mod-a/build/test-results/test/*.xml' --tool jest -- validate --validation PINNED --run-time 1
assert_eq "$(jq -r '.packages["node_modules/jest"].version' "$SCRIPT_DIR/../ontrack-web-core/package-lock.json")" \
    "$(cli_flag --evidence-tool-version)" "jest: the version is the lock file's"
evidence junit PINNED 'mod-a/build/test-results/test/*.xml' --tool playwright -- validate --validation PINNED --run-time 1
assert_eq "$(jq -r '.packages["node_modules/@playwright/test"].version' "$SCRIPT_DIR/../ontrack-web-tests/package-lock.json")" \
    "$(cli_flag --evidence-tool-version)" "playwright: the version is the lock file's"

# A build named on the command line is not necessarily the commit of this run: a rescan runs from
# `main` on a released build. Its commit is asked of the instance.
evidence junit SPELLED 'mod-a/build/test-results/test/*.xml' --tool gradle --tool-version 1 -- \
    validate --project yontrack --branch release-5.5 --build release-5.5-3 --validation SPELLED --run-time 1
report="$(cat "$EVIDENCE/SPELLED/SPELLED-report.html")"
assert_contains "$report" "release-5.5-3" "header: the build on the command line wins"
assert_contains "$report" "cafe0123" "header: a build spelled out has the commit the instance gives it"
assert_not_contains "$report" "abc123" "header: not the commit of the run"

CI_EVIDENCE_COMMIT=f00d evidence junit SPELLED 'mod-a/build/test-results/test/*.xml' --tool gradle --tool-version 1 -- \
    validate --project yontrack --branch main --build main-3 --validation SPELLED --run-time 1
assert_contains "$(cat "$EVIDENCE/SPELLED/SPELLED-report.html")" "f00d" "header: an explicit commit wins"

# ===============================================================================================
# Trivy: the report as it was sent, each finding as the stamp counts it
# ===============================================================================================

cat > "$WORK/trivy" <<'STUB'
#!/usr/bin/env bash
[ "${TRIVY_BROKEN:-}" = "true" ] && exit 1
[ "$1 $2 $3" = "version --format json" ] && echo '{"Version":"0.76.1"}'
STUB
chmod +x "$WORK/trivy"
export PATH="$WORK:$PATH"

cat > "$REPO/trivy.json" <<'JSON'
{
  "SchemaVersion": 2,
  "Results": [
    {
      "Target": "app.jar",
      "Vulnerabilities": [
        {"VulnerabilityID": "CVE-1", "PkgName": "lib", "PkgIdentifier": {"PURL": "pkg:maven/org/lib@1.0?type=jar"},
         "InstalledVersion": "1.0", "FixedVersion": "1.1", "Severity": "MEDIUM", "Title": "<b>Bold</b> title"},
        {"VulnerabilityID": "CVE-1", "PkgName": "lib", "PkgIdentifier": {"PURL": "pkg:maven/org/lib@2.0"},
         "InstalledVersion": "2.0", "FixedVersion": "2.1", "Severity": "HIGH", "Title": "The same one, worse"},
        {"VulnerabilityID": "CVE-2", "PkgName": "other", "Severity": "LOW"}
      ],
      "Secrets": [{"Title": "Key", "Match": "SECRET-MATCHED-TEXT"}],
      "Misconfigurations": [{"Title": "MISCONFIGURATION-TITLE"}],
      "ExperimentalModifiedFindings": [
        {"Type": "vulnerability", "Status": "ignored", "Statement": "Not reachable", "Source": ".trivyignore.yaml",
         "Finding": {"VulnerabilityID": "CVE-3", "PkgName": "third", "Severity": "CRITICAL", "Title": "Accepted one"}},
        {"Type": "vulnerability", "Status": "fixed", "Finding": {"VulnerabilityID": "CVE-4", "PkgName": "fourth", "Severity": "CRITICAL"}},
        {"Type": "secret", "Status": "ignored", "Finding": {"Match": "SECRET-MODIFIED-TEXT"}}
      ]
    }
  ]
}
JSON
evidence trivy SECURITY.IMAGE.BACKEND "$REPO/trivy.json" -- \
    validate --validation SECURITY.IMAGE.BACKEND findings --format trivy --kind IMAGE --report "$REPO/trivy.json"
assert_eq 0 "$?" "trivy: the step succeeds"
RAW="$EVIDENCE/SECURITY.IMAGE.BACKEND/SECURITY.IMAGE.BACKEND-trivy.json"
HTML="$EVIDENCE/SECURITY.IMAGE.BACKEND/SECURITY.IMAGE.BACKEND-report.html"
assert_eq "$RAW
$HTML" "$(cli_flag --evidence)" "trivy: both evidences are passed, under their stable names"
assert_eq "$(sha256 "$REPO/trivy.json")" "$(sha256 "$RAW")" "trivy: the raw file is the exact one that was sent"
assert_eq trivy "$(cli_flag --evidence-tool)" "trivy: the tool is passed"
assert_eq 0.76.1 "$(cli_flag --evidence-tool-version)" "trivy: the version is the tool's own"

report="$(cat "$HTML")"
assert_contains "$report" "$(sha256 "$RAW")" "trivy: the header carries the SHA-256 of the raw file"
# CVE-1 twice on one package is one finding, at the worse of its two severities - HIGH - as the
# stamp merges them. CVE-3 is accepted, whatever its severity, and CVE-4 is not reported at all.
assert_contains "$report" 'data-total="critical">0<' "trivy: an accepted finding is not counted in its severity"
assert_contains "$report" 'data-total="high">1<' "trivy: one CVE on two versions of a package is one finding"
assert_contains "$report" 'data-total="medium">0<' "trivy: the merged finding keeps the worse severity"
assert_contains "$report" 'data-total="low">1<' "trivy: a finding without a purl counts"
assert_contains "$report" 'data-total="accepted">1<' "trivy: the accepted one is counted apart"
assert_contains "$report" "Not reachable" "trivy: an accepted finding says why"
assert_contains "$report" "CVE-3" "trivy: an accepted finding is listed"
assert_contains "$report" "pkg:maven/org/lib" "trivy: the location is the package"
assert_not_contains "$report" "CVE-4" "trivy: a finding modified to anything else is not read, as on the stamp"
assert_not_contains "$report" "SECRET-" "trivy: nothing of a secret result is read"
assert_not_contains "$report" "MISCONFIGURATION-" "trivy: nor of a misconfiguration"

evidence trivy SECURITY.IMAGE.UI "$REPO/not-here.json" -- validate --validation SECURITY.IMAGE.UI --status FAILED
assert_eq 0 "$?" "trivy, no report: the step does not add a failure of its own"
assert_not_contains "$(cli_args)" "--evidence" "trivy, no report: a bare FAILED"

evidence trivy SECURITY.IMAGE.UI "" -- validate --validation SECURITY.IMAGE.UI --status FAILED
assert_eq 0 "$?" "trivy, an output that was never set: a bare FAILED too"
assert_not_contains "$(cli_args)" "--evidence" "trivy, an output that was never set: no evidence"

echo 'not json' > "$REPO/not-json.json"
evidence trivy SECURITY.IMAGE.UI "$REPO/not-json.json" -- validate --validation SECURITY.IMAGE.UI --status FAILED
assert_eq 1 "$?" "trivy, unreadable: the step is red"
assert_eq "$EVIDENCE/SECURITY.IMAGE.UI/SECURITY.IMAGE.UI-trivy.json" "$(cli_flag --evidence)" \
    "trivy, unreadable: the raw file still goes"

TRIVY_BROKEN=true evidence trivy SECURITY.IMAGE.UI "$REPO/trivy.json" -- validate --validation SECURITY.IMAGE.UI --status FAILED
assert_eq 1 "$?" "trivy, no version: the step is red"

: > "$REPO/empty.json"
evidence trivy SECURITY.IMAGE.UI "$REPO/empty.json" -- validate --validation SECURITY.IMAGE.UI --status FAILED
assert_eq 1 "$?" "trivy, empty report: the step is red"
assert_not_contains "$(cli_args)" "--evidence" "trivy, empty report: the CLI would refuse it and record no run, so it is not passed"
assert_contains "$(cli_args)" "SECURITY.IMAGE.UI" "trivy, empty report: the stamp is still sent"

# ===============================================================================================
# Findings
# ===============================================================================================

cat > "$REPO/findings.json" <<'JSON'
{"scanner": "codeql", "kind": "CODE", "findings": [
  {"externalId": "java/sql", "location": "src/A.kt", "severity": "HIGH", "title": "<script>alert('title')</script>", "url": "https://github.example/alert/1"},
  {"externalId": "java/old", "location": "src/B.kt", "severity": "MEDIUM", "title": "Lapsed", "acceptance": {"statement": "Lapsed", "expiresAt": "2001-01-01", "source": "somewhere"}},
  {"externalId": "java/ok", "location": "src/C.kt", "severity": "CRITICAL", "title": "Tolerated", "acceptance": {"statement": "<i>False positive</i>", "source": "alert #3"}}
]}
JSON
evidence findings SECURITY.CODE "$REPO/findings.json" --tool codeql --tool-version 2.20.0 -- \
    validate --validation SECURITY.CODE findings --format findings --kind CODE --report "$REPO/findings.json"
assert_eq 0 "$?" "findings: the step succeeds"
RAW="$EVIDENCE/SECURITY.CODE/SECURITY.CODE-findings.json"
HTML="$EVIDENCE/SECURITY.CODE/SECURITY.CODE-report.html"
assert_eq "$RAW
$HTML" "$(cli_flag --evidence)" "findings: both evidences are passed, under their stable names"
assert_eq codeql "$(cli_flag --evidence-tool)" "findings: the tool is passed"
assert_eq 2.20.0 "$(cli_flag --evidence-tool-version)" "findings: the pin is passed"
report="$(cat "$HTML")"
assert_contains "$report" 'data-total="high">1<' "findings: counted by severity"
assert_contains "$report" 'data-total="medium">1<' "findings: an expired acceptance counts again, as on the stamp"
assert_contains "$report" 'data-total="accepted">1<' "findings: the accepted one apart"
assert_not_contains "$report" "<script>alert" "findings: markup in a finding title stays text"
assert_not_contains "$report" "<i>False" "findings: markup in a statement stays text"
assert_contains "$report" "&lt;script&gt;alert" "findings: the title is still readable"
assert_contains "$report" "src/A.kt" "findings: the location is listed"
assert_not_contains "$report" 'href="https://github.example/alert' "findings: only the workflow run is a link"

# ===============================================================================================
# Search performance: a table of its figures
# ===============================================================================================

cat > "$REPO/search-perf.json" <<'JSON'
{"palette_p95": 41, "rebuild_seconds": 12.5, "<b>hostile</b>": 1, "note": "not a figure",
 "details": {"failures": ["p95 <over> its ceiling"], "over_budget": ["palette_p95"]}}
JSON
evidence search-perf SEARCH.PERFORMANCE "$REPO/search-perf.json" --tool searchPerfTest --tool-version 6.0.0 -- \
    validate --validation SEARCH.PERFORMANCE --status FAILED
assert_eq 0 "$?" "search-perf: the step succeeds"
RAW="$EVIDENCE/SEARCH.PERFORMANCE/SEARCH.PERFORMANCE-report.json"
HTML="$EVIDENCE/SEARCH.PERFORMANCE/SEARCH.PERFORMANCE-report.html"
assert_eq "$RAW
$HTML" "$(cli_flag --evidence)" "search-perf: both evidences are passed, under their stable names"
assert_eq "$(sha256 "$REPO/search-perf.json")" "$(sha256 "$RAW")" "search-perf: the raw file is the test's own"
report="$(cat "$HTML")"
assert_contains "$report" "<td>palette_p95</td><td>41</td>" "search-perf: a figure is a row"
assert_contains "$report" "<td>rebuild_seconds</td><td>12.5</td>" "search-perf: a decimal one too"
assert_not_contains "$report" "not a figure" "search-perf: only the figures"
assert_contains "$report" "p95 &lt;over&gt; its ceiling" "search-perf: the failures, escaped"
assert_not_contains "$report" "<b>hostile" "search-perf: a hostile key stays text"

report_tests
