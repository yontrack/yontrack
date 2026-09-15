#!/usr/bin/env bash
#
# Counts the open CodeQL security alerts of a ref, and finds the Yontrack build to report them
# on (#1750).
#
# Called by .github/workflows/codeql.yml, whose `report` job validates SECURITY.CODE once every
# language has been analysed. The logic lives here rather than inline in the workflow so that
# scripts/security-code-scan-test.sh can exercise it against a stubbed `gh` and `yontrack`.
#
# Usage: scripts/security-code-scan.sh count|fetch|resolve-build ...
#
#   count [FILE]                 Prints the counts of a code-scanning alerts answer - one page or
#                                several printed back to back, as `gh api --paginate` does - read
#                                from FILE, or from stdin.
#   fetch REPOSITORY REF         Fetches every open CodeQL alert of REF and prints the counts.
#                                When $GITHUB_OUTPUT is set they are also written there as
#                                `critical`, `high`, `medium`, `low`.
#   resolve-build PROJECT COMMIT Finds the Yontrack build CI registered for COMMIT, waiting for it
#                                if need be. Writes `found=true`, `project`, `branch` and `build`
#                                (the build *name*) to $GITHUB_OUTPUT, or `found=false` when no
#                                build appeared in time - which is not an error.
#
# Environment:
#   SECURITY_CODE_SCAN_BUILD_ATTEMPTS  build lookups before giving up (default: 20)
#   SECURITY_CODE_SCAN_BUILD_DELAY     seconds between two lookups (default: 30)
#
# What is counted, and why:
#
#   * The alerts API, not the SARIF file: dismissing a false positive on GitHub must lower the
#     count in Yontrack, and only the API knows about dismissals.
#   * `state=open` only.
#   * `tool_name=CodeQL` only. The Trivy image scans of ci.yml (#1749) upload to the same ref, and
#     their alerts are already counted by SECURITY.IMAGE.*.
#   * Security alerts only, by `rule.security_severity_level`. Code-quality queries have no
#     security severity and are left out: the CHML stamp is about security.
#
# An answer that cannot be read is an error, never zero alerts: a failed call must not read as
# clean code.
#
# Requires jq on the PATH, plus `gh` (authenticated through GH_TOKEN) for fetch and the Yontrack
# CLI (configured) for resolve-build. ubuntu-latest carries jq and gh.

set -uo pipefail

scs_fail() { echo "ERROR: $*" >&2; return 1; }

# Prints `critical=N`, `high=N`, `medium=N`, `low=N`, one per line.
#
# `jq -s` slurps however many arrays the answer holds - one per page - and each of them must be
# an array: an error object (a 404 when the ref was never analysed, say) is not a page of zero
# alerts.
scs_count() {
    local input="${1:--}"
    if [ "$input" != "-" ] && [ ! -f "$input" ]; then
        scs_fail "No alerts file at $input"
        return 1
    fi
    jq -e -r -s '
        if length == 0 or any(.[]; type != "array") then
            error("not a list of code-scanning alerts")
        else
            [.[][] | .rule.security_severity_level // empty] as $s
            | ["critical", "high", "medium", "low"][]
            | . as $level
            | "\($level)=\([$s[] | select(. == $level)] | length)"
        end
    ' "$input" || { scs_fail "Could not read the code-scanning alerts"; return 1; }
}

scs_fetch() {
    local repository="${1:-}" ref="${2:-}" alerts counts
    [ -n "$repository" ] || { scs_fail "No repository"; return 1; }
    [ -n "$ref" ] || { scs_fail "No ref"; return 1; }

    echo "Fetching the open CodeQL alerts of $repository at $ref" >&2
    alerts="$(gh api --paginate \
        "repos/$repository/code-scanning/alerts?ref=$ref&state=open&tool_name=CodeQL&per_page=100")" \
        || { scs_fail "Could not fetch the code-scanning alerts of $ref"; return 1; }

    counts="$(printf '%s' "$alerts" | scs_count)" || return 1
    echo "$counts"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$counts" >> "$GITHUB_OUTPUT"
    fi
}

# ci.yml registers its build in its `yontrack` job, a few minutes into the run, and this
# workflow starts on the same push. The analysis nearly always outlasts that, but "nearly" is
# a race: a CI run queued behind another one on the same ref (ci.yml does not cancel) registers
# its build much later. Hence the wait. A build that still does not exist after it - a branch CI
# never registers, a Dependabot push - is skipped rather than failed.
#
# A lookup that *errors* is retried too, but an error on the last attempt fails: Yontrack being
# down is not the same thing as there being no build.
scs_resolve_build() {
    local project="${1:-}" commit="${2:-}"
    local attempts="${SECURITY_CODE_SCAN_BUILD_ATTEMPTS:-20}"
    local delay="${SECURITY_CODE_SCAN_BUILD_DELAY:-30}"
    local attempt=1 answer rc outputs
    [ -n "$project" ] || { scs_fail "No project"; return 1; }
    [ -n "$commit" ] || { scs_fail "No commit"; return 1; }

    while :; do
        answer="$(yontrack build search \
            --project "$project" \
            --commit "$commit" \
            --count 1 \
            --accept-not-found \
            --output json)"
        rc=$?
        if [ "$rc" -eq 0 ] && [ -n "$answer" ]; then
            outputs="$(printf '%s' "$answer" | jq -e -r '
                "found=true",
                "project=\(.Branch.Project.Name)",
                "branch=\(.Branch.Name)",
                "build=\(.Name)"
            ' 2>/dev/null)" \
                || { scs_fail "Could not read the build found for $commit: $answer"; return 1; }
            echo "$outputs"
            if [ -n "${GITHUB_OUTPUT:-}" ]; then
                echo "$outputs" >> "$GITHUB_OUTPUT"
            fi
            return 0
        fi

        if [ "$attempt" -ge "$attempts" ]; then
            if [ "$rc" -ne 0 ]; then
                scs_fail "Yontrack could not be searched for the build of $commit"
                return 1
            fi
            echo "No build of $project for commit $commit after $attempts attempts: nothing to report."
            if [ -n "${GITHUB_OUTPUT:-}" ]; then
                echo "found=false" >> "$GITHUB_OUTPUT"
            fi
            return 0
        fi

        if [ "$rc" -ne 0 ]; then
            echo "Attempt $attempt/$attempts: Yontrack could not be searched, retrying in ${delay}s" >&2
        else
            echo "Attempt $attempt/$attempts: no build of $project for $commit yet, retrying in ${delay}s" >&2
        fi
        attempt=$((attempt + 1))
        sleep "$delay"
    done
}

scs_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        count) scs_count "$@" ;;
        fetch) scs_fetch "$@" ;;
        resolve-build) scs_resolve_build "$@" ;;
        *)
            echo "Usage: $0 count|fetch|resolve-build ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${SECURITY_CODE_SCAN_LIB_ONLY:-}" ]; then
    scs_main "$@"
    exit $?
fi
