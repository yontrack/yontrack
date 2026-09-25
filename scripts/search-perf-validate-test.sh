#!/usr/bin/env bash
#
# Tests for scripts/search-perf-validate.sh (#1887).
#
# Run by hand: ./scripts/search-perf-validate-test.sh
#
# The CLI is replaced by a recorder that writes its arguments to a log and answers `build search`
# from a table, so what is asserted is exactly the command line the nightly workflow sends, and
# the build it picks. Neither can be seen from a green run: a stamp posted on the wrong build, or
# PASSED for a run whose EXPLAIN assertion failed, looks like a correct one until someone reads it.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# An explicit template: a bare `mktemp -d` ignores $TMPDIR on macOS.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/search-perf-validate-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# The recorder. One line per call. `build search` answers from $BUILDS, one `<commit> <json>` line
# per commit that has a build; FAKE_CLI_FAIL makes every call fail, as an unreachable instance
# would.
CLI="$WORK/fake-yontrack"
cat > "$CLI" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CALL_LOG"
[ -z "${FAKE_CLI_FAIL:-}" ] || exit 1
if [ "$1 $2" = "build search" ]; then
    commit=""
    while [ $# -gt 0 ]; do
        [ "$1" = "--commit" ] && commit="$2"
        shift
    done
    grep "^$commit " "$BUILDS" 2>/dev/null | head -n 1 | cut -d' ' -f2-
fi
exit 0
EOF
chmod +x "$CLI"

RUN_URL="https://github.com/yontrack/yontrack/actions/runs/42"
export SEARCH_PERF_RUN_URL="$RUN_URL"

# Runs the script with the recorder in place, and sets `calls`, `out` and `script_status`. Not a
# command substitution: that would run it in a subshell and lose the exit code, which is half of
# what is asserted.
run_script() {
    local log="$WORK/calls.txt"
    rm -f "$log" "$WORK/github-output.txt"
    CALL_LOG="$log" SEARCH_PERF_CLI="$CLI" BUILDS="$WORK/builds.txt" \
        GITHUB_OUTPUT="$WORK/github-output.txt" \
        "$SCRIPT_DIR/search-perf-validate.sh" "$@" > "$WORK/out.txt" 2>&1
    script_status=$?
    calls="$(cat "$log" 2>/dev/null)"
    out="$(cat "$WORK/out.txt")"
}

run_validate() {
    run_script validate "$1" yontrack v6 20260925231746-413 \
        -- --run-time 600 --source-type github-workflow --source-uri "$RUN_URL" \
           --trigger-type schedule --trigger-data abc123
}

# A report shaped like SearchPerf's, with the figures of its first local run. $1 is jq applied to it.
REPORT="$WORK/search-perf.json"
write_report() {
    jq "${1:-.}" > "$REPORT" <<'EOF'
{
  "palette_p95": 361.6,
  "results_p95": 508.4,
  "commit_lookup_p95": 49.6,
  "exact_build_p95": 73.9,
  "rebuild_seconds": 139.7,
  "explain": [
    {"scenario": "palette", "query": "payment", "statement": "palette rows",
     "expected_indexes": ["search_documents_ix_identifiers_trgm"], "passed": true,
     "indexes": ["search_documents_ix_identifiers_trgm"]},
    {"scenario": "commit_lookup", "query": "0a1b2c3d", "statement": "palette rows",
     "expected_indexes": ["search_documents_ix_identifiers"], "passed": true,
     "indexes": ["search_documents_ix_identifiers"]}
  ],
  "details": {
    "budgets": {"palette_p95": 150.0, "results_p95": 500.0, "exact_build_p95": 150.0, "commit_lookup_p95": 150.0},
    "over_budget": [],
    "failures": []
  }
}
EOF
}

# ===============================================================================================
# validate: a clean run
# ===============================================================================================

write_report
run_validate "$REPORT"
assert_eq "0" "$script_status" "a clean run succeeds: $out"
assert_eq "1" "$(grep -c . <<< "$calls")" "one call"
assert_contains "$calls" "validate --project yontrack --branch v6 --build 20260925231746-413 --validation SEARCH.PERFORMANCE" \
    "on the resolved build, spelled out: nothing in this workflow exports YONTRACK_BUILD_NAME"
assert_contains "$calls" \
    "metrics --metric palette_p95=361.6 --metric results_p95=508.4 --metric commit_lookup_p95=49.6 --metric exact_build_p95=73.9 --metric rebuild_seconds=139.7" \
    "the five figures, in the order of the report"
assert_not_contains "$calls" "--status" "no status: metrics falls back to PASSED"
assert_contains "$calls" "--metric rebuild_seconds=139.7 --run-time 600 --source-type github-workflow" \
    "the pass-through arguments come last, after the metrics subcommand"
assert_contains "$calls" "--description All EXPLAIN assertions passed, every p95 within budget. See $RUN_URL, artefact search-perf-report." \
    "the description says so and points at the run"

# ===============================================================================================
# validate: over budget is recorded, not failed
# ===============================================================================================

write_report '.details.over_budget = ["palette_p95", "results_p95"]'
run_validate "$REPORT"
assert_eq "0" "$script_status" "a p95 over its budget is not a failure: $out"
assert_not_contains "$calls" "--status" "still PASSED"
assert_contains "$calls" "metrics --metric palette_p95=361.6" "with its figures"
assert_contains "$calls" "Over budget: palette_p95 361.6 ms (budget 150), results_p95 508.4 ms (budget 500)." \
    "and the description names each figure over, against its budget"

# ===============================================================================================
# validate: a failed EXPLAIN assertion
# ===============================================================================================

write_report '.explain[1] += {"passed": false, "reason": "Seq Scan on search_documents", "sql": "SELECT 1", "plan": "Seq Scan"}
    | .details.failures = ["EXPLAIN commit_lookup '"'"'0a1b2c3d'"'"' (palette rows): Seq Scan on search_documents"]'
run_validate "$REPORT"
assert_eq "1" "$script_status" "a failed EXPLAIN assertion fails the step, and so the run"
assert_eq "1" "$(grep -c . <<< "$calls")" "one call all the same: the stamp is sent"
assert_contains "$calls" "--validation SEARCH.PERFORMANCE --status FAILED" "FAILED"
assert_contains "$calls" "EXPLAIN assertion failed: commit_lookup '0a1b2c3d' (palette rows): Seq Scan on search_documents." \
    "with the offending scenario, query and statement, and why"
assert_not_contains "$calls" "metrics" "and no figure"
assert_contains "$calls" "--build 20260925231746-413" "on the resolved build"
assert_contains "$calls" "--run-time 600 --source-type github-workflow" "with the run info"
assert_contains "$out" "::error title=SEARCH.PERFORMANCE::EXPLAIN assertion failed" "and an annotation on the run"

write_report '.explain[0] += {"passed": false, "reason": "no index of its tier"}
    | .explain[1] += {"passed": false, "reason": "Seq Scan"}
    | .details.failures = ["one", "two"]'
run_validate "$REPORT"
assert_contains "$calls" "EXPLAIN assertion failed (and 1 more): palette 'payment' (palette rows): no index of its tier." \
    "several failures: the first, and how many more"

# ===============================================================================================
# validate: the other failures
# ===============================================================================================

write_report '.details.failures = ["palette_p95 = 1612.3 ms, past its ceiling of 1500.0 ms"]'
run_validate "$REPORT"
assert_eq "1" "$script_status" "a p95 past its ceiling fails"
assert_contains "$calls" "--status FAILED --description searchPerfTest failed: palette_p95 = 1612.3 ms, past its ceiling of 1500.0 ms." \
    "FAILED, the failure as the test words it"
assert_not_contains "$calls" "metrics" "and no figure"

write_report '.details.failures = ["Rebuild: 3 batches could not be written", "results_p95 = 5100.0 ms, past its ceiling of 5000.0 ms"]'
run_validate "$REPORT"
assert_contains "$calls" "searchPerfTest failed: Rebuild: 3 batches could not be written; results_p95 = 5100.0 ms" \
    "several failures, all of them"

# What SearchPerf.main writes when run() threw: details only.
echo '{"details": {"failures": ["Error: Connection refused"]}}' > "$REPORT"
run_validate "$REPORT"
assert_eq "1" "$script_status" "a crash fails"
assert_contains "$calls" "--status FAILED --description searchPerfTest failed: Error: Connection refused." "and says why"

# ===============================================================================================
# validate: no report
# ===============================================================================================

run_validate "$WORK/does-not-exist.json"
assert_eq "1" "$script_status" "no report fails"
assert_eq "1" "$(grep -c . <<< "$calls")" "and still sends the stamp"
assert_contains "$calls" "--status FAILED --description searchPerfTest wrote no report: it did not get as far as measuring." \
    "saying the test never measured"

echo '{"palette_p95": 36' > "$REPORT"
run_validate "$REPORT"
assert_contains "$calls" "--status FAILED --description searchPerfTest wrote no report" "a truncated report is no report"

write_report 'del(.rebuild_seconds) | del(.exact_build_p95)'
run_validate "$REPORT"
assert_eq "1" "$script_status" "a report missing a figure fails"
assert_contains "$calls" "--status FAILED --description The searchPerfTest report has no figure for: exact_build_p95 rebuild_seconds." \
    "naming the missing figures"
assert_not_contains "$calls" "--metric" "and sends none: a partial set reads as a smaller measure"

# ===============================================================================================
# validate: the description fits its column
# ===============================================================================================

long="$(printf 'x%.0s' $(seq 1 700))"
write_report ".details.failures = [\"$long\"]"
run_validate "$REPORT"
description="$(sed -e 's/.*--description //' -e 's/ --run-time.*//' <<< "$calls")"
assert_eq "500" "${#description}" "cut to VARCHAR(500)"
assert_contains "$description" "xxx..." "with an ellipsis"

# ===============================================================================================
# validate: the stamp could not be posted
# ===============================================================================================

write_report
FAKE_CLI_FAIL=1 run_validate "$REPORT"
assert_eq "1" "$script_status" "a PASSED that could not be posted fails the step"
assert_contains "$out" "Could not post SEARCH.PERFORMANCE" "and says so"

run_script validate "$REPORT" yontrack v6
assert_eq "1" "$script_status" "no build, no call"
assert_eq "" "$calls" "nothing is sent"

# ===============================================================================================
# resolve
# ===============================================================================================

REPO="$WORK/repo"
git init -q -b v6 "$REPO"
for message in one two three; do
    git -C "$REPO" -c user.name=t -c user.email=t@t commit -q --allow-empty -m "$message"
done
c1="$(git -C "$REPO" rev-parse HEAD~2)"
c2="$(git -C "$REPO" rev-parse HEAD~1)"
c3="$(git -C "$REPO" rev-parse HEAD)"
build_json() {
    printf '{"Id":"1","Name":"%s","DisplayName":"%s","Branch":{"Name":"v6","Project":{"Name":"yontrack"}}}' "$1" "$2"
}

{
    echo "$c1 $(build_json b-1 6.0.0-rc-1)"
    echo "$c3 $(build_json b-3 6.0.0-rc-3)"
} > "$WORK/builds.txt"
run_script resolve yontrack v6 "$REPO"
assert_eq "0" "$script_status" "the head has a build: $out"
assert_contains "$calls" "build search --project yontrack --branch v6 --commit $c3 --count 1 --output json --accept-not-found" \
    "searched on the branch, by commit"
assert_eq "1" "$(grep -c . <<< "$calls")" "the head only"
assert_eq "commit=$c3
build=b-3
version=6.0.0-rc-3" "$(cat "$WORK/github-output.txt")" "the head's build, as step outputs"
assert_not_contains "$out" "::notice" "nothing to notice"

# The head is a [skip ci] commit, with no build of its own.
echo "$c1 $(build_json b-1 6.0.0-rc-1)" > "$WORK/builds.txt"
run_script resolve yontrack v6 "$REPO"
assert_eq "0" "$script_status" "an older commit with a build will do"
assert_eq "$c3 $c2 $c1" "$(sed -e 's/.*--commit //' -e 's/ .*//' <<< "$calls" | tr '\n' ' ' | sed 's/ $//')" \
    "walking back one commit at a time, from the head"
assert_contains "$(cat "$WORK/github-output.txt")" "commit=$c1" "and the commit to check out is that one"
assert_contains "$(cat "$WORK/github-output.txt")" "build=b-1" "with its build"
assert_contains "$out" "::notice title=SEARCH.PERFORMANCE::The head of v6 ($c3) has no Yontrack build; measuring $c1 instead" \
    "which the run says"

SEARCH_PERF_MAX_COMMITS=2 run_script resolve yontrack v6 "$REPO"
assert_eq "1" "$script_status" "no build in the window: fails"
assert_eq "2" "$(grep -c . <<< "$calls")" "having looked no further than the window"
assert_contains "$out" "None of the last 2 commits of v6 has a build" "and says so"
assert_eq "" "$(cat "$WORK/github-output.txt" 2>/dev/null)" "with no output"

FAKE_CLI_FAIL=1 run_script resolve yontrack v6 "$REPO"
assert_eq "1" "$script_status" "an instance that cannot be reached fails"
assert_eq "1" "$(grep -c . <<< "$calls")" "at once, rather than reading as no build and walking on"

run_script resolve yontrack v6 "$WORK/not-a-repo"
assert_eq "1" "$script_status" "no history, no build"

run_script nonsense
assert_eq "1" "$script_status" "an unknown command fails"
assert_contains "$out" "Usage:" "with the usage"

report_tests
