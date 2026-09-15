#!/usr/bin/env bash
#
# Tests for security-image-scan.sh. `trivy` is stubbed on the PATH, so nothing here pulls an
# image or downloads a vulnerability database: the stub answers from canned Trivy JSON and
# records every call it receives, which is what the assertions read. `jq` and `yq` are the real
# ones - they are what is being tested.
#
# Usage: scripts/security-image-scan-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load security-image-scan.sh as a library: defines the functions, runs nothing.
SECURITY_IMAGE_SCAN_LIB_ONLY=1
export SECURITY_IMAGE_SCAN_LIB_ONLY
# shellcheck source=security-image-scan.sh
source "$SCRIPT_DIR/security-image-scan.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/security-image-scan-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Fixtures - the shape `trivy image --format json` produces
# ===========================================================================

# Two results (an OS layer and a language layer), every severity including UNKNOWN, and a
# result whose `Vulnerabilities` is null - which is what Trivy writes for a scanned target with
# nothing found, rather than leaving the key out.
cat > "$WORK/mixed.json" <<'JSON'
{
  "SchemaVersion": 2,
  "ArtifactName": "ghcr.io/yontrack/yontrack/ontrack-ui:run-1",
  "Results": [
    {
      "Target": "ontrack-ui (alpine 3.21.3)",
      "Class": "os-pkgs",
      "Vulnerabilities": [
        {"VulnerabilityID": "CVE-1", "Severity": "CRITICAL"},
        {"VulnerabilityID": "CVE-2", "Severity": "HIGH"},
        {"VulnerabilityID": "CVE-3", "Severity": "HIGH"},
        {"VulnerabilityID": "CVE-4", "Severity": "UNKNOWN"}
      ]
    },
    {
      "Target": "Node.js",
      "Class": "lang-pkgs",
      "Vulnerabilities": [
        {"VulnerabilityID": "CVE-5", "Severity": "MEDIUM"},
        {"VulnerabilityID": "CVE-6", "Severity": "MEDIUM"},
        {"VulnerabilityID": "CVE-7", "Severity": "MEDIUM"},
        {"VulnerabilityID": "CVE-8", "Severity": "LOW"},
        {"VulnerabilityID": "CVE-9", "Severity": "CRITICAL"}
      ]
    },
    {
      "Target": "app/package-lock.json",
      "Class": "lang-pkgs",
      "Vulnerabilities": null
    },
    {
      "Target": "app/pom.properties",
      "Class": "lang-pkgs"
    }
  ]
}
JSON

cat > "$WORK/clean.json" <<'JSON'
{
  "SchemaVersion": 2,
  "Results": [
    {"Target": "ontrack (ubuntu 24.04)", "Class": "os-pkgs", "Vulnerabilities": null}
  ]
}
JSON

# An image Trivy found no packages in at all: no `Results` key.
cat > "$WORK/no-results.json" <<'JSON'
{"SchemaVersion": 2, "ArtifactName": "scratch"}
JSON

# `Results: null`, which Trivy has written for an empty target as well.
cat > "$WORK/null-results.json" <<'JSON'
{"SchemaVersion": 2, "Results": null}
JSON

echo '{"Results": [' > "$WORK/broken.json"

# ===========================================================================
# count
# ===========================================================================

out="$(sis_count "$WORK/mixed.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds on a report with findings"
assert_eq "critical=2
high=2
medium=3
low=1" "$out" "count: counts every severity across results, and ignores UNKNOWN"

out="$(sis_count "$WORK/clean.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds when Vulnerabilities is null"
assert_eq "critical=0
high=0
medium=0
low=0" "$out" "count: a null Vulnerabilities list counts as zero"

out="$(sis_count "$WORK/no-results.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds when there are no Results at all"
assert_eq "critical=0
high=0
medium=0
low=0" "$out" "count: a missing Results counts as zero"

out="$(sis_count "$WORK/null-results.json")"; rc=$?
assert_eq "0" "$rc" "count: succeeds when Results is null"
assert_contains "$out" "critical=0" "count: a null Results counts as zero"

