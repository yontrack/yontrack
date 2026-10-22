#!/usr/bin/env bash
#
# Tests for scripts/dev-license-validate.sh (#2107).
#
# Run by hand: ./scripts/dev-license-validate-test.sh
#
# The CLI is replaced by a recorder that writes its arguments to a log, so what is asserted is the
# command line the `kdsl-report` job sends. The keys are built here: only their end of validity is
# read, never their signature.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# An explicit template: a bare `mktemp -d` ignores $TMPDIR on macOS.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/dev-license-validate-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

CLI="$WORK/fake-yontrack"
cat > "$CLI" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CALL_LOG"
EOF
chmod +x "$CLI"

export DEV_LICENSE_VALIDATE_CLI="$CLI"
export CALL_LOG="$WORK/calls.log"

# A key whose signed content has the given end of validity ("null" for none)
key_until() {
    local valid_until="$1"
    if [ "$valid_until" != "null" ]; then
        valid_until="\"$valid_until\""
    fi
    local data
    data="$(printf '{"name":"Development","validUntil":%s,"usage":"dev"}' "$valid_until" | base64 | tr -d '\n')"
    printf '{"data":"%s","signature":"c2lnbmF0dXJl"}' "$data" | base64 | tr -d '\n'
}

# Runs the script for a key on a day, and prints the command line it sent (empty when none)
run() {
    local key="$1" today="$2"
    rm -f "$CALL_LOG"
    YONTRACK_DEV_LICENSE_KEY="$key" DEV_LICENSE_VALIDATE_TODAY="$today" \
        "$SCRIPT_DIR/dev-license-validate.sh" --source-type github-workflow > /dev/null
    [ -f "$CALL_LOG" ] && cat "$CALL_LOG"
}

assert_eq \
    "validate --validation LICENSE.DEV --status PASSED --description Valid until 2027-10-22 (365 days) --source-type github-workflow" \
    "$(run "$(key_until 2027-10-22)" 2026-10-22)" \
    "a year left is PASSED, and the extra arguments follow"

assert_eq \
    "validate --validation LICENSE.DEV --status PASSED --description Valid until 2027-10-22 (31 days) --source-type github-workflow" \
    "$(run "$(key_until 2027-10-22)" 2027-09-21)" \
    "31 days left is still PASSED"

assert_eq \
    "validate --validation LICENSE.DEV --status WARNING --description Valid until 2027-10-22 (30 days) --source-type github-workflow" \
    "$(run "$(key_until 2027-10-22)" 2027-09-22)" \
    "30 days left is a WARNING"

assert_eq \
    "validate --validation LICENSE.DEV --status WARNING --description Valid until 2027-10-22 (0 days) --source-type github-workflow" \
    "$(run "$(key_until 2027-10-22)" 2027-10-22)" \
    "the last day of validity is still a WARNING, not a failure"

assert_eq \
    "validate --validation LICENSE.DEV --status FAILED --description Expired on 2027-10-22 (1 days ago) --source-type github-workflow" \
    "$(run "$(key_until 2027-10-22)" 2027-10-23)" \
    "the day after the end of validity is FAILED"

assert_eq \
    "validate --validation LICENSE.DEV --status PASSED --description Valid with no end date --source-type github-workflow" \
    "$(run "$(key_until null)" 2026-10-22)" \
    "a key with no end of validity is PASSED"

assert_eq \
    "validate --validation LICENSE.DEV --status FAILED --description The development key cannot be read --source-type github-workflow" \
    "$(run "not-a-key" 2026-10-22)" \
    "a key which cannot be read is FAILED"

assert_eq \
    "" \
    "$(run "" 2026-10-22)" \
    "no key, nothing recorded"

report_tests
