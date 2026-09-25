#!/usr/bin/env bash
#
# Records the SEARCH.PERFORMANCE validation stamp from the report of `./gradlew searchPerfTest`
# (#1887).
#
# Called by .github/workflows/search-perf.yml. It lives here rather than inline in the workflow
# for the reason scripts/coverage-validate.sh gives: which build is measured, and what the stamp
# says for each way the run can end, are the whole judgement of the issue - testable here
# (scripts/search-perf-validate-test.sh), untestable in workflow YAML.
#
# Usage:
#
#   scripts/search-perf-validate.sh resolve PROJECT BRANCH [REPO]
#
#       The Yontrack build to measure and report on. Walks the history of REPO (default: the
#       current directory) back from HEAD, and stops at the first commit that has a build on
#       PROJECT/BRANCH. Writes `commit`, `build` and `version` to $GITHUB_OUTPUT when it is set,
#       and prints them. Fails when none of the last $SEARCH_PERF_MAX_COMMITS commits (default 30)
#       has a build.
#
#   scripts/search-perf-validate.sh validate REPORT PROJECT BRANCH BUILD [-- EXTRA...]
#
#       Posts SEARCH.PERFORMANCE on PROJECT/BRANCH/BUILD from the JSON report at REPORT. EXTRA is
#       passed through to the `yontrack validate` call: the run time and $YONTRACK_RUN_INFO.
#       Exits 0 when it posted PASSED, 1 when it posted FAILED or could not post at all - so the
#       step, and the workflow run, are red exactly when the stamp is.
#
# Environment:
#   SEARCH_PERF_RUN_URL       the workflow run; it goes in every description.
#   SEARCH_PERF_MAX_COMMITS   how far back `resolve` looks (default 30).
#   SEARCH_PERF_CLI           the CLI to call (default: `yontrack`). Only the tests set it.
#
# Which build
# -----------
# "The build of the checked-out commit" - but the head of `v6` is often a commit with no build:
# a `Merge main into v6 [skip ci]`, a docs commit under `[skip ci]`, or a push whose CI has not
# registered its build yet. Failing then would lose the night for nothing. So `resolve` walks back
# to the newest commit that has one, and the workflow checks THAT commit out before measuring:
# the commit measured and the commit of the build reported on are always the same one. What it
# skipped over is `[skip ci]` by convention, which is to say documentation.
#
# The search is by commit on the branch, not on the whole project: a commit pushed on a working
# branch and on `v6` has a build on each, and the stamp belongs on `v6`'s.
#
# What is sent
# ------------
# searchPerfTest writes its report whatever happens - `SearchPerf.main` catches everything, puts
# it in `details.failures`, writes the report and only then exits 1 - so the report is the one
# source, and the Gradle outcome is not needed:
#
#   * no failure: PASSED, the five figures as metrics. `metrics` has no notion of pass or fail
#     (MetricsValidationDataType.computeStatus returns null, the run falls back to PASSED), so no
#     `--status` is sent. A p95 over its budget is not a failure - the test only fails past ten
#     times the budget, so that a noisy runner does not turn it red - and the description says
#     which ones are over;
#   * a failed EXPLAIN assertion: FAILED, the offending scenario, query, statement and reason in
#     the description. It comes first because it is the deterministic regression: an index no
#     longer used;
#   * any other failure - a p95 past its ceiling, a rebuild error, an exception: FAILED with the
#     failures, as the test words them, in the description;
#   * no report, or one without the five figures: FAILED, saying so. The test did not get as far
#     as measuring - the stack did not come up, the build did not compile.
#
# A FAILED stamp carries no figure, as COVERAGE's do not: a failed run's figures are in the
# report, which the workflow keeps as an artefact. And a stamp is always sent, for the reason
# coverage-validate.sh gives: a night with no SEARCH.PERFORMANCE at all is a hole in the history
# that nothing explains, and this stamp gates nothing.
#
# The description is cut to 500 characters: VALIDATION_RUN_STATUSES.DESCRIPTION is a
# VARCHAR(500), and a longer one would lose the stamp altogether.

set -uo pipefail

SP_METRICS=(palette_p95 results_p95 commit_lookup_p95 exact_build_p95 rebuild_seconds)
SP_STAMP=SEARCH.PERFORMANCE
SP_DESCRIPTION_MAX=500

sp_fail() { echo "ERROR: $*" >&2; return 1; }

sp_cli() { "${SEARCH_PERF_CLI:-yontrack}" "$@"; }

sp_output() {
    echo "$1=$2"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "$1=$2" >> "$GITHUB_OUTPUT"
    fi
}

# ---------------------------------------------------------------------------------------------
# resolve
# ---------------------------------------------------------------------------------------------

sp_resolve() {
    local project="${1:-}" branch="${2:-}" repo="${3:-.}"
    [ -n "$project" ] || { sp_fail "No project"; return 1; }
    [ -n "$branch" ] || { sp_fail "No branch"; return 1; }
    local max="${SEARCH_PERF_MAX_COMMITS:-30}"

    local commits
    commits="$(git -C "$repo" rev-list --max-count="$max" HEAD)" \
        || { sp_fail "Cannot read the history of $repo"; return 1; }

    local commit json build version head skipped=0
    head="$(head -n 1 <<< "$commits")"
    for commit in $commits; do
        # --accept-not-found: no build prints nothing and succeeds. Anything else failing is the
        # instance or the credentials, and must not read as "no build here, try the next one".
        json="$(sp_cli build search --project "$project" --branch "$branch" --commit "$commit" \
            --count 1 --output json --accept-not-found)" \
            || { sp_fail "Cannot search the builds of $project/$branch"; return 1; }
        build="$(jq -r '.Name // empty' <<< "${json:-null}" 2>/dev/null)"
        if [ -n "$build" ]; then
            version="$(jq -r '.DisplayName // empty' <<< "$json")"
            if [ "$commit" != "$head" ]; then
                echo "::notice title=SEARCH.PERFORMANCE::The head of $branch ($head) has no Yontrack build; measuring $commit instead, the newest commit that has one ($skipped skipped)."
            fi
            sp_output commit "$commit"
            sp_output build "$build"
            sp_output version "${version:-$build}"
            return 0
        fi
        skipped=$((skipped + 1))
    done
    sp_fail "None of the last $max commits of $branch has a build in $project/$branch: nothing to report on."
}

