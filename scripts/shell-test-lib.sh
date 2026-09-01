#!/usr/bin/env bash
#
# Assertions for the shell test suites - scripts/demo-deploy-test.sh and
# scripts/demo-smoke-test.sh. Sourced, never executed.
#
# These suites are run by hand rather than in CI, so the report at the end is the whole
# result: it has to say how many assertions ran, not just whether the script exited 0.
#
# Usage:
#
#     source "$SCRIPT_DIR/shell-test-lib.sh"
#     assert_eq expected actual "what this proves"
#     ...
#     report_tests   # exits 0 or 1

tests_run=0
tests_failed=0

assert_eq() {
    local expected="$1" actual="$2" message="$3"
    tests_run=$((tests_run + 1))
    if [ "$expected" != "$actual" ]; then
        tests_failed=$((tests_failed + 1))
        echo "FAIL: $message"
        echo "      expected: '$expected'"
        echo "      actual:   '$actual'"
    fi
}

assert_contains() {
    local haystack="$1" needle="$2" message="$3"
    tests_run=$((tests_run + 1))
    case "$haystack" in
        *"$needle"*) ;;
        *)
            tests_failed=$((tests_failed + 1))
            echo "FAIL: $message"
            echo "      expected to contain: '$needle'"
            echo "      actual:              '$haystack'"
            ;;
    esac
}

assert_not_contains() {
    local haystack="$1" needle="$2" message="$3"
    tests_run=$((tests_run + 1))
    case "$haystack" in
        *"$needle"*)
            tests_failed=$((tests_failed + 1))
            echo "FAIL: $message"
            echo "      expected NOT to contain: '$needle'"
            echo "      actual:                  '$haystack'"
            ;;
    esac
}

# The suite's verdict. Call it last: it exits.
report_tests() {
    echo
    if [ "$tests_failed" -eq 0 ]; then
        echo "OK: $tests_run assertions passed"
        exit 0
    else
        echo "FAILED: $tests_failed of $tests_run assertions failed"
        exit 1
    fi
}