out="$(sis_count "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: a report that is not JSON is an error, not zero findings"

out="$(sis_count "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count: a missing report is an error, not zero findings"

# ===========================================================================
# check-ignorefile
# ===========================================================================

cat > "$WORK/ignore-empty.yaml" <<'YAML'
# Nothing accepted yet.
vulnerabilities: []
YAML
out="$(sis_check_ignorefile "$WORK/ignore-empty.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "ignorefile: an empty list is valid"

printf '# only comments\n' > "$WORK/ignore-comments.yaml"
out="$(sis_check_ignorefile "$WORK/ignore-comments.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "ignorefile: a file with only comments is valid"

cat > "$WORK/ignore-ok.yaml" <<'YAML'
vulnerabilities:
  - id: CVE-2025-0001
    statement: Not reachable - the parser is never fed user input
    expired_at: 2026-12-31
YAML
out="$(sis_check_ignorefile "$WORK/ignore-ok.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "ignorefile: an entry with a statement and an expiry is valid"

cat > "$WORK/ignore-no-statement.yaml" <<'YAML'
vulnerabilities:
  - id: CVE-2025-0001
    statement: Fine
    expired_at: 2026-12-31
  - id: CVE-2025-0002
    expired_at: 2026-12-31
YAML
out="$(sis_check_ignorefile "$WORK/ignore-no-statement.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "ignorefile: an entry without a statement is refused"
assert_contains "$out" "CVE-2025-0002" "ignorefile: names the entry missing its statement"
assert_not_contains "$out" "CVE-2025-0001" "ignorefile: does not blame the valid entry"

cat > "$WORK/ignore-no-expiry.yaml" <<'YAML'
vulnerabilities:
  - id: CVE-2025-0003
    statement: Accepted forever, apparently
YAML
out="$(sis_check_ignorefile "$WORK/ignore-no-expiry.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "ignorefile: an entry without an expiry is refused"
assert_contains "$out" "CVE-2025-0003" "ignorefile: names the entry missing its expiry"

cat > "$WORK/ignore-blank-statement.yaml" <<'YAML'
vulnerabilities:
  - id: CVE-2025-0004
    statement: ""
    expired_at: 2026-12-31
YAML
out="$(sis_check_ignorefile "$WORK/ignore-blank-statement.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "ignorefile: a blank statement is no statement"

out="$(sis_check_ignorefile "$WORK/missing.yaml" 2>&1)"; rc=$?
assert_eq "1" "$rc" "ignorefile: a missing file is an error"

# The one at the repository root is the one CI uses: it has to pass its own check.
out="$(sis_check_ignorefile "$SCRIPT_DIR/../.trivyignore.yaml" 2>&1)"; rc=$?
assert_eq "0" "$rc" "ignorefile: the repository's own .trivyignore.yaml is valid"

# ===========================================================================
# scan - against a stubbed trivy
# ===========================================================================

# `image` copies the canned report named by STUB_REPORT to --output; `convert` writes a SARIF
# placeholder. STUB_FAIL makes the named subcommand exit 1, which is what a scanner error
# (image not found, DB download failure) looks like from outside.
cat > "$WORK/bin/trivy" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "trivy $*" >> "$STUB_CALLS"
sub="$1"
if [ "${STUB_FAIL:-}" = "$sub" ]; then
    echo "FATAL stubbed $sub failure" >&2
    exit 1
fi
output=""
prev=""
for arg in "$@"; do
    [ "$prev" = "--output" ] && output="$arg"
    prev="$arg"
done
case "$sub" in
    image) cp "$STUB_REPORT" "$output" ;;
    convert) echo '{"version": "2.1.0", "runs": []}' > "$output" ;;
esac
exit 0
STUB
chmod +x "$WORK/bin/trivy"

