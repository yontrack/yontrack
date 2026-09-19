#!/usr/bin/env bash
#
# Tests for scripts/coverage-metrics.sh and its scripts/coverage-metrics.py (#1821).
#
# Run by hand: ./scripts/coverage-metrics-test.sh
#
# Every fixture is a handful of hand-written JaCoCo XML lines whose expected figures are computed
# here in the comments, so that "what 72.4 means" is pinned down by arithmetic a reader can redo
# rather than by whatever the script happens to print.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# An explicit template: a bare `mktemp -d` ignores $TMPDIR on macOS and lands wherever the
# platform's own temporary directory is, which is not always writable.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/coverage-metrics-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# Writes a JaCoCo XML report. Each remaining argument is `file:nr:ci:cb:mb` -- one <line>, with
# `ci` the covered instructions (so `ci > 0` means the line is covered), `cb`/`mb` the covered and
# missed branches.
write_report() {
    local path="$1"
    shift
    mkdir -p "$(dirname "$path")"
    {
        echo '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        echo '<report name="fixture">'
        echo '  <package name="net/nemerosa/fixture">'
        local current="" spec file nr ci cb mb
        for spec in "$@"; do
            IFS=: read -r file nr ci cb mb <<< "$spec"
            if [ "$file" != "$current" ]; then
                [ -n "$current" ] && echo '    </sourcefile>'
                echo "    <sourcefile name=\"$file\">"
                current="$file"
            fi
            echo "      <line nr=\"$nr\" mi=\"0\" ci=\"$ci\" mb=\"$mb\" cb=\"$cb\"/>"
        done
        [ -n "$current" ] && echo '    </sourcefile>'
        echo '  </package>'
        echo '</report>'
    } > "$path"
}

write_jest_summary() {
    local path="$1" lines="$2" branches="$3"
    mkdir -p "$(dirname "$path")"
    cat > "$path" <<EOF
{"total":{"lines":{"pct":$lines},"branches":{"pct":$branches},"statements":{"pct":1},"functions":{"pct":1}}}
EOF
}

# ===============================================================================================
# A complete set, with an overlap whose `unique_line` is computed by hand below.
# ===============================================================================================
#
# Ten lines, A.kt 1-5 and B.kt 1-5, are the denominator of every line figure.
#
#   unit        covers A:1 A:2 A:3                  -> 3 lines
#   integration covers A:3 A:4 B:1                  -> 3 lines
#   kdsl        covers B:1 B:2                      -> 2 lines
#   ui          covers B:2 B:3                      -> 2 lines
#   merged      covers A:1 A:2 A:3 A:4 B:1 B:2 B:3  -> 7 lines
#
#   unique: unit A:1 A:2      -> 2/10 = 20.0
#           integration A:4   -> 1/10 = 10.0
#           kdsl (B:1 shared with integration, B:2 with ui) -> 0/10 = 0.0
#           ui B:3            -> 1/10 = 10.0
#   line:   unit 30.0, integration 30.0, kdsl 20.0, ui 20.0, total 70.0
#
# Branches, all on A:1, so that each type's branch figure is its own and independent of the lines:
#   unit 3 covered / 1 missed = 75.0; integration 1/3 = 25.0; kdsl 0/0 = 0.0 (no branches at all);
#   ui 2/2 = 50.0; merged is the union of the *reports*, so its own <line> carries 3/1 = 75.0.

REPORTS="$WORK/complete"
all_lines=(A.kt:1:0:0:0 A.kt:2:0:0:0 A.kt:3:0:0:0 A.kt:4:0:0:0 A.kt:5:0:0:0
           B.kt:1:0:0:0 B.kt:2:0:0:0 B.kt:3:0:0:0 B.kt:4:0:0:0 B.kt:5:0:0:0)

# Each report lists all ten lines -- as a real jacococli report does, since every report is built
# from the same class files -- with `ci` set on the covered ones.
report_for() {
    local out="$1" cb="$2" mb="$3"
    shift 3
    local -a specs=() spec covered=" $* "
    local file nr
    for spec in "${all_lines[@]}"; do
        IFS=: read -r file nr _ _ _ <<< "$spec"
        local key="$file:$nr" ci=0
        case "$covered" in *" $key "*) ci=1 ;; esac
        if [ "$key" = "A.kt:1" ]; then
            specs+=("$file:$nr:$ci:$cb:$mb")
        else
            specs+=("$file:$nr:$ci:0:0")
        fi
    done
    write_report "$out" "${specs[@]}"
}

report_for "$REPORTS/unit/jacoco.xml" 3 1 A.kt:1 A.kt:2 A.kt:3
report_for "$REPORTS/integration/jacoco.xml" 1 3 A.kt:3 A.kt:4 B.kt:1
report_for "$REPORTS/kdsl/jacoco.xml" 0 0 B.kt:1 B.kt:2
report_for "$REPORTS/ui/jacoco.xml" 2 2 B.kt:2 B.kt:3
report_for "$REPORTS/merged/jacoco.xml" 3 1 A.kt:1 A.kt:2 A.kt:3 A.kt:4 B.kt:1 B.kt:2 B.kt:3

write_jest_summary "$WORK/jest/coverage-summary.json" 61.25 43.9