# ---------------------------------------------------------------------------------------------
# validate
# ---------------------------------------------------------------------------------------------

sp_validate() {
    local report="${1:-}" project="${2:-}" branch="${3:-}" build="${4:-}"
    [ -n "$report" ] || { sp_fail "No report"; return 1; }
    [ -n "$project" ] || { sp_fail "No project"; return 1; }
    [ -n "$branch" ] || { sp_fail "No branch"; return 1; }
    [ -n "$build" ] || { sp_fail "No build"; return 1; }
    shift 4
    [ "${1:-}" = "--" ] && shift
    local -a extra=("$@")
    local -a target=(--project "$project" --branch "$branch" --build "$build")
    local run_url="${SEARCH_PERF_RUN_URL:-}"
    local see="See ${run_url:-the run}, artefact search-perf-report."

    local json=""
    if [ -f "$report" ] && jq -e 'type == "object"' "$report" > /dev/null 2>&1; then
        json="$(cat "$report")"
    fi
    if [ -z "$json" ]; then
        sp_failed "${target[@]}" "searchPerfTest wrote no report: it did not get as far as measuring. $see" \
            "${extra[@]+"${extra[@]}"}"
        return 1
    fi

    # A failed EXPLAIN assertion first: the offending query, and why.
    local explain_count
    explain_count="$(jq '[.explain // [] | .[] | select(.passed == false)] | length' <<< "$json")"
    if [ "$explain_count" -gt 0 ]; then
        local first more=""
        first="$(jq -r --arg q "'" '[.explain[] | select(.passed == false)][0]
            | "\(.scenario) \($q)\(.query)\($q) (\(.statement)): \(.reason // "no reason given")"' <<< "$json")"
        if [ "$explain_count" -gt 1 ]; then
            more=" (and $((explain_count - 1)) more)"
        fi
        sp_failed "${target[@]}" "EXPLAIN assertion failed$more: $first. $see" "${extra[@]+"${extra[@]}"}"
        return 1
    fi

    # Any other failure, as the test words it.
    local failures
    failures="$(jq -r '.details.failures // [] | join("; ")' <<< "$json")"
    if [ -n "$failures" ]; then
        sp_failed "${target[@]}" "searchPerfTest failed: $failures. $see" "${extra[@]+"${extra[@]}"}"
        return 1
    fi

    # All five figures, or none: a partial set would read as a stamp that measured less.
    local metric value missing=""
    local -a metrics=()
    for metric in "${SP_METRICS[@]}"; do
        value="$(jq -r --arg key "$metric" '.[$key] | numbers' <<< "$json")"
        if [ -z "$value" ]; then
            missing="$missing $metric"
        else
            metrics+=(--metric "$metric=$value")
        fi
    done
    if [ -n "$missing" ]; then
        sp_failed "${target[@]}" "The searchPerfTest report has no figure for:$missing. $see" \
            "${extra[@]+"${extra[@]}"}"
        return 1
    fi

    # Over budget: recorded, not failed.
    local over description
    over="$(jq -r '. as $r | ($r.details.budgets // {}) as $b | $r.details.over_budget // []
        | map("\(.) \($r[.]) ms (budget \($b[.] // "?"))") | join(", ")' <<< "$json")"
    if [ -n "$over" ]; then
        description="All EXPLAIN assertions passed. Over budget: $over. $see"
    else
        description="All EXPLAIN assertions passed, every p95 within budget. $see"
    fi
    sp_cli validate "${target[@]}" --validation "$SP_STAMP" \
        --description "$(sp_truncate "$description")" \
        metrics "${metrics[@]}" "${extra[@]+"${extra[@]}"}" \
        || { sp_fail "Could not post $SP_STAMP"; return 1; }
}

# sp_failed --project P --branch B --build N DESCRIPTION [EXTRA...]
#
# No subcommand: a validation with a status and no data. The run-info flags are declared on the
# `validate` command itself, so they are accepted there.
sp_failed() {
    local -a target=("$1" "$2" "$3" "$4" "$5" "$6")
    local description="$7"
    shift 7
    sp_cli validate "${target[@]}" --validation "$SP_STAMP" --status FAILED \
        --description "$(sp_truncate "$description")" "$@" \
        || sp_fail "Could not post $SP_STAMP"
    echo "::error title=SEARCH.PERFORMANCE::$description"
}

sp_truncate() {
    local text="$1"
    if [ "${#text}" -gt "$SP_DESCRIPTION_MAX" ]; then
        printf '%s...' "${text:0:$((SP_DESCRIPTION_MAX - 3))}"
    else
        printf '%s' "$text"
    fi
}

sp_main() {
    local command="${1:-}"
    shift || true
    case "$command" in
        resolve) sp_resolve "$@" ;;
        validate) sp_validate "$@" ;;
        *) sp_fail "Usage: $0 resolve PROJECT BRANCH [REPO] | validate REPORT PROJECT BRANCH BUILD [-- EXTRA...]" ;;
    esac
}

if [ -z "${SEARCH_PERF_VALIDATE_LIB_ONLY:-}" ]; then
    sp_main "$@"
    exit $?
fi
