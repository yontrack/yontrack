#!/usr/bin/env bash
#
# Sends a validation with its evidence: the raw file the stamp was computed from and an HTML
# report rendered from it (#2128). doc/dev-guide/ci-evidence.md lists what each stamp attaches.
#
# Called by the workflows and by scripts/search-perf-validate.sh, in place of `yontrack`. What goes
# wrong with evidence, and what it must never cost, is the whole judgement here - testable in
# scripts/ci-evidence-test.sh, untestable in workflow YAML.
#
# Usage: scripts/ci-evidence.sh KIND STAMP INPUT... [--tool T] [--tool-version V] -- CLI_ARGS...
#
#   junit STAMP PATTERN          <STAMP>-junit.zip and <STAMP>-report.html, from what the same
#                                glob as the stamp's `--pattern` matches
#
#   trivy STAMP REPORT           <STAMP>-trivy.json and <STAMP>-report.html
#   findings STAMP REPORT        <STAMP>-findings.json and <STAMP>-report.html
#   dast STAMP FINDINGS MANIFEST <STAMP>-findings.json, <STAMP>-manifest.json and <STAMP>-report.html
#   search-perf STAMP REPORT     <STAMP>-report.json and <STAMP>-report.html
#
# CLI_ARGS is the `yontrack` call, from `validate` on. The evidence flags are added after it.
#
# `trivy` implies --tool trivy. --tool-version is for a pin held by the caller. Without it, the version of a known tool is read
# from where it is pinned, or from the tool: gradle, jest, playwright, trivy.
#
# Environment:
#   CI_EVIDENCE_DIR      where the evidence is written (default: $RUNNER_TEMP/ci-evidence), one
#                        directory per stamp, emptied first
#   CI_EVIDENCE_COMMIT   the commit of the report's header (default: see ce_commit)
#   CI_EVIDENCE_CLI      the CLI to call (default: `yontrack`). Only the tests set it.

set -uo pipefail

CE_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CE_REPO_ROOT="$(cd "$CE_SCRIPT_DIR/.." && pwd)"

ce_fail() { echo "ERROR: $*" >&2; return 1; }

ce_cli() { "${CI_EVIDENCE_CLI:-yontrack}" "$@"; }

ce_py() { python3 "$CE_SCRIPT_DIR/ci-evidence.py" "$@"; }

ce_run_url() {
    if [ -n "${GITHUB_SERVER_URL:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ] && [ -n "${GITHUB_RUN_ID:-}" ]; then
        echo "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
    fi
}

ce_tool_version() {
    case "$1" in
        gradle)
            sed -n 's/^distributionUrl=.*gradle-\(.*\)-[a-z]*\.zip$/\1/p' \
                "$CE_REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null
            ;;
        jest)
            jq -r '.packages["node_modules/jest"].version // empty' \
                "$CE_REPO_ROOT/ontrack-web-core/package-lock.json" 2>/dev/null
            ;;
        playwright)
            jq -r '.packages["node_modules/@playwright/test"].version // empty' \
                "$CE_REPO_ROOT/ontrack-web-tests/package-lock.json" 2>/dev/null
            ;;
        trivy)
            trivy version --format json 2>/dev/null | jq -r '.Version // empty' 2>/dev/null
            ;;
    esac
}

