#!/usr/bin/env bash
#
# Tests for scripts/coverage-validate.sh (#1822).
#
# Run by hand: ./scripts/coverage-validate-test.sh
#
# The CLI is replaced by a recorder that writes its arguments to a log, so what is asserted is
# exactly the six command lines the `coverage` job sends. That is the part that cannot be seen
# from a green run: a stamp reported with the wrong metric name, or a figure sent for a test type
# whose execution data never arrived, looks identical to a correct one until somebody reads the
# chart months later and believes it.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# An explicit template: a bare `mktemp -d` ignores $TMPDIR on macOS.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/coverage-validate-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# The recorder. One line per call, arguments separated by a single space -- every argument the
# script passes is a single word or a quoted string with no newline in it.
CLI="$WORK/fake-yontrack"
cat > "$CLI" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CALL_LOG"
EOF
chmod +x "$CLI"

METRICS="$WORK/metrics.json"
cat > "$METRICS" <<'EOF'
{
  "unit":        {"line": 20.2, "branch": 21.9, "unique_line": 3.8},
  "integration": {"line": 76.8, "branch": 52.2, "unique_line": 18.1},
  "kdsl":        {"line": 51.0, "branch": 21.8, "unique_line": 1.6},
  "ui":          {"line": 50.0, "branch": 21.5, "unique_line": 1.6},
  "total":       {"line": 85.8, "branch": 65.5},
  "ui_unit":     {"line": 27.6, "branch": 52.4}
}
EOF

complete_status() {
    printf 'unit\tok\nintegration\tok\nkdsl\tok\nui\tok\n' > "$1"
}

# Runs the script with the recorder in place, and sets `calls` to its log and `validate_status`
# to its exit code. Not a command substitution: that would run it in a subshell and lose the exit
# code the caller is half of what is being asserted. Everything after `--` is what the workflow
# passes through: the run time it measured, and $YONTRACK_RUN_INFO.
run_validate() {
    local log="$WORK/calls.txt"
    rm -f "$log"
    CALL_LOG="$log" COVERAGE_VALIDATE_CLI="$CLI" \
        "$SCRIPT_DIR/coverage-validate.sh" "$@" \
        -- --run-time 90 --source-type github-workflow \
           --source-uri https://github.com/yontrack/yontrack/actions/runs/42 \
           --trigger-type push --trigger-data deadbeef \
        > "$WORK/out.txt" 2>&1
    validate_status=$?
    calls="$(cat "$log" 2>/dev/null)"
}

# One call line, by the stamp it names.
call_for() {
    grep -- "--validation $1 " <<< "$2" | head -n 1
}

# ===============================================================================================
# A complete run: six PASSED stamps, with their figures
# ===============================================================================================

STATUS="$WORK/status.txt"
complete_status "$STATUS"
COVERAGE_REPORTS_URL="https://github.com/yontrack/yontrack/actions/runs/42"
export COVERAGE_REPORTS_URL

run_validate "$STATUS" "$METRICS"
assert_eq "0" "$validate_status" "a complete run succeeds: $(cat "$WORK/out.txt")"
assert_eq "6" "$(grep -c . <<< "$calls")" "six stamps, one call each"

unit="$(call_for COVERAGE.UNIT "$calls")"
assert_contains "$unit" "validate --validation COVERAGE.UNIT" "the stamp is named on the parent command"
assert_contains "$unit" "metrics --metric line=20.2 --metric branch=21.9 --metric unique_line=3.8" \
    "with the three per-type metrics, in order"
assert_not_contains "$unit" "--status" "and no status at all: metrics falls back to PASSED"
assert_not_contains "$unit" "--build" \
    "nor --build: validateMetricsCmd reads YONTRACK_BUILD_NAME, unlike chml"
assert_contains "$unit" "--run-time 90 --source-type github-workflow" \
    "the pass-through arguments come last, after the metrics subcommand"

assert_contains "$(call_for COVERAGE.INTEGRATION "$calls")" \
    "--metric line=76.8 --metric branch=52.2 --metric unique_line=18.1" "the integration figures"
assert_contains "$(call_for COVERAGE.KDSL "$calls")" \
    "--metric line=51 --metric branch=21.8 --metric unique_line=1.6" "the KDSL figures"