setup_scan() {
    rm -rf "$WORK/out" "$WORK/calls" "$WORK/github_output"
    : > "$WORK/calls"
    : > "$WORK/github_output"
    STUB_CALLS="$WORK/calls"
    STUB_REPORT="$WORK/mixed.json"
    STUB_FAIL=""
    export STUB_CALLS STUB_REPORT STUB_FAIL
}
calls() { cat "$WORK/calls"; }

setup_scan
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    SECURITY_IMAGE_SCAN_IGNOREFILE="$WORK/ignore-empty.yaml" \
    sis_scan ghcr.io/yontrack/yontrack/ontrack-ui:run-1 "$WORK/out" 2>&1)"; rc=$?
assert_eq "0" "$rc" "scan: findings do not fail the scan"
assert_contains "$(calls)" "trivy image" "scan: scans the image"
assert_contains "$(calls)" "ghcr.io/yontrack/yontrack/ontrack-ui:run-1" "scan: scans the image it was given"
assert_contains "$(calls)" "--ignore-unfixed" "scan: counts only the vulnerabilities with a fix"
assert_contains "$(calls)" "--format json" "scan: produces the JSON report the counts come from"
assert_contains "$(calls)" "--scanners vuln" "scan: looks for vulnerabilities only"
assert_contains "$(calls)" "--exit-code 0" "scan: findings never set the exit code"
assert_contains "$(calls)" "--ignorefile $WORK/ignore-empty.yaml" "scan: applies the ignore file"
assert_contains "$(calls)" "trivy convert --format sarif" "scan: converts the same report to SARIF"
assert_eq "1" "$(grep -c '^trivy image' "$WORK/calls")" "scan: scans the image once, not once per format"
present=no; [ -s "$WORK/out/trivy.json" ] && present=yes
assert_eq "yes" "$present" "scan: leaves the JSON report in the output directory"
present=no; [ -s "$WORK/out/trivy.sarif" ] && present=yes
assert_eq "yes" "$present" "scan: leaves the SARIF report in the output directory"
assert_eq "critical=2
high=2
medium=3
low=1" "$(cat "$WORK/github_output")" "scan: writes the counts to GITHUB_OUTPUT"
assert_contains "$out" "critical=2" "scan: prints the counts in the log"

setup_scan
STUB_FAIL=image
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    SECURITY_IMAGE_SCAN_IGNOREFILE="$WORK/ignore-empty.yaml" \
    sis_scan ghcr.io/yontrack/yontrack/ontrack:missing "$WORK/out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "scan: a scanner error fails the scan"
assert_eq "" "$(cat "$WORK/github_output")" "scan: a scanner error reports no counts"
assert_not_contains "$(calls)" "convert" "scan: stops at the scanner error"

setup_scan
STUB_FAIL=convert
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    SECURITY_IMAGE_SCAN_IGNOREFILE="$WORK/ignore-empty.yaml" \
    sis_scan ghcr.io/yontrack/yontrack/ontrack:run-1 "$WORK/out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "scan: a SARIF conversion error fails the scan"

setup_scan
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    SECURITY_IMAGE_SCAN_IGNOREFILE="$WORK/ignore-no-statement.yaml" \
    sis_scan ghcr.io/yontrack/yontrack/ontrack:run-1 "$WORK/out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "scan: refuses to run on an invalid ignore file"
assert_eq "" "$(calls)" "scan: checks the ignore file before scanning anything"

setup_scan
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    SECURITY_IMAGE_SCAN_IGNOREFILE="$WORK/ignore-empty.yaml" \
    sis_scan "" "$WORK/out" 2>&1)"; rc=$?
assert_eq "1" "$rc" "scan: refuses to run without an image"

# ===========================================================================
# main
# ===========================================================================

out="$(sis_main 2>&1)"; rc=$?
assert_eq "1" "$rc" "no command: fails with the usage"
assert_contains "$out" "scan|count|check-ignorefile" "no command: prints the usage"

out="$(sis_main count "$WORK/clean.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "main: dispatches count"
assert_contains "$out" "low=0" "main: count prints the counts"

# --- report ----------------------------------------------------------------

report_tests
