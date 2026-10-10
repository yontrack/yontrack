#!/usr/bin/env bash
#
# Tests for security-rescan.sh. `gh` is stubbed on the PATH and `srs_graphql` is replaced by a
# recording stub, so nothing here calls GitHub or a Yontrack instance: the stubs answer from
# canned JSON and record every call they receive, which is what the assertions read. `jq` and
# `yq` are the real ones - they are what is being tested.
#
# Usage: scripts/security-rescan-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load security-rescan.sh as a library: defines the functions, runs nothing.
SECURITY_RESCAN_LIB_ONLY=1
export SECURITY_RESCAN_LIB_ONLY
# shellcheck source=security-rescan.sh
source "$SCRIPT_DIR/security-rescan.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/security-rescan-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ===========================================================================
# Fixtures - the shape the `builds` GraphQL query answers with
# ===========================================================================

# Two minors, several patches each, plus the noise a real answer carries: a build whose display
# name is not a version (a RELEASE-promoted build with no release property shows its own name),
# and an older minor that must be left out.
cat > "$WORK/builds.json" <<'JSON'
[
  {"id": 91, "name": "20260913104500-99", "displayName": "5.4.1",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 90, "name": "20260912160000-98", "displayName": "5.4.0",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 85, "name": "20260905180000-90", "displayName": "5.3.1",
   "branch": {"name": "release-5.3", "project": {"name": "yontrack"}}},
  {"id": 84, "name": "20260903180000-88", "displayName": "5.3.0",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 80, "name": "20260826150000-80", "displayName": "5.2.2",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 79, "name": "20260825150000-79", "displayName": "20260825150000-79",
   "branch": {"name": "main", "project": {"name": "yontrack"}}}
]
JSON

# A single minor: only one build to rescan.
cat > "$WORK/builds-one-minor.json" <<'JSON'
[
  {"id": 91, "name": "20260913104500-99", "displayName": "5.4.1",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 90, "name": "20260912160000-98", "displayName": "5.4.0",
   "branch": {"name": "main", "project": {"name": "yontrack"}}}
]
JSON

# Nothing has ever been released.
echo '[]' > "$WORK/builds-none.json"

# Nothing released *under a version*: every promoted build shows its own name.
cat > "$WORK/builds-unversioned.json" <<'JSON'
[
  {"id": 79, "name": "20260825150000-79", "displayName": "20260825150000-79",
   "branch": {"name": "main", "project": {"name": "yontrack"}}}
]
JSON

# Patches out of id order, and a two-digit patch: the pick is by version, never by position.
cat > "$WORK/builds-unordered.json" <<'JSON'
[
  {"id": 50, "name": "b-5-3-2", "displayName": "5.3.2",
   "branch": {"name": "release-5.3", "project": {"name": "yontrack"}}},
  {"id": 99, "name": "b-5-4-9", "displayName": "5.4.9",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 98, "name": "b-5-4-10", "displayName": "5.4.10",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 40, "name": "b-5-3-10", "displayName": "5.3.10",
   "branch": {"name": "release-5.3", "project": {"name": "yontrack"}}}
]
JSON

# Across majors: 6.0 and 5.4 are the two most recent minors.
cat > "$WORK/builds-majors.json" <<'JSON'
[
  {"id": 99, "name": "b-6-0-0", "displayName": "6.0.0",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 91, "name": "b-5-4-1", "displayName": "5.4.1",
   "branch": {"name": "main", "project": {"name": "yontrack"}}},
  {"id": 84, "name": "b-5-3-0", "displayName": "5.3.0",
   "branch": {"name": "main", "project": {"name": "yontrack"}}}
]
JSON

: > "$WORK/nothing.json"
echo '[{"id": 1,' > "$WORK/broken.json"
echo '{"message": "Not found", "status": "404"}' > "$WORK/error-object.json"

# ===========================================================================
# select
# ===========================================================================