export COVERAGE_JEST_SUMMARY="$WORK/jest/coverage-summary.json"
output="$(COVERAGE_JEST_SUMMARY="$COVERAGE_JEST_SUMMARY" \
    "$SCRIPT_DIR/coverage-metrics.sh" "$REPORTS" "$WORK/metrics.json" 2>&1)"
status=$?
assert_eq "0" "$status" "a complete set of reports computes the figures: $output"

m() { jq -r "$1" "$WORK/metrics.json"; }

assert_eq "30" "$(m '.unit.line')" "unit line = 3 of 10"
assert_eq "20" "$(m '.unit.unique_line')" "unit unique = A:1 A:2 of 10"
assert_eq "75" "$(m '.unit.branch')" "unit branch = 3 of 4"
assert_eq "30" "$(m '.integration.line')" "integration line = 3 of 10"
assert_eq "10" "$(m '.integration.unique_line')" "integration unique = A:4 of 10"
assert_eq "25" "$(m '.integration.branch')" "integration branch = 1 of 4"
assert_eq "20" "$(m '.kdsl.line')" "kdsl line = 2 of 10"
assert_eq "0" "$(m '.kdsl.unique_line')" "kdsl covers nothing no other type covers"
assert_eq "0" "$(m '.kdsl.branch')" "no branches at all is 0, not a crash"
assert_eq "20" "$(m '.ui.line')" "ui line = 2 of 10"
assert_eq "10" "$(m '.ui.unique_line')" "ui unique = B:3 of 10"
assert_eq "50" "$(m '.ui.branch')" "ui branch = 2 of 4"
assert_eq "70" "$(m '.total.line')" "merged line = 7 of 10"
assert_eq "75" "$(m '.total.branch')" "merged branch = 3 of 4"
assert_eq "61.3" "$(m '.ui_unit.line')" "Jest lines, rounded to one decimal"
assert_eq "43.9" "$(m '.ui_unit.branch')" "Jest branches"
assert_eq "null" "$(m '.total.unique_line')" "COVERAGE.TOTAL has no unique_line"
assert_eq "null" "$(m '.ui_unit.unique_line')" "COVERAGE.UI_UNIT has no unique_line"

# The four unique figures may not add up to more than the merged line figure.
sum="$(jq -r '[.unit, .integration, .kdsl, .ui | .unique_line] | add' "$WORK/metrics.json")"
assert_eq "true" "$(jq -n --argjson s "$sum" --argjson t "$(m '.total.line')" '$s <= $t')" \
    "the unique figures sum to at most the merged line figure"

assert_contains "$output" "COVERAGE.TOTAL" "the table names the stamps"
assert_contains "$output" "| 70 |" "the table carries the merged figure"

# ===============================================================================================
# A missing report: no figures at all, and a message naming what is missing.
# ===============================================================================================

MISSING="$WORK/missing"
mkdir -p "$MISSING"
cp -R "$REPORTS"/* "$MISSING/"
rm -rf "$MISSING/kdsl"

output="$("$SCRIPT_DIR/coverage-metrics.sh" "$MISSING" "$WORK/missing.json" 2>&1)"
status=$?
assert_eq "1" "$status" "a missing report fails"
assert_contains "$output" "kdsl" "and says which type is missing"
assert_eq "false" "$([ -f "$WORK/missing.json" ] && echo true || echo false)" \
    "and emits nothing: a partial figure reads as a real coverage drop"

# ===============================================================================================
# A type whose report is empty -- the suite ran and covered nothing.
# ===============================================================================================

EMPTY="$WORK/empty"
mkdir -p "$EMPTY"
cp -R "$REPORTS"/* "$EMPTY/"
report_for "$EMPTY/kdsl/jacoco.xml" 0 4

output="$("$SCRIPT_DIR/coverage-metrics.sh" "$EMPTY" "$WORK/empty.json" 2>&1)"
status=$?
assert_eq "0" "$status" "a type that covered nothing is a figure, not a failure: $output"
assert_eq "0" "$(jq -r '.kdsl.line' "$WORK/empty.json")" "an empty type is 0.0, not absent"
assert_eq "0" "$(jq -r '.kdsl.unique_line' "$WORK/empty.json")" "and its unique figure is 0.0"
assert_eq "0" "$(jq -r '.kdsl.branch' "$WORK/empty.json")" "and its branch figure is 0.0"

# ===============================================================================================
# A report built from different class files: the denominators disagree, so nothing is comparable.
# ===============================================================================================

SKEWED="$WORK/skewed"
mkdir -p "$SKEWED"
cp -R "$REPORTS"/* "$SKEWED/"
write_report "$SKEWED/ui/jacoco.xml" A.kt:1:1:0:0 A.kt:2:0:0:0

output="$("$SCRIPT_DIR/coverage-metrics.sh" "$SKEWED" "$WORK/skewed.json" 2>&1)"
status=$?
assert_eq "1" "$status" "a report over a different denominator fails"
assert_contains "$output" "different class files" "and says why"

# ===============================================================================================
# A missing Jest summary: COVERAGE.UI_UNIT cannot be computed, so nothing is emitted.
# ===============================================================================================

output="$(COVERAGE_JEST_SUMMARY="$WORK/nowhere.json" \
    "$SCRIPT_DIR/coverage-metrics.sh" "$REPORTS" "$WORK/nojest.json" 2>&1)"
status=$?
assert_eq "1" "$status" "a missing Jest summary fails"
assert_contains "$output" "Jest coverage summary" "and says what was missing"

report_tests
