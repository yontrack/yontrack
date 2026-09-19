#!/usr/bin/env bash
#
# Records the six COVERAGE.* validation stamps on the Yontrack build (#1822).
#
# Called by the `coverage` job of .github/workflows/ci.yml, once the reports are built and the
# figures computed. It lives here rather than inline in the workflow for the reason stated at the
# top of scripts/coverage-report.sh, and for one specific to it: which figure may be sent for
# which stamp, given what a run lost, is the whole judgement of this issue, and it is testable
# here (scripts/coverage-validate-test.sh) and untestable in workflow YAML.
#
# Usage: scripts/coverage-validate.sh STATUS METRICS [-- EXTRA...]
#
#   STATUS    the output of `scripts/coverage-report.sh status`: one `<type><TAB>ok` or
#             `<type><TAB><reason>` line per backend test type. A file that is not there means
#             the check itself did not run, which is not the same as everything being fine.
#   METRICS   the JSON document scripts/coverage-metrics.sh writes. Absent or unreadable when no
#             figure could be computed at all.
#   EXTRA     passed through to every `yontrack validate` call, after the `metrics` subcommand:
#             the run time and $YONTRACK_RUN_INFO. `validateMetricsCmd` redeclares the run-info
#             flags on itself, which is why they go there and not on the parent command.
#
# Environment:
#   COVERAGE_REPORTS_URL    the run the reports are an artefact of; it goes in the description of
#                           every stamp that passes.
#   COVERAGE_VALIDATE_CLI   the CLI to call (default: `yontrack`). Only the tests set it.
#
# What is sent
# ------------
# The stamps are declared with the `metrics` data type in .yontrack/ci.yaml, sit in no promotion
# and carry no threshold: they are a record, not a gate. `MetricsValidationDataType.computeStatus`
# returns null and `ValidationDataTypeServiceImpl` then falls back to PASSED, so the figures alone
# are the whole happy path -- no `--status` is sent on it.
#
# `--build` is deliberately not spelled out, unlike the `chml` calls in the same workflow: in CLI
# 5.1.1 `validateMetricsCmd` resolves the build through `GetProjectBranchBuildFlags`, which falls
# back to $YONTRACK_BUILD_NAME, so the name the workflow-build action exports is picked up. `chml`
# calls `GetProjectBranchFlags` and needs the flag; this does not.
#
# When a leg lost its data
# ------------------------
# Per stamp, never all-or-nothing, and never a smaller number:
#
#   * a type whose sessions are incomplete is FAILED, with the missing ones in the description,
#     and sends no figure;
#   * COVERAGE.TOTAL is FAILED as soon as ANY backend type is incomplete -- a merged figure over
#     part of the suite reads as a real coverage drop, in the one direction that matters;
#   * the types that did report send their real `line` and `branch`, which are read from their own
#     report and are unaffected by what another type lost, but NOT `unique_line`: "covered by this
#     type and by no other" is measured against the other types, so a missing type inflates every
#     other type's unique share;
#   * COVERAGE.UI_UNIT comes from Jest and is independent of all of it.
#
# A stamp is always sent. A run with no COVERAGE.UNIT at all is a hole in the history that nothing
# explains; a red one says what happened, and these stamps gate nothing.

set -uo pipefail

CV_TYPES=(unit integration kdsl ui)

cv_fail() { echo "ERROR: $*" >&2; return 1; }

cv_cli() { "${COVERAGE_VALIDATE_CLI:-yontrack}" "$@"; }