# The value following FLAG in the arguments, or nothing.
ce_flag_value() {
    local flag="$1"
    shift
    while [ $# -gt 1 ]; do
        if [ "$1" = "$flag" ]; then
            printf '%s' "$2"
            return 0
        fi
        shift
    done
}

# The commit of the report's header. A build spelled out on the command line is not necessarily
# the commit of this run - a rescan runs from `main` on a released build - so it is asked of the
# instance. Otherwise the CLI found the build by $GITHUB_SHA. A commit that cannot be read leaves
# the evidence intact: the header says so.
ce_commit() {
    local project="$1" branch="$2" build="$3" answer commit=""
    if [ -n "${CI_EVIDENCE_COMMIT:-}" ]; then
        echo "$CI_EVIDENCE_COMMIT"
        return 0
    fi
    if [ -z "$build" ]; then
        echo "${GITHUB_SHA:-unknown}"
        return 0
    fi
    # shellcheck disable=SC2016  # GraphQL variables, not shell ones
    answer="$(ce_cli graphql \
        --query 'query($project: String!, $branch: String!, $name: String!) { builds(project: $project, branch: $branch, name: $name) { gitCommitProperty { value } } }' \
        --var "project=$project" --var "branch=$branch" --var "name=$build" 2>/dev/null)"
    commit="$(jq -r '.builds[0].gitCommitProperty.value.commit // empty' <<< "$answer" 2>/dev/null)"
    if [ -z "$commit" ]; then
        echo "::warning title=$CE_STAMP evidence::Could not read the commit of build $build." >&2
    fi
    echo "${commit:-unknown}"
}

# Whether the validation says FAILED by itself: with --status, or with no result to count. Only such
# a validation may go without a report: it is the bare FAILED of a job that never got that far.
ce_says_failed() {
    [ "$(ce_flag_value --status "$@")" = "FAILED" ] && return 0
    local arg
    for arg in "$@"; do
        [ "$arg" = "--fail-when-no-results" ] && return 0
    done
    return 1
}

ce_gap() {
    CE_GAP=true
    echo "::error title=$CE_STAMP evidence::$*"
}

# Prepares the evidence of a kind, adding what could be made to CE_FILES. Nothing is a failure
# here but a gap: the validation is sent whatever comes of this.
ce_prepare_junit() {
    local pattern="$1" zip="$2/$CE_STAMP-junit.zip" html="$2/$CE_STAMP-report.html" files
    shift 2
    # No report is for ce_run to judge. Without Python there is no telling, and no evidence either:
    # the report must be assumed to exist.
    if ! files="$(ce_py match "$pattern" 2>/dev/null)"; then
        ce_gap "Could not look for the JUnit reports."
        return 0
    fi
    [ -n "$files" ] || return 0
    ce_py zip "$pattern" --output "$zip" || ce_gap "Could not bundle the JUnit reports."
    local -a raws=()
    [ -s "$zip" ] && raws=(--raw "$zip")
    ce_py render junit "$pattern" "$@" "${raws[@]+"${raws[@]}"}" --output "$html" \
        || ce_gap "Could not render the JUnit reports."
    ce_keep "$zip"
    ce_keep "$html"
}

# A report the validation was computed from, under its stable name, and its HTML. An empty one is a
# gap: the CLI would refuse it.
ce_prepare_report() {
    local kind="$1" report="$2" raw="$4/$CE_STAMP-$3" html="$4/$CE_STAMP-report.html"
    shift 4
    if [ -z "$report" ] || [ ! -e "$report" ]; then
        return 0
    fi
    cp "$report" "$raw" || ce_gap "Could not copy $report."
    ce_keep "$raw"
    local -a raws=()
    [ -s "$raw" ] && raws=(--raw "$raw")
    ce_py render "$kind" "$report" "$@" "${raws[@]+"${raws[@]}"}" --output "$html" \
        || ce_gap "Could not render $report."
    ce_keep "$html"
}

# The findings and the manifest of the private report, and an HTML built from those two only:
# security/dast/README.md says why nothing else may reach it.
ce_prepare_dast() {
    local findings="$1" manifest="$2"
    local raw="$3/$CE_STAMP-findings.json" kept="$3/$CE_STAMP-manifest.json" html="$3/$CE_STAMP-report.html"
    shift 3
    if [ -z "$findings" ] || [ ! -e "$findings" ]; then
        return 0
    fi
    cp "$findings" "$raw" || ce_gap "Could not copy the findings."
    ce_keep "$raw"
    local -a extra=()
    [ -s "$raw" ] && extra+=(--raw "$raw")
    if [ -n "$manifest" ] && [ -s "$manifest" ]; then
        cp "$manifest" "$kept" || ce_gap "Could not copy the manifest."
    fi
    ce_keep "$kept"
    [ -s "$kept" ] && extra+=(--raw "$kept" --manifest "$kept")
    ce_py render dast "$findings" "$@" "${extra[@]+"${extra[@]}"}" --output "$html" \
        || ce_gap "Could not render the findings."
    ce_keep "$html"
}

# Only a file that exists and is not empty reaches the CLI: it checks them all before it creates
# the validation run, so any other would lose the stamp altogether.
ce_keep() {
    if [ -s "$1" ]; then
        CE_FILES+=("$1")
    else
        ce_gap "No $(basename "$1") to attach."
    fi
}

ce_run() {
    local kind="$1"
    CE_STAMP="$2"
    shift 2

    local -a inputs=() args=()
    local tool="" version=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --tool) tool="${2:-}"; shift 2 ;;
            --tool-version) version="${2:-}"; shift 2 ;;
            --) shift; args=("$@"); break ;;
            *) inputs+=("$1"); shift ;;
        esac
    done
    [ "${#args[@]}" -gt 0 ] || { ce_fail "No validation to send"; return 1; }

    CE_GAP=false
    CE_FILES=()
    local dir="${CI_EVIDENCE_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/ci-evidence}/$CE_STAMP"
    rm -rf "$dir"
    mkdir -p "$dir" || ce_gap "Could not create $dir."

    local project branch build commit run_url
    project="$(ce_flag_value --project "${args[@]}")"
    branch="$(ce_flag_value --branch "${args[@]}")"
    build="$(ce_flag_value --build "${args[@]}")"
    commit="$(ce_commit "$project" "$branch" "$build")"
    run_url="$(ce_run_url)"
    local -a header=(--stamp "$CE_STAMP" --build "${build:-${YONTRACK_BUILD_NAME:-unknown}}" --commit "$commit" --run-url "$run_url")

    case "$kind" in
        junit) ce_prepare_junit "${inputs[0]:-}" "$dir" "${header[@]}" ;;
        trivy) tool=trivy; ce_prepare_report trivy "${inputs[0]:-}" trivy.json "$dir" "${header[@]}" ;;
        findings) ce_prepare_report findings "${inputs[0]:-}" findings.json "$dir" "${header[@]}" ;;
        dast) ce_prepare_dast "${inputs[0]:-}" "${inputs[1]:-}" "$dir" "${header[@]}" ;;
        search-perf) ce_prepare_report search-perf "${inputs[0]:-}" report.json "$dir" "${header[@]}" ;;
    esac

    local -a flags=()
    if [ "${#CE_FILES[@]}" -gt 0 ]; then
        local file
        for file in "${CE_FILES[@]}"; do
            flags+=(--evidence "$file")
        done
        version="${version:-$(ce_tool_version "$tool")}"
        [ -n "$tool" ] && flags+=(--evidence-tool "$tool")
        [ -n "$version" ] && flags+=(--evidence-tool-version "$version")
        [ -n "$run_url" ] && flags+=(--evidence-source-url "$run_url")
        [ -n "$tool" ] || ce_gap "No tool to record on the evidence."
        [ -n "$version" ] || ce_gap "No version of $tool to record on the evidence."
        [ -n "$run_url" ] || ce_gap "No workflow run to record on the evidence."
    elif [ "$CE_GAP" != "true" ] && ! ce_says_failed "${args[@]}"; then
        ce_gap "No report behind a validation which does not say FAILED."
    fi

    # The stamp first, always. A gap only says so afterwards.
    ce_cli "${args[@]}" "${flags[@]+"${flags[@]}"}"
    local rc=$?
    if [ "$rc" -ne 0 ]; then
        return "$rc"
    fi
    if [ "$CE_GAP" = "true" ]; then
        return 1
    fi
}

ce_main() {
    local kind="${1:-}" stamp="${2:-}"
    case "$kind" in
        junit|trivy|findings|dast|search-perf) ;;
        *) ce_fail "Usage: $0 junit|trivy|findings|dast|search-perf STAMP INPUT... [--tool T] [--tool-version V] -- CLI_ARGS..."; return 1 ;;
    esac
    [ -n "$stamp" ] || { ce_fail "No stamp"; return 1; }
    ce_run "$@"
}

if [ -z "${CI_EVIDENCE_LIB_ONLY:-}" ]; then
    ce_main "$@"
    exit $?
fi
