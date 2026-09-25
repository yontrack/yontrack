#!/usr/bin/env bash
#
# Scans a Docker image with Trivy and counts its vulnerabilities per severity (#1749).
#
# Called by .github/actions/security-image-scan, which ci.yml's `security-images` job uses on
# every full run, and which the nightly rescan (#1751) is meant to reuse. The logic lives here
# rather than inline in a workflow so that there is exactly one definition of "the count" - the
# CI stamp and the nightly one must never disagree about what they measure - and so that
# scripts/security-image-scan-test.sh can exercise it against a stubbed Trivy.
#
# Usage: scripts/security-image-scan.sh scan|count|check-ignorefile ...
#
#   scan IMAGE OUTDIR       Checks the ignore file, scans IMAGE once to OUTDIR/trivy.json,
#                           converts that same report to OUTDIR/trivy.sarif for the GitHub
#                           Security tab, and prints the counts. When $GITHUB_OUTPUT is set the
#                           counts are also written there as `critical`, `high`, `medium`, `low`.
#   count REPORT            Prints the counts of a Trivy JSON report.
#   check-ignorefile FILE   Fails when an entry of a .trivyignore.yaml has no `statement` or no
#                           `expired_at`.
#
# Environment:
#   SECURITY_IMAGE_SCAN_IGNOREFILE   ignore file to apply (default: .trivyignore.yaml at the
#                                    root of the repository)
#   SECURITY_IMAGE_SCAN_TIMEOUT      Trivy's own timeout (default: 15m - the backend image is
#                                    large, and Trivy's 5m default is a scanner error waiting to
#                                    happen)
#
# What is counted, and why:
#
#   * Only vulnerabilities with a fix available (`--ignore-unfixed`). A count nobody can bring
#     down by upgrading is noise on a stamp.
#   * CRITICAL, HIGH, MEDIUM and LOW. UNKNOWN is left out: the CHML stamp has no slot for it.
#   * One entry per (vulnerability, package) as Trivy reports it: the same CVE in two packages
#     counts twice, since both have to be upgraded.
#   * Not what the ignore file accepts. The scan keeps those in the report (`--show-suppressed`),
#     but under `ExperimentalModifiedFindings`, which the count does not read: they are there for
#     the findings mirror (#1869), which sends them as accepted findings. Nor does the SARIF:
#     Trivy's SARIF writer reads the vulnerabilities alone, so the Security tab is unchanged.
#
# Findings never fail a scan - they only drive the stamp's status. A scanner *error* does:
# an image that cannot be pulled or a database that cannot be downloaded must not read as an
# image with zero vulnerabilities.
#
# Requires trivy, jq and yq (mikefarah's, v4) on the PATH. ubuntu-latest carries jq and yq.

set -uo pipefail

SIS_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

sis_fail() { echo "ERROR: $*" >&2; return 1; }

# Prints `critical=N`, `high=N`, `medium=N`, `low=N`, one per line.
#
# `// []` on both levels: Trivy writes `"Vulnerabilities": null` for a target with nothing
# found, and `Results` is absent or null for an image it found no packages in. Both are zero,
# not an error. A report that is missing or not JSON *is* an error.
sis_count() {
    local report="$1"
    [ -f "$report" ] || { sis_fail "No Trivy report at $report"; return 1; }
    jq -r '
        [(.Results // [])[] | (.Vulnerabilities // [])[] | .Severity] as $s
        | ["CRITICAL", "HIGH", "MEDIUM", "LOW"][]
        | . as $level
        | "\($level | ascii_downcase)=\([$s[] | select(. == $level)] | length)"
    ' "$report" || { sis_fail "Could not read the Trivy report $report"; return 1; }
}

# Every entry of every section (vulnerabilities, secrets...) must say why the risk is accepted
# and until when. Trivy itself accepts both as optional, which is exactly what this closes.
sis_check_ignorefile() {
    local file="$1" invalid
    [ -f "$file" ] || { sis_fail "No ignore file at $file"; return 1; }
    invalid="$(yq -o=json '.' "$file" | jq -r '
        (. // {}) | to_entries[]
        | .key as $section
        | (.value // [])[]
        | select(
            ((.statement // "") | tostring | length) == 0
            or ((.expired_at // "") | tostring | length) == 0
          )
        | "\($section): \(.id // "<no id>")"
    ')" || { sis_fail "Could not parse the ignore file $file"; return 1; }
    if [ -n "$invalid" ]; then
        echo "ERROR: every entry of $file needs a statement and an expired_at date. Missing on:" >&2
        while IFS= read -r entry; do
            echo "  - $entry" >&2
        done <<< "$invalid"
        return 1
    fi
}

sis_scan() {
    local image="${1:-}" outdir="${2:-}" ignorefile counts
    [ -n "$image" ] || { sis_fail "No image to scan"; return 1; }
    [ -n "$outdir" ] || { sis_fail "No output directory"; return 1; }
    ignorefile="${SECURITY_IMAGE_SCAN_IGNOREFILE:-$SIS_REPO_ROOT/.trivyignore.yaml}"

    sis_check_ignorefile "$ignorefile" || return 1
    mkdir -p "$outdir" || return 1

    echo "Scanning $image"
    # Scanned once. The SARIF is converted from this same report rather than produced by a
    # second scan, so the Security tab and the stamp can never be looking at two databases.
    trivy image \
        --scanners vuln \
        --ignore-unfixed \
        --show-suppressed \
        --ignorefile "$ignorefile" \
        --exit-code 0 \
        --no-progress \
        --timeout "${SECURITY_IMAGE_SCAN_TIMEOUT:-15m}" \
        --format json \
        --output "$outdir/trivy.json" \
        "$image" || { sis_fail "Trivy could not scan $image"; return 1; }

    trivy convert \
        --format sarif \
        --output "$outdir/trivy.sarif" \
        "$outdir/trivy.json" || { sis_fail "Trivy could not convert the report to SARIF"; return 1; }

    counts="$(sis_count "$outdir/trivy.json")" || return 1
    echo "$counts"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$counts" >> "$GITHUB_OUTPUT"
    fi
}

sis_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        scan) sis_scan "$@" ;;
        count) sis_count "${1:-}" ;;
        check-ignorefile) sis_check_ignorefile "${1:-$SIS_REPO_ROOT/.trivyignore.yaml}" ;;
        *)
            echo "Usage: $0 scan|count|check-ignorefile ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${SECURITY_IMAGE_SCAN_LIB_ONLY:-}" ]; then
    sis_main "$@"
    exit $?
fi