out="$(srs_select "$WORK/builds.json")"; rc=$?
assert_eq "0" "$rc" "select: succeeds on a normal answer"
assert_eq "2" "$(printf '%s' "$out" | jq 'length')" "select: keeps the current minor and the previous one, and nothing older"
assert_eq "5.4.1" "$(printf '%s' "$out" | jq -r '.[0].version')" "select: the newest target comes first"
assert_eq "5.3.1" "$(printf '%s' "$out" | jq -r '.[1].version')" "select: the previous minor comes second"
assert_eq "20260913104500-99" "$(printf '%s' "$out" | jq -r '.[0].build')" "select: carries the build name, which is what a validation is reported on"
assert_eq "release-5.3" "$(printf '%s' "$out" | jq -r '.[1].branch')" "select: a released build is taken wherever it lives, release branch included"
assert_eq "yontrack" "$(printf '%s' "$out" | jq -r '.[1].project')" "select: carries the project"
assert_eq "" "$(printf '%s' "$out" | jq -r '.[] | select(.version | startswith("5.2"))')" "select: leaves out the minors nobody is asked to run"

out="$(srs_select "$WORK/builds-unordered.json")"; rc=$?
assert_eq "5.4.10" "$(printf '%s' "$out" | jq -r '.[0].version')" "select: compares patches as numbers, not as text"
assert_eq "5.3.10" "$(printf '%s' "$out" | jq -r '.[1].version')" "select: picks the highest patch of the previous minor too"

out="$(srs_select "$WORK/builds-majors.json")"; rc=$?
assert_eq "6.0.0" "$(printf '%s' "$out" | jq -r '.[0].version')" "select: a new major is the current minor"
assert_eq "5.4.1" "$(printf '%s' "$out" | jq -r '.[1].version')" "select: the previous minor can be under the previous major"

out="$(srs_select "$WORK/builds-one-minor.json")"; rc=$?
assert_eq "1" "$(printf '%s' "$out" | jq 'length')" "select: one minor gives one target"
assert_eq "5.4.1" "$(printf '%s' "$out" | jq -r '.[0].version')" "select: that target is the highest patch of it"

out="$(srs_select "$WORK/builds-none.json")"; rc=$?
assert_eq "0" "$rc" "select: no released build at all is not an error"
assert_eq "0" "$(printf '%s' "$out" | jq 'length')" "select: and gives no target"

out="$(srs_select "$WORK/builds-unversioned.json")"; rc=$?
assert_eq "0" "$rc" "select: a promoted build with no version is not an error"
assert_eq "0" "$(printf '%s' "$out" | jq 'length')" "select: but is no target either - there is no image tag to scan"

out="$(srs_select < "$WORK/builds.json")"; rc=$?
assert_eq "0" "$rc" "select: reads the builds from stdin when no file is given"
assert_eq "5.4.1" "$(printf '%s' "$out" | jq -r '.[0].version')" "select: stdin is read like a file"

out="$(srs_select "$WORK/nothing.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "select: an empty answer is an error, not an empty list"

out="$(srs_select "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "select: an answer that is not JSON is an error"

out="$(srs_select "$WORK/error-object.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "select: an API error object is an error, not an empty list"

out="$(srs_select "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "select: a missing file is an error"

# ===========================================================================
# targets - against a stubbed srs_graphql
# ===========================================================================

# Replaces the real GraphQL client: records the query and its variables, and answers with the
# canned payload named by STUB_GRAPHQL_ANSWER, or fails when STUB_GRAPHQL_FAIL is set.
srs_graphql() {
    printf '%s\n%s\n' "$1" "${2:-}" >> "$WORK/queries"
    if [ -n "${STUB_GRAPHQL_FAIL:-}" ]; then
        echo "ERROR: Yontrack answered HTTP 401" >&2
        return 1
    fi
    cat "$STUB_GRAPHQL_ANSWER"
}

