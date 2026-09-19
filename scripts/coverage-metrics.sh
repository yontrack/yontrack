#!/usr/bin/env bash
#
# The six sets of coverage figures (#1821).
#
# Entry point for the computation scripts/coverage-metrics.py performs, and the place its contract
# is written down. Called by the `coverage` job of .github/workflows/ci.yml, and by the local
# Gradle path of #1823 -- which is why the figures are defined here and not in workflow YAML:
# if CI and a local run can disagree about what "72.4" means, the number is worthless.
#
# Usage: scripts/coverage-metrics.sh REPORTDIR [OUTPUT]
#
#   REPORTDIR   the output directory of `scripts/coverage-report.sh report`: it holds
#               unit/, integration/, kdsl/, ui/ and merged/, each with a jacoco.xml.
#   OUTPUT      where the JSON document goes (default: REPORTDIR/coverage-metrics.json).
#
# Environment:
#   COVERAGE_JEST_SUMMARY   Jest's summary from #1820
#                           (default: ontrack-web-core/coverage/coverage-summary.json).
#   GITHUB_OUTPUT           when set, `metrics=<compact json>` is appended to it. Optional: the
#                           computation itself never reads a $GITHUB_* variable.
#   GITHUB_STEP_SUMMARY     when set, the table below is appended to it as well.
#
# The contract -- #1822 depends on it
# -----------------------------------
# OUTPUT is a JSON object with exactly these six keys, each a map of metric name to a percentage
# from 0 to 100 with one decimal:
#
#     {
#       "unit":        {"line": 0.0, "branch": 0.0, "unique_line": 0.0},
#       "integration": {"line": 0.0, "branch": 0.0, "unique_line": 0.0},
#       "kdsl":        {"line": 0.0, "branch": 0.0, "unique_line": 0.0},
#       "ui":          {"line": 0.0, "branch": 0.0, "unique_line": 0.0},
#       "total":       {"line": 0.0, "branch": 0.0},
#       "ui_unit":     {"line": 0.0, "branch": 0.0}
#     }
#
# The keys are the COVERAGE.* stamps of the design record, lowercased: `total` is COVERAGE.TOTAL
# and `ui_unit` is COVERAGE.UI_UNIT, the only one that does not come from JaCoCo at all.
#
# `line` is covered lines over all lines; `branch` is covered branches over all branches;
# `unique_line` is the lines covered by that type and by no other backend type, over the *same*
# denominator as `line`, so the four are directly comparable and sum to at most `total.line`.
#
# **Partial figures are never emitted.** Anything missing -- a report, the Jest summary, a
# denominator that does not match the merged one -- fails, writes no output, and says what was
# missing. A figure computed over part of the suite reads as a real coverage drop, which is worse
# than no figure at all.
#
# Completeness of the *execution data* is a separate question, asked by
# `scripts/coverage-report.sh status` and answered per backend test type. This script computes
# every figure regardless: a type whose legs all reported has an honest `line` and `branch` even
# when another type lost everything, since each is read from that type's own report. Which of
# these figures may be sent to which stamp is `scripts/coverage-validate.sh`'s decision, and
# `unique_line` is the one that does not survive a missing type -- see its header (#1822).

set -uo pipefail

CM_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CM_REPO_ROOT="$(cd "$CM_SCRIPT_DIR/.." && pwd)"

cm_fail() { echo "ERROR: $*" >&2; return 1; }

# A markdown table of the figures, for a human and for $GITHUB_STEP_SUMMARY.
cm_table() {
    local json="$1"
    echo "| Stamp | line | branch | unique_line |"
    echo "|-------|------|--------|-------------|"
    jq -r '
        ["unit", "integration", "kdsl", "ui", "total", "ui_unit"][] as $key
        | .[$key] as $m
        | "| COVERAGE.\($key | ascii_upcase) | \($m.line) | \($m.branch) | \($m.unique_line // "-") |"
    ' <<< "$json"
}

cm_main() {
    local report_dir="${1:-}" output="${2:-}" jest json
    [ -n "$report_dir" ] || { cm_fail "No report directory"; return 1; }
    [ -d "$report_dir" ] || { cm_fail "No report directory at $report_dir"; return 1; }
    output="${output:-$report_dir/coverage-metrics.json}"
    jest="${COVERAGE_JEST_SUMMARY:-$CM_REPO_ROOT/ontrack-web-core/coverage/coverage-summary.json}"

    python3 "$CM_SCRIPT_DIR/coverage-metrics.py" "$report_dir" "$jest" --output "$output" || return 1

    json="$(cat "$output")" || return 1
    echo "$json"

    local table
    table="$(cm_table "$json")" || return 1
    echo
    echo "$table"

    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "metrics=$(jq -c . "$output")" >> "$GITHUB_OUTPUT"
    fi
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        {
            echo "### Test coverage"
            echo
            echo "$table"
        } >> "$GITHUB_STEP_SUMMARY"
    fi
}

if [ -z "${COVERAGE_METRICS_LIB_ONLY:-}" ]; then
    cm_main "$@"
    exit $?
fi
