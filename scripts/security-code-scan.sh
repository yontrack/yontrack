#!/usr/bin/env bash
#
# Turns the CodeQL alerts of a ref into a security findings report, and finds the Yontrack build
# to report it on (#1750, #1875).
#
# Called by .github/workflows/codeql.yml, whose `report` job validates SECURITY.CODE once every
# language has been analysed. The logic lives here rather than inline in the workflow so that
# scripts/security-code-scan-test.sh can exercise it against a stubbed `gh` and `yontrack`.
#
# Usage: scripts/security-code-scan.sh findings|summary|fetch|resolve-build ...
#
#   findings [FILE]              Converts a code-scanning alerts answer - one page or several
#                                printed back to back, as `gh api --paginate` does - read from
#                                FILE, or from stdin, into a report in the neutral `findings`
#                                format, printed on stdout.
#   summary REPORT               Prints the open findings of REPORT by severity, as `critical`,
#                                `high`, `medium`, `low`, and the accepted ones as `accepted`.
#                                When $GITHUB_OUTPUT is set they are also written there.
#   fetch REPOSITORY REF OUTPUT  Fetches the open and dismissed CodeQL alerts of REF and writes
#                                them to OUTPUT as a report in the neutral `findings` format.
#   resolve-build PROJECT COMMIT Finds the Yontrack build CI registered for COMMIT, waiting for it
#                                if need be. Writes `found=true`, `project`, `branch` and `build`
#                                (the build *name*) to $GITHUB_OUTPUT, or `found=false` when no
#                                build appeared in time - which is not an error.
#
# Environment:
#   SECURITY_CODE_SCAN_BUILD_ATTEMPTS  build lookups before giving up (default: 20)
#   SECURITY_CODE_SCAN_BUILD_DELAY     seconds between two lookups (default: 30)
#
# What is reported, and why:
#
#   * The alerts API, not the SARIF file: dismissals live in GitHub, not in the SARIF, so sending
#     the SARIF would bring every dismissed alert back as open. A dismissed alert is an accepted
#     finding, its reason and comment as the statement.
#   * `tool_name=CodeQL` only. The Trivy image scans of ci.yml (#1749) upload to the same ref, and
#     their findings are already reported by SECURITY.IMAGE.*.
#   * Security alerts only, by `rule.security_severity_level`. Code-quality queries have no
#     security severity and are left out: the stamp is about security.
#   * The rule id and the path without its line: a finding must survive the code moving.
#
# An answer that cannot be read is an error, never an empty report: a failed call must not read
# as clean code.
#
# Requires jq on the PATH, plus `gh` (authenticated through GH_TOKEN) for fetch and the Yontrack
# CLI (configured) for resolve-build. ubuntu-latest carries jq and gh.

set -uo pipefail

scs_fail() { echo "ERROR: $*" >&2; return 1; }

# `jq -s` slurps however many arrays the answer holds - one per page - and each of them must be
# an array: an error object (a 404 when the ref was never analysed, say) is not a page of zero
# alerts.
scs_findings() {
    local input="${1:--}"
    if [ "$input" != "-" ] && [ ! -f "$input" ]; then
        scs_fail "No alerts file at $input"
        return 1
    fi
    jq -e -s -c '
        if length == 0 or any(.[]; type != "array") then
            error("not a list of code-scanning alerts")
        else
            {
                scanner: "codeql",
                kind: "CODE",
                findings: [
                    .[][]
                    | select(.rule.security_severity_level != null)
                    | {
                        externalId: .rule.id,
                        location: (.most_recent_instance.location.path // ""),
                        severity: (.rule.security_severity_level | ascii_upcase),
                        rawSeverity: .rule.security_severity_level,
                        title: (.rule.description // .rule.id),
                        url: .html_url
                    } + (
                        if .state == "dismissed" then
                            {acceptance: {
                                statement: ([.dismissed_reason, .dismissed_comment]
                                    | map(select(. != null and . != "")) | join(": ")),
                                source: "GitHub code scanning alert #\(.number)"
                            }}
                        else {} end
                    )
                ]
            }
        end
    ' "$input" || { scs_fail "Could not read the code-scanning alerts"; return 1; }
}

# For the job summary only: the stamp's status is computed by Yontrack, from the report itself.
scs_summary() {
    local report="${1:-}" summary
    [ -f "$report" ] || { scs_fail "No findings report at $report"; return 1; }
    summary="$(jq -e -r '
        [.findings[] | select(.acceptance == null) | .severity] as $open
        | (["CRITICAL", "HIGH", "MEDIUM", "LOW"][]
            | . as $level
            | "\($level | ascii_downcase)=\([$open[] | select(. == $level)] | length)"),
          "accepted=\([.findings[] | select(.acceptance != null)] | length)"
    ' "$report")" || { scs_fail "Could not read the findings report $report"; return 1; }
    echo "$summary"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$summary" >> "$GITHUB_OUTPUT"
    fi
}

scs_fetch() {
    local repository="${1:-}" ref="${2:-}" output="${3:-}" alerts state page report
    [ -n "$repository" ] || { scs_fail "No repository"; return 1; }
    [ -n "$ref" ] || { scs_fail "No ref"; return 1; }
    [ -n "$output" ] || { scs_fail "No output file"; return 1; }

    alerts=""
    for state in open dismissed; do
        echo "Fetching the $state CodeQL alerts of $repository at $ref" >&2
        page="$(gh api --paginate \
            "repos/$repository/code-scanning/alerts?ref=$ref&state=$state&tool_name=CodeQL&per_page=100")" \
            || { scs_fail "Could not fetch the $state code-scanning alerts of $ref"; return 1; }
        alerts+="$page"$'\n'
    done
    report="$(printf '%s' "$alerts" | scs_findings)" || return 1
    printf '%s\n' "$report" > "$output"
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
        findings) scs_findings "$@" ;;
        summary) scs_summary "$@" ;;
        fetch) scs_fetch "$@" ;;
        resolve-build) scs_resolve_build "$@" ;;
        *)
            echo "Usage: $0 findings|summary|fetch|resolve-build ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${SECURITY_CODE_SCAN_LIB_ONLY:-}" ]; then
    scs_main "$@"
    exit $?
fi