setup_stubs() {
    rm -f "$WORK/calls" "$WORK/queries" "$WORK/github_output" "$WORK/summary"
    : > "$WORK/calls"
    : > "$WORK/queries"
    : > "$WORK/github_output"
    : > "$WORK/summary"
    STUB_CALLS="$WORK/calls"
    STUB_GH_FAIL=""
    STUB_GH_OPEN=""
    STUB_GH_RESOLVED=""
    STUB_GRAPHQL_FAIL=""
    export STUB_CALLS STUB_GH_FAIL STUB_GH_OPEN STUB_GH_RESOLVED STUB_GRAPHQL_FAIL
}
calls() { cat "$WORK/calls"; }
queries() { cat "$WORK/queries"; }

wrap_builds() {
    jq -c '{data: {builds: .}}' "$1" > "$WORK/answer.json"
    STUB_GRAPHQL_ANSWER="$WORK/answer.json"
}

setup_stubs
wrap_builds "$WORK/builds.json"
out="$(GITHUB_OUTPUT="$WORK/github_output" srs_targets yontrack 2>&1)"; rc=$?
assert_eq "0" "$rc" "targets: succeeds"
assert_contains "$(queries)" "buildProjectFilter" "targets: searches the whole project, not one branch"
assert_contains "$(queries)" "RELEASE" "targets: resolves the builds by promotion"
assert_not_contains "$(queries)" "release/" "targets: never resolves them by branch name"
outputs="$(cat "$WORK/github_output")"
assert_contains "$outputs" "count=2" "targets: reports how many builds are to be rescanned"
assert_contains "$outputs" "newest_version=5.4.1" "targets: names the newest target, which carries SECURITY.SECRETS"
assert_contains "$outputs" "newest_build=20260913104500-99" "targets: as a build name"
assert_contains "$outputs" "newest_branch=main" "targets: with its branch"
assert_contains "$outputs" "newest_project=yontrack" "targets: and its project"
targets_json="$(grep '^targets=' "$WORK/github_output" | cut -d= -f2-)"
assert_eq "2" "$(printf '%s' "$targets_json" | jq 'length')" "targets: writes the matrix as one JSON line"
assert_eq "1" "$(grep -c '^targets=' "$WORK/github_output")" "targets: on a single line, as a matrix expression needs"
assert_contains "$out" "5.4.1" "targets: says in the log what it is about to rescan"
assert_contains "$out" "5.3.1" "targets: for every target"

setup_stubs
wrap_builds "$WORK/builds-none.json"
out="$(GITHUB_OUTPUT="$WORK/github_output" srs_targets yontrack 2>&1)"; rc=$?
assert_eq "0" "$rc" "targets: no released build exits successfully - there is nothing to rescan"
assert_contains "$(cat "$WORK/github_output")" "count=0" "targets: and says so"

setup_stubs
STUB_GRAPHQL_FAIL=1
wrap_builds "$WORK/builds.json"
out="$(GITHUB_OUTPUT="$WORK/github_output" srs_targets yontrack 2>&1)"; rc=$?
assert_eq "1" "$rc" "targets: a Yontrack that cannot be reached fails, it does not read as no release"
assert_eq "" "$(cat "$WORK/github_output")" "targets: and writes no count"

setup_stubs
out="$(srs_targets "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "targets: refuses to run without a project"

# ===========================================================================
# stamp-config - read from .yontrack/ci.yaml, so the rescan and CI cannot disagree
# ===========================================================================

setup_stubs
FINDINGS_TYPE="net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType"
out="$(srs_stamp_config SECURITY.IMAGE.BACKEND)"; rc=$?
assert_eq "0" "$rc" "stamp-config: reads a security-findings stamp out of the CI configuration"
assert_eq "$FINDINGS_TYPE" \
    "$(printf '%s' "$out" | jq -r '.dataType')" "stamp-config: resolves the security-findings alias to the data type behind it"
