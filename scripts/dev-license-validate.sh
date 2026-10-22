#!/usr/bin/env bash
#
# Records the LICENSE.DEV validation stamp on the Yontrack build (#2107): how long the development
# key of the CI is still valid.
#
# Called by the `kdsl-report` job of .github/workflows/ci.yml. The KDSL and Playwright stacks run
# the `dev` profile, which enables no licensed feature without that key, so an expired key turns
# every licensed test red at once. The stamp says so a month before, on Slack (see the
# notifications of .yontrack/ci.yaml), and says why when it happens. It lives here rather than
# inline in the workflow so that the judgement is tested (scripts/dev-license-validate-test.sh).
#
# Usage: scripts/dev-license-validate.sh [EXTRA...]
#
#   EXTRA     passed through to the `yontrack validate` call: $YONTRACK_RUN_INFO.
#
# Environment:
#   YONTRACK_DEV_LICENSE_KEY      the development key. Not set, or empty - a fork's pull request
#                                 gets no secret - and nothing is recorded: the KDSL jobs have
#                                 already failed on it, saying why.
#   DEV_LICENSE_VALIDATE_TODAY    the day to judge against, YYYY-MM-DD (default: today, UTC). Only
#                                 the tests set it.
#   DEV_LICENSE_VALIDATE_CLI      the CLI to call (default: `yontrack`). Only the tests set it.
#
# What is sent
# ------------
#   PASSED    more than 30 days left, or no end of validity at all
#   WARNING   30 days or fewer left - the key is still valid on its last day
#   FAILED    expired, or the key cannot be read
#
# The signature is not checked: the instance does that, and refuses the key if it is not genuine.
# Only the end of validity is read here.

set -uo pipefail

# The days of warning before the end of validity
DLV_WARNING_DAYS=30

dlv_cli() { "${DEV_LICENSE_VALIDATE_CLI:-yontrack}" "$@"; }

# Prints the status and the description of the stamp, separated by a tab, for a key and a day.
dlv_judge() {
    local key="$1" today="$2"
    python3 -I - "$key" "$today" "$DLV_WARNING_DAYS" <<'PY'
import base64, datetime, json, sys

key, today, warning_days = sys.argv[1], datetime.date.fromisoformat(sys.argv[2]), int(sys.argv[3])
try:
    container = json.loads(base64.b64decode(key, validate=True))
    data = json.loads(base64.b64decode(container["data"], validate=True))
    valid_until = data.get("validUntil")
    valid_until = datetime.date.fromisoformat(valid_until) if valid_until else None
except Exception:
    print("FAILED\tThe development key cannot be read")
    sys.exit(0)

if valid_until is None:
    print("PASSED\tValid with no end date")
else:
    days = (valid_until - today).days
    if days < 0:
        print(f"FAILED\tExpired on {valid_until} ({-days} days ago)")
    elif days <= warning_days:
        print(f"WARNING\tValid until {valid_until} ({days} days)")
    else:
        print(f"PASSED\tValid until {valid_until} ({days} days)")
PY
}

dlv_main() {
    local key="${YONTRACK_DEV_LICENSE_KEY:-}"
    if [ -z "$key" ]; then
        echo "No development key: LICENSE.DEV is not recorded."
        return 0
    fi
    local today="${DEV_LICENSE_VALIDATE_TODAY:-$(date -u +%F)}"
    local judgement status description
    judgement="$(dlv_judge "$key" "$today")" || return 1
    status="${judgement%%$'\t'*}"
    description="${judgement#*$'\t'}"
    echo "LICENSE.DEV: $status - $description"
    dlv_cli validate --validation LICENSE.DEV --status "$status" --description "$description" "$@"
}

if [ -z "${DEV_LICENSE_VALIDATE_LIB_ONLY:-}" ]; then
    dlv_main "$@"
    exit $?
fi