assert_contains "$(call_for COVERAGE.UI "$calls")" \
    "--metric line=50 --metric branch=21.5 --metric unique_line=1.6" "the Playwright figures"

total="$(call_for COVERAGE.TOTAL "$calls")"
assert_contains "$total" "metrics --metric line=85.8 --metric branch=65.5" "the merged total"
assert_not_contains "$total" "unique_line" "which has no unique share to report"

ui_unit="$(call_for COVERAGE.UI_UNIT "$calls")"
assert_contains "$ui_unit" "metrics --metric line=27.6 --metric branch=52.4" "the Jest figures"
assert_not_contains "$ui_unit" "unique_line" "and no unique share either"

assert_contains "$unit" "--description Coverage reports: https://github.com/yontrack/yontrack/actions/runs/42" \
    "a passing run points at the reports"

# ===============================================================================================
# One test type lost its execution data
# ===============================================================================================

printf 'unit\tok\nintegration\tok\nkdsl\tno execution data for: kdsl-2\nui\tok\n' > "$STATUS"
run_validate "$STATUS" "$METRICS"
assert_eq "0" "$validate_status" "an incomplete set still succeeds: the stamps carry the news"
assert_eq "6" "$(grep -c . <<< "$calls")" "and all six stamps are still sent"

kdsl="$(call_for COVERAGE.KDSL "$calls")"
assert_contains "$kdsl" "--status FAILED" "the type that lost its data is FAILED"
assert_contains "$kdsl" "--description no execution data for: kdsl-2" "naming what was missing"
assert_not_contains "$kdsl" "metrics" "and sends no figure at all: a partial figure is a lie"

total="$(call_for COVERAGE.TOTAL "$calls")"
assert_contains "$total" "--status FAILED" "the merged total follows any incomplete type"
assert_contains "$total" "coverage drop" "saying why a merged figure is withheld"

unit="$(call_for COVERAGE.UNIT "$calls")"
assert_contains "$unit" "metrics --metric line=20.2 --metric branch=21.9" \
    "a complete type still records its own line and branch"
assert_not_contains "$unit" "--metric unique_line" \
    "but not unique_line, which a missing type inflates"
assert_contains "$unit" "no unique_line is recorded" "and the description says so"
assert_not_contains "$unit" "--status FAILED" "a lost kdsl shard does not fail COVERAGE.UNIT"

assert_contains "$(call_for COVERAGE.UI_UNIT "$calls")" "--metric line=27.6" \
    "the Jest figures are untouched by anything JaCoCo did"

# ===============================================================================================
# No figures at all
# ===============================================================================================

complete_status "$STATUS"
run_validate "$STATUS" "$WORK/does-not-exist.json"
assert_eq "0" "$validate_status" "a missing metrics document is reported, not thrown"
assert_eq "6" "$(grep -c . <<< "$calls")" "every stamp is still sent"
assert_eq "6" "$(grep -c -- "--status FAILED" <<< "$calls")" "all six FAILED"
assert_contains "$(call_for COVERAGE.UNIT "$calls")" "No coverage figures could be computed" \
    "with a reason a reader can act on"
assert_contains "$(call_for COVERAGE.UNIT "$calls")" "actions/runs/42" "and where to look"

# A status file that never arrived: the check step itself broke. Nothing may be assumed complete.
run_validate "$WORK/no-status.txt" "$METRICS"
assert_eq "6" "$(grep -c . <<< "$calls")" "six stamps, still"
assert_contains "$(call_for COVERAGE.KDSL "$calls")" "--status FAILED" \
    "an unknown completeness is not a complete one"
assert_contains "$(call_for COVERAGE.TOTAL "$calls")" "--status FAILED" "and the total follows"

# ===============================================================================================
# The CLI failing does not lose the other five stamps
# ===============================================================================================

cat > "$CLI" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CALL_LOG"
case "$*" in *COVERAGE.UNIT*) exit 1 ;; esac
EOF
chmod +x "$CLI"

complete_status "$STATUS"
run_validate "$STATUS" "$METRICS"
assert_eq "6" "$(grep -c . <<< "$calls")" "one refused call does not stop the other five"
assert_eq "1" "$validate_status" "but the step fails, so the run says a stamp was lost"

report_tests