assert_eq "CRITICAL" "$(printf '%s' "$out" | jq -r '.dataTypeConfig.failedLevel')" \
    "stamp-config: carries the thresholds CI declares, so both report against the same ones"

out="$(srs_stamp_config SECURITY.SECRETS)"; rc=$?
assert_eq "0" "$rc" "stamp-config: reads the secrets stamp"
assert_eq "$FINDINGS_TYPE" \
    "$(printf '%s' "$out" | jq -r '.dataType')" "stamp-config: the secrets stamp takes findings"
assert_eq "CRITICAL|1" "$(printf '%s' "$out" | jq -r '.dataTypeConfig | "\(.failedLevel)|\(.failedValue)"')" \
    "stamp-config: one open alert - a CRITICAL finding - is a failure"

COVERAGE_TYPE="net.nemerosa.ontrack.extension.general.validation.MetricsValidationDataType"
out="$(srs_stamp_config COVERAGE.UNIT)"; rc=$?
assert_eq "$COVERAGE_TYPE" "$(printf '%s' "$out" | jq -r '.dataType')" "stamp-config: the other aliases still resolve"

out="$(srs_stamp_config NO.SUCH.STAMP 2>&1)"; rc=$?
assert_eq "1" "$rc" "stamp-config: a stamp the CI configuration does not declare is an error"

# ===========================================================================
# setup-stamp - against the stubbed srs_graphql
# ===========================================================================

echo '{"data": {"setupValidationStamp": {"errors": null, "validationStamp": {"id": 1}}}}' \
    > "$WORK/setup-ok.json"
echo '{"data": {"setupValidationStamp": {"errors": [{"message": "Branch not found"}]}}}' \
    > "$WORK/setup-user-error.json"

setup_stubs
STUB_GRAPHQL_ANSWER="$WORK/setup-ok.json"
out="$(srs_setup_stamp yontrack release-5.3 SECURITY.SECRETS 2>&1)"; rc=$?
assert_eq "0" "$rc" "setup-stamp: succeeds"
assert_contains "$(queries)" "setupValidationStamp" "setup-stamp: creates the stamp if the branch has none, updates it otherwise"
assert_contains "$(queries)" "release-5.3" "setup-stamp: on the branch of the build being rescanned"
assert_contains "$(queries)" "FindingsValidationDataType" "setup-stamp: with the data type, so a branch whose CI config predates the stamp still gets the thresholds"
assert_contains "$(queries)" "failedLevel" "setup-stamp: and its configuration"

setup_stubs
STUB_GRAPHQL_ANSWER="$WORK/setup-user-error.json"
out="$(srs_setup_stamp yontrack release-5.3 SECURITY.SECRETS 2>&1)"; rc=$?
assert_eq "1" "$rc" "setup-stamp: a mutation error fails rather than leaving a stamp with no data type behind"
assert_contains "$out" "Branch not found" "setup-stamp: and says what Yontrack answered"

setup_stubs
STUB_GRAPHQL_ANSWER="$WORK/setup-ok.json"
out="$(srs_setup_stamp yontrack "" SECURITY.SECRETS 2>&1)"; rc=$?
assert_eq "1" "$rc" "setup-stamp: refuses to run without a branch"

# ===========================================================================
# secret-findings
# ===========================================================================

# The open and resolved secret-scanning alerts. The fixtures carry the secret itself, as an
# answer without `hide_secret` would, precisely so that a test can prove it never goes anywhere.
cat > "$WORK/alerts-open.json" <<'JSON'
[
  {"number": 3, "state": "open", "secret_type": "github_personal_access_token",
   "secret_type_display_name": "GitHub Personal Access Token",
   "secret": "ghp_SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/3"},
  {"number": 2, "state": "open", "secret_type": "slack_api_token",
   "secret": "xoxb-SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/2"}
]
[
  {"number": 1, "state": "open", "secret_type": "aws_access_key_id",
   "secret_type_display_name": "Amazon AWS Access Key ID",
   "secret": "AKIASUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/1"}
]
JSON