cv_main() {
    local status_file="${1:-}" metrics_file="${2:-}"
    [ -n "$status_file" ] || { cv_fail "No status file"; return 1; }
    [ -n "$metrics_file" ] || { cv_fail "No metrics file"; return 1; }
    shift 2
    [ "${1:-}" = "--" ] && shift

    local -a extra=("$@")
    local reports_url="${COVERAGE_REPORTS_URL:-}"
    local reports="Coverage reports: ${reports_url:-see the run} (artefact \`coverage-reports\`)"

    local metrics=""
    if [ -f "$metrics_file" ]; then
        metrics="$(cat "$metrics_file")" || metrics=""
    fi

    # Nothing was computed: every stamp is FAILED and says so.
    if [ -z "$metrics" ]; then
        local reason="No coverage figures could be computed. See ${reports_url:-the run}"
        local stamp failures=0
        for stamp in UNIT INTEGRATION KDSL UI TOTAL UI_UNIT; do
            cv_failed "COVERAGE.$stamp" "$reason" "${extra[@]+"${extra[@]}"}" \
                || failures=$((failures + 1))
        done
        return $(( failures > 0 ? 1 : 0 ))
    fi

    # The completeness of each backend type. An absent status file leaves every type unknown,
    # which is treated as incomplete: the check not having run is not the same as it having
    # passed, and the figures would be believed for years.
    local type reason failures=0 backend_ok=1
    declare -A cv_reason=()
    for type in "${CV_TYPES[@]}"; do
        cv_reason[$type]="the completeness of the coverage data could not be established."
    done
    if [ -f "$status_file" ]; then
        while IFS=$'\t' read -r type reason; do
            [ -n "$type" ] || continue
            cv_reason[$type]="$reason"
        done < "$status_file"
    fi
    for type in "${CV_TYPES[@]}"; do
        [ "${cv_reason[$type]}" = "ok" ] || backend_ok=0
    done

    local stamp partial="Not every backend test type reported, so no unique_line is recorded."
    for type in "${CV_TYPES[@]}"; do
        stamp="COVERAGE.$(tr '[:lower:]' '[:upper:]' <<< "$type")"
        reason="${cv_reason[$type]}"
        if [ "$reason" != "ok" ]; then
            cv_failed "$stamp" "$reason" "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))
        elif [ "$backend_ok" -eq 1 ]; then
            cv_passed "$stamp" "$reports" \
                "line=$(cv_figure "$metrics" "$type" line)" \
                "branch=$(cv_figure "$metrics" "$type" branch)" \
                "unique_line=$(cv_figure "$metrics" "$type" unique_line)" \
                "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))
        else
            cv_passed "$stamp" "$reports $partial" \
                "line=$(cv_figure "$metrics" "$type" line)" \
                "branch=$(cv_figure "$metrics" "$type" branch)" \
                "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))
        fi
    done

    if [ "$backend_ok" -eq 1 ]; then
        cv_passed COVERAGE.TOTAL "$reports" \
            "line=$(cv_figure "$metrics" total line)" \
            "branch=$(cv_figure "$metrics" total branch)" \
            "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))
    else
        cv_failed COVERAGE.TOTAL \
            "Not every backend test type reported: a merged figure over part of the suite would read as a coverage drop." \
            "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))
    fi

    # Jest (#1820), and nothing to do with what the JaCoCo legs did or did not collect.
    cv_passed COVERAGE.UI_UNIT "$reports" \
        "line=$(cv_figure "$metrics" ui_unit line)" \
        "branch=$(cv_figure "$metrics" ui_unit branch)" \
        "${extra[@]+"${extra[@]}"}" || failures=$((failures + 1))

    return $(( failures > 0 ? 1 : 0 ))
}

cv_figure() {
    jq -r --arg key "$2" --arg metric "$3" '.[$key][$metric]' <<< "$1"
}

# cv_passed STAMP DESCRIPTION name=value... [EXTRA...]
#
# The metrics are the arguments that look like `name=value`; everything after the last of them is
# passed through. Splitting on the shape rather than on a marker keeps the call sites readable,
# and a metric name can never contain a `-`, so a flag can never be mistaken for one.
cv_passed() {
    local stamp="$1" description="$2"
    shift 2
    local -a metrics=() extra=()
    local argument
    for argument in "$@"; do
        if [ "${#extra[@]}" -eq 0 ] && [[ "$argument" == [a-z_]*=* ]]; then
            metrics+=(--metric "$argument")
        else
            extra+=("$argument")
        fi
    done
    cv_cli validate --validation "$stamp" --description "$description" \
        metrics "${metrics[@]}" "${extra[@]+"${extra[@]}"}"
}

# cv_failed STAMP DESCRIPTION [EXTRA...]
#
# No subcommand: a validation with a status and no data. The run-info flags are declared on the
# `validate` command itself, so they are accepted here too.
cv_failed() {
    local stamp="$1" description="$2"
    shift 2
    cv_cli validate --validation "$stamp" --status FAILED --description "$description" "$@"
}

if [ -z "${COVERAGE_VALIDATE_LIB_ONLY:-}" ]; then
    cv_main "$@"
    exit $?
fi
