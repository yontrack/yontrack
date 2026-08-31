#!/usr/bin/env bash
#
# Tests for the pure functions of dev-stack.sh: slug derivation, slot
# allocation and port arithmetic. No Docker, Gradle or npm involved.
#
# Usage: scripts/dev-stack-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load dev-stack.sh as a library: defines the functions, runs nothing.
DEV_STACK_LIB_ONLY=1
export DEV_STACK_LIB_ONLY
# shellcheck source=dev-stack.sh
source "$SCRIPT_DIR/dev-stack.sh"

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

assert_within() {
    local min="$1" max="$2" actual="$3" message="$4"
    tests_run=$((tests_run + 1))
    if [ "$actual" -lt "$min" ] || [ "$actual" -gt "$max" ]; then
        tests_failed=$((tests_failed + 1))
        echo "FAIL: $message"
        echo "      expected: between $min and $max"
        echo "      actual:   $actual"
    fi
}

# --- ds_slug ---------------------------------------------------------------

assert_eq "5-0" "$(ds_slug /Users/damien/workspaces/ontrack/5.0)" \
    "ds_slug replaces dots with dashes"
assert_eq "yontrack" "$(ds_slug /home/user/yontrack)" \
    "ds_slug keeps a plain name"
assert_eq "feature-1645" "$(ds_slug /tmp/worktrees/feature_1645)" \
    "ds_slug replaces underscores with dashes"
assert_eq "my-branch" "$(ds_slug /tmp/My.Branch)" \
    "ds_slug lowercases and collapses separators"
assert_eq "abc" "$(ds_slug /tmp/--abc--)" \
    "ds_slug trims leading and trailing separators"
assert_eq "instance" "$(ds_slug /tmp/!!!)" \
    "ds_slug falls back to a default when nothing survives sanitising"

# --- ds_slot_from_path -----------------------------------------------------

slot_a="$(ds_slot_from_path /Users/damien/worktrees/alpha)"
slot_a_again="$(ds_slot_from_path /Users/damien/worktrees/alpha)"
assert_eq "$slot_a" "$slot_a_again" \
    "ds_slot_from_path is deterministic for the same path"
assert_within 1 9 "$slot_a" \
    "ds_slot_from_path allocates a linked worktree into slots 1-9"

# Slot 0 is reserved for the main checkout, so hashing never returns it.
for path in /a /b /c /d /e /f /g /h /i /j /k /l; do
    assert_within 1 9 "$(ds_slot_from_path "$path")" \
        "ds_slot_from_path never returns the reserved slot 0 (path $path)"
done

# Distinct checkouts sharing a basename must not collide: the whole absolute
# path is hashed, not just the last segment. Asserted on the hash rather than
# the slot, because two distinct hashes can legitimately land on one slot.
hash_x="$(ds_path_hash /Users/damien/projects/one/5.0)"
hash_y="$(ds_path_hash /Users/damien/projects/two/5.0)"
tests_run=$((tests_run + 1))
if [ "$hash_x" = "$hash_y" ]; then
    tests_failed=$((tests_failed + 1))
    echo "FAIL: ds_path_hash hashes the full path, not the basename"
    echo "      both /one/5.0 and /two/5.0 hashed to $hash_x"
fi

assert_eq "$(ds_path_hash /tmp/alpha)" "$(ds_path_hash /tmp/alpha)" \
    "ds_path_hash is deterministic"

# --- ds_offset -------------------------------------------------------------

assert_eq "0" "$(ds_offset 0)" "ds_offset leaves the main checkout on today's ports"
assert_eq "100" "$(ds_offset 1)" "ds_offset steps by 100 per slot"
assert_eq "900" "$(ds_offset 9)" "ds_offset handles the highest slot"

# --- ds_port ---------------------------------------------------------------

assert_eq "3000" "$(ds_port 3000 0)" "ds_port: UI on slot 0"
assert_eq "3100" "$(ds_port 3000 1)" "ds_port: UI on slot 1"
assert_eq "8180" "$(ds_port 8080 1)" "ds_port: backend on slot 1"
assert_eq "8900" "$(ds_port 8800 1)" "ds_port: management on slot 1"
assert_eq "8108" "$(ds_port 8008 1)" "ds_port: Keycloak on slot 1"
assert_eq "5532" "$(ds_port 5432 1)" "ds_port: Postgres on slot 1"
assert_eq "9300" "$(ds_port 9200 1)" "ds_port: Elasticsearch on slot 1"
assert_eq "15772" "$(ds_port 15672 1)" "ds_port: Rabbit management on slot 1"

# --- ds_project ------------------------------------------------------------

assert_eq "yontrack-dev-5-0" "$(ds_project 5-0)" \
    "ds_project uses the same naming convention for every slot"
assert_eq "yontrack-dev-alpha" "$(ds_project alpha)" \
    "ds_project prefixes the slug"

# --- ds_next_slot ----------------------------------------------------------

assert_eq "1" "$(ds_next_slot 0)" "ds_next_slot bumps a taken slot 0 into the hashed range"
assert_eq "2" "$(ds_next_slot 1)" "ds_next_slot advances within the range"
assert_eq "1" "$(ds_next_slot 9)" "ds_next_slot wraps around to 1, never back to 0"

# --- report ----------------------------------------------------------------

echo
if [ "$tests_failed" -eq 0 ]; then
    echo "OK: $tests_run assertions passed"
    exit 0
else
    echo "FAILED: $tests_failed of $tests_run assertions failed"
    exit 1
fi