# Resolved: as a false positive and as used in tests - accepted - and as revoked, which is a fix
# and no longer a finding at all.
cat > "$WORK/alerts-resolved.json" <<'JSON'
[
  {"number": 7, "state": "resolved", "resolution": "false_positive", "resolution_comment": "A sample in the docs",
   "secret_type": "slack_api_token", "secret_type_display_name": "Slack API Token",
   "secret": "xoxb-SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/7"},
  {"number": 6, "state": "resolved", "resolution": "used_in_tests", "resolution_comment": null,
   "secret_type": "aws_access_key_id", "secret_type_display_name": "Amazon AWS Access Key ID",
   "secret": "AKIASUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/6"},
  {"number": 5, "state": "resolved", "resolution": "revoked", "resolution_comment": "Rotated",
   "secret_type": "github_personal_access_token", "secret_type_display_name": "GitHub Personal Access Token",
   "secret": "ghp_SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/5"}
]
JSON

cat "$WORK/alerts-open.json" "$WORK/alerts-resolved.json" > "$WORK/alerts-all.json"
echo '[]' > "$WORK/alerts-empty.json"

report="$(srs_secret_findings "$WORK/alerts-all.json")"; rc=$?
assert_eq "0" "$rc" "secret-findings: succeeds"
assert_eq "github-secret-scanning|SECRETS" "$(printf '%s' "$report" | jq -r '"\(.scanner)|\(.kind)"')" \
    "secret-findings: the scanner and the kind"
assert_eq "5" "$(printf '%s' "$report" | jq '.findings | length')" \
    "secret-findings: every page, open and accepted, without the revoked alert"
assert_eq "github_personal_access_token|#3|CRITICAL|GitHub Personal Access Token" \
    "$(printf '%s' "$report" | jq -r '.findings[0] | "\(.externalId)|\(.location)|\(.severity)|\(.title)"')" \
    "secret-findings: the secret type, the alert number as location, CRITICAL - a leaked credential has no acceptable count"
assert_eq "slack_api_token" "$(printf '%s' "$report" | jq -r '.findings[1].title')" \
    "secret-findings: the type is the title when there is no display name"
assert_eq "https://github.com/yontrack/yontrack/security/secret-scanning/3" \
    "$(printf '%s' "$report" | jq -r '.findings[0].url')" "secret-findings: links to the alert"
assert_eq "null" "$(printf '%s' "$report" | jq -c '.findings[0].acceptance')" \
    "secret-findings: an open alert is not accepted"
assert_eq '{"statement":"false_positive: A sample in the docs","source":"GitHub secret scanning alert #7"}' \
    "$(printf '%s' "$report" | jq -c '.findings[3].acceptance')" \
    "secret-findings: a false positive is an acceptance, with its comment"
assert_eq '{"statement":"used_in_tests","source":"GitHub secret scanning alert #6"}' \
    "$(printf '%s' "$report" | jq -c '.findings[4].acceptance')" \
    "secret-findings: so is a secret used in tests, without comment"
assert_not_contains "$report" "SUPERSECRETVALUE" "secret-findings: never the value of a secret"
assert_eq "" "$(printf '%s' "$report" | jq -r '.findings[] | keys[] | select(IN("externalId","location","severity","title","url","acceptance") | not)')" \
    "secret-findings: no field the neutral format would reject"

report="$(srs_secret_findings < "$WORK/alerts-all.json")"; rc=$?
assert_eq "5" "$(printf '%s' "$report" | jq '.findings | length')" "secret-findings: reads stdin when no file is given"

report="$(srs_secret_findings "$WORK/alerts-empty.json")"; rc=$?
assert_eq "0" "$rc" "secret-findings: no alert is a report"
assert_eq "0" "$(printf '%s' "$report" | jq '.findings | length')" "secret-findings: with no finding"

