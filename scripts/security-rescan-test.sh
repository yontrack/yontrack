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
    STUB_GH_PAGES="$WORK/alerts1.json $WORK/alerts2.json"
    STUB_GRAPHQL_FAIL=""
    export STUB_CALLS STUB_GH_FAIL STUB_GH_PAGES STUB_GRAPHQL_FAIL
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
out="$(srs_stamp_config SECURITY.IMAGE.BACKEND)"; rc=$?
assert_eq "0" "$rc" "stamp-config: reads a CHML stamp out of the CI configuration"
assert_eq "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType" \
    "$(printf '%s' "$out" | jq -r '.dataType')" "stamp-config: resolves the chml alias to the data type behind it"
assert_eq "CRITICAL" "$(printf '%s' "$out" | jq -r '.dataTypeConfig.failedLevel')" \
    "stamp-config: carries the thresholds CI declares, so both report against the same ones"

out="$(srs_stamp_config SECURITY.SECRETS)"; rc=$?
assert_eq "0" "$rc" "stamp-config: reads the secrets stamp"
assert_eq "net.nemerosa.ontrack.extension.general.validation.ThresholdNumberValidationDataType" \
    "$(printf '%s' "$out" | jq -r '.dataType')" "stamp-config: the secrets stamp counts a number"
assert_eq "0" "$(printf '%s' "$out" | jq -r '.dataTypeConfig.failureThreshold')" \
    "stamp-config: one open alert is a failure"
assert_eq "false" "$(printf '%s' "$out" | jq -r '.dataTypeConfig.okIfGreater')" \
    "stamp-config: and more is worse, not better"

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
assert_contains "$(queries)" "ThresholdNumberValidationDataType" "setup-stamp: with the data type, so a branch whose CI config predates the stamp still gets the thresholds"
assert_contains "$(queries)" "failureThreshold" "setup-stamp: and its configuration"

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
# count-secrets
# ===========================================================================

# One page of open secret-scanning alerts. Only the count is ever used; the fixtures carry the
# surrounding fields precisely so that a test can prove they are not printed.
cat > "$WORK/alerts1.json" <<'JSON'
[
  {"number": 3, "state": "open", "secret_type": "github_personal_access_token",
   "secret": "ghp_SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/3"},
  {"number": 2, "state": "open", "secret_type": "slack_api_token",
   "secret": "xoxb-SUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/2"}
]
JSON

cat > "$WORK/alerts2.json" <<'JSON'
[
  {"number": 1, "state": "open", "secret_type": "aws_access_key_id",
   "secret": "AKIASUPERSECRETVALUE", "html_url": "https://github.com/yontrack/yontrack/security/secret-scanning/1"}
]
JSON

cat "$WORK/alerts1.json" "$WORK/alerts2.json" > "$WORK/alerts-paginated.json"
echo '[]' > "$WORK/alerts-empty.json"

out="$(srs_count_secrets "$WORK/alerts1.json")"; rc=$?
assert_eq "0" "$rc" "count-secrets: succeeds on one page"
assert_eq "secrets=2" "$out" "count-secrets: counts the open alerts"

out="$(srs_count_secrets "$WORK/alerts-paginated.json")"; rc=$?
assert_eq "secrets=3" "$out" "count-secrets: adds up every page"

out="$(srs_count_secrets "$WORK/alerts-empty.json")"; rc=$?
assert_eq "0" "$rc" "count-secrets: succeeds when there is no open alert"
assert_eq "secrets=0" "$out" "count-secrets: no alert counts as zero"

out="$(srs_count_secrets "$WORK/alerts-paginated.json" 2>&1)"
assert_not_contains "$out" "SUPERSECRETVALUE" "count-secrets: never prints a secret"
assert_not_contains "$out" "secret_type" "count-secrets: nor what kind of secret it is"
assert_not_contains "$out" "security/secret-scanning" "count-secrets: nor where it is"

out="$(srs_count_secrets "$WORK/nothing.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count-secrets: an empty answer is an error, not zero alerts"

out="$(srs_count_secrets "$WORK/broken.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count-secrets: an answer that is not JSON is an error, not zero alerts"

out="$(srs_count_secrets "$WORK/error-object.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count-secrets: an API error object is an error, not zero alerts"

out="$(srs_count_secrets "$WORK/does-not-exist.json" 2>&1)"; rc=$?
assert_eq "1" "$rc" "count-secrets: a missing file is an error, not zero alerts"

# ===========================================================================
# secrets - against a stubbed gh
# ===========================================================================

cat > "$WORK/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
echo "gh $*" >> "$STUB_CALLS"
if [ -n "${STUB_GH_FAIL:-}" ]; then
    echo "gh: Not Found (HTTP 404)" >&2
    exit 1
fi
for page in $STUB_GH_PAGES; do
    cat "$page"
done
STUB
chmod +x "$WORK/bin/gh"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack 2>&1)"; rc=$?
assert_eq "0" "$rc" "secrets: succeeds"
assert_contains "$(calls)" "--paginate" "secrets: follows every page"
assert_contains "$(calls)" "repos/yontrack/yontrack/secret-scanning/alerts" "secrets: asks the secret-scanning alerts API"
assert_contains "$(calls)" "state=open" "secrets: counts only the open alerts, so a resolved one lowers the count"
assert_contains "$(calls)" "per_page=100" "secrets: asks for the largest pages"
assert_eq "secrets=3" "$(cat "$WORK/github_output")" "secrets: writes the count to GITHUB_OUTPUT"
assert_contains "$out" "secrets=3" "secrets: prints the count in the log"
assert_not_contains "$out" "SUPERSECRETVALUE" "secrets: and prints nothing else about the alerts"

setup_stubs
STUB_GH_PAGES="$WORK/alerts-empty.json"
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack 2>&1)"; rc=$?
assert_eq "0" "$rc" "secrets: succeeds with no alert"
assert_eq "secrets=0" "$(cat "$WORK/github_output")" "secrets: no alert writes a zero"

setup_stubs
STUB_GH_FAIL=1
out="$(PATH="$WORK/bin:$PATH" GITHUB_OUTPUT="$WORK/github_output" \
    srs_secrets yontrack/yontrack 2>&1)"; rc=$?
assert_eq "1" "$rc" "secrets: an API error fails - a token that cannot read the alerts must not read as none"
assert_eq "" "$(cat "$WORK/github_output")" "secrets: an API error writes no count"

setup_stubs
out="$(PATH="$WORK/bin:$PATH" srs_secrets "" 2>&1)"; rc=$?
assert_eq "1" "$rc" "secrets: refuses to run without a repository"

report_tests