out="$(srs_secret_findings "$WORK/nothing.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secret-findings: an empty answer is an error, not zero alerts"

out="$(srs_secret_findings "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secret-findings: an answer that is not JSON is an error, not zero alerts"

out="$(srs_secret_findings "$WORK/error-object.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secret-findings: an API error object is an error, not zero alerts"
assert_not_contains "$out" "SUPERSECRETVALUE" "secret-findings: and its error says nothing of the alerts"

out="$(srs_secret_findings "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secret-findings: a missing file is an error, not zero alerts"

# ===========================================================================
# secrets - against a stubbed gh
# ===========================================================================

# Answers the open alerts with STUB_GH_OPEN and the resolved ones with STUB_GH_RESOLVED, by the
# `state` of the query.
cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "gh $*" >> "$STUB_CALLS"
if [ -n "${STUB_GH_FAIL:-}" ]; then
    echo "gh: Not Found (HTTP 404)" >&2
    exit 1
fi
case "$*" in
    *state=open*) cat "$STUB_GH_OPEN" ;;
    *state=resolved*) cat "$STUB_GH_RESOLVED" ;;
esac
STUB
chmod +x "$WORK/bin/gh"

secrets_stubs() {
    setup_stubs
    STUB_GH_OPEN="$WORK/alerts-open.json"
    STUB_GH_RESOLVED="$WORK/alerts-resolved.json"
    export STUB_GH_OPEN STUB_GH_RESOLVED
    rm -f "$WORK/secrets.json"
}

secrets_stubs
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack "$WORK/secrets.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "secrets: succeeds"
assert_contains "$(calls)" "--paginate" "secrets: follows every page"
assert_contains "$(calls)" "repos/yontrack/yontrack/secret-scanning/alerts?state=open&hide_secret=true&per_page=100" \
    "secrets: the open alerts, without their secret, in the largest pages"
assert_contains "$(calls)" "repos/yontrack/yontrack/secret-scanning/alerts?state=resolved&hide_secret=true&per_page=100" \
    "secrets: and the resolved ones, which may be acceptances"
assert_eq "5" "$(jq '.findings | length' "$WORK/secrets.json")" "secrets: writes the report"
assert_eq "secrets=3
accepted=2" "$(cat "$WORK/github_output")" "secrets: writes the counts to GITHUB_OUTPUT"
assert_contains "$out" "secrets=3" "secrets: prints the counts in the log"
assert_not_contains "$out" "SUPERSECRETVALUE" "secrets: and prints nothing else about the alerts"
assert_not_contains "$out" "secret_type" "secrets: nor what kind of secret it is"

secrets_stubs
STUB_GH_OPEN="$WORK/alerts-empty.json"
STUB_GH_RESOLVED="$WORK/alerts-empty.json"
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack "$WORK/secrets.json" 2>&1)"; rc=$?
assert_eq "0" "$rc" "secrets: succeeds with no alert"
assert_eq "0" "$(jq '.findings | length' "$WORK/secrets.json")" "secrets: no alert is an empty report"
assert_contains "$(cat "$WORK/github_output")" "secrets=0" "secrets: no alert writes a zero"

secrets_stubs
STUB_GH_FAIL=1
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack "$WORK/secrets.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secrets: an API error fails - a token that cannot read the alerts must not read as none"
assert_eq "" "$(cat "$WORK/github_output")" "secrets: an API error writes no count"
assert_eq "false" "$([ -f "$WORK/secrets.json" ] && echo true || echo false)" "secrets: and no report"

secrets_stubs
out="$(PATH="$WORK/bin:$PATH" srs_secrets "" "$WORK/secrets.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secrets: refuses to run without a repository"

secrets_stubs
out="$(PATH="$WORK/bin:$PATH" srs_secrets yontrack/yontrack "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secrets: refuses to run without an output file"

report_tests
