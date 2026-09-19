#!/usr/bin/env bash
#
# Tests for scripts/coverage-report.sh (#1821).
#
# Run by hand: ./scripts/coverage-report-test.sh
#
# Everything but the `report` command itself, which is a `jacococli` invocation over real class
# files and belongs to a CI run rather than to a unit test. What is tested here is the part that
# can be wrong silently: which artefact belongs to which test type, which sessions a set of
# artefacts adds up to, whether an incomplete set is caught, and what the denominator is.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

COVERAGE_REPORT_LIB_ONLY=1
# shellcheck source=scripts/coverage-report.sh
source "$SCRIPT_DIR/coverage-report.sh"

# An explicit template: a bare `mktemp -d` ignores $TMPDIR on macOS.
WORK="$(mktemp -d "${TMPDIR:-/tmp}/coverage-report-test.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

exec_file() {
    mkdir -p "$(dirname "$1")"
    printf 'not really an exec file' > "$1"
}

# A complete set of artefacts, laid out as actions/download-artifact leaves them: one directory
# per artefact, holding the paths the upload matched.
complete_artefacts() {
    local root="$1" shard module
    rm -rf "$root"
    for module in ontrack-model ontrack-service ontrack-extension-general; do
        exec_file "$root/coverage-unit/$module/build/jacoco/test.exec"
    done
    for shard in 1 2 3 4 5; do
        exec_file "$root/coverage-integration-$shard/ontrack-service/build/jacoco/integrationTest.exec"
        exec_file "$root/coverage-integration-$shard/ontrack-ui/build/jacoco/integrationTest.exec"
    done
    for shard in 1 2; do
        exec_file "$root/coverage-kdsl-$shard/build/jacoco/kdsl-$shard.exec"
    done
    for shard in 1 2 3; do
        exec_file "$root/coverage-ui-main-$shard/build/jacoco/ui-main-$shard.exec"
    done
    exec_file "$root/coverage-ui-ldap-1/build/jacoco/ui-ldap.exec"
    exec_file "$root/coverage-ui-oidc-1/build/jacoco/ui-oidc.exec"
    # Two artefacts of the same run that carry no execution data at all.
    mkdir -p "$root/coverage-classes/ontrack-model/build/classes/kotlin/main"
    mkdir -p "$root/coverage-jest/lcov-report"
}

# ===============================================================================================
# Which artefact belongs to which backend test type
# ===============================================================================================

assert_eq "unit" "$(cr_type_of_artefact coverage-unit)" "the unit artefact"
assert_eq "integration" "$(cr_type_of_artefact coverage-integration-3)" "an integration shard"
assert_eq "kdsl" "$(cr_type_of_artefact coverage-kdsl-2)" "a KDSL shard"
assert_eq "ui" "$(cr_type_of_artefact coverage-ui-main-2)" "a main Playwright shard"
assert_eq "ui" "$(cr_type_of_artefact coverage-ui-oidc-1)" "the oidc Playwright leg"
assert_eq "" "$(cr_type_of_artefact coverage-classes)" "the class files carry no execution data"
assert_eq "" "$(cr_type_of_artefact coverage-jest)" "and neither does the Jest report"
assert_eq "" "$(cr_type_of_artefact kdsl-results-shard-1)" "an artefact of another job is not ours"

# The session of a file: named after the task for the Gradle JVMs, after the session itself for
# the container legs (#1819).
assert_eq "unit" "$(cr_session_of coverage-unit some/build/jacoco/test.exec)" "unit, whatever the module"
assert_eq "integration-4" "$(cr_session_of coverage-integration-4 x/build/jacoco/integrationTest.exec)" \
    "an integration session carries its shard"
assert_eq "ui-main-2" "$(cr_session_of coverage-ui-main-2 build/jacoco/ui-main-2.exec)" \
    "a container leg's file is named after its session"
assert_eq "ui-ldap" "$(cr_session_of coverage-ui-ldap-1 build/jacoco/ui-ldap.exec)" \
    "the ldap leg is not sharded, so its session carries no suffix"

# ===============================================================================================
# Collecting a complete set
# ===============================================================================================

complete_artefacts "$WORK/artefacts"
output="$(cr_collect "$WORK/artefacts" "$WORK/exec" 2>&1)"
status=$?
assert_eq "0" "$status" "a complete set collects: $output"
assert_contains "$output" "coverage-unit: 3 execution data file(s) as unit" "every module's unit data"
assert_contains "$output" "coverage-integration-5: 2 execution data file(s) as integration" "both modules of a shard"

sessions="$(cr_sessions "$WORK/exec" | tr '\n' ' ')"
assert_eq "integration-1 integration-2 integration-3 integration-4 integration-5 kdsl-1 kdsl-2 ui-ldap ui-main-1 ui-main-2 ui-main-3 ui-oidc unit " \
    "$sessions" "the thirteen sessions of a complete run"

assert_eq "3" "$(find "$WORK/exec/unit/unit" -name '*.exec' | wc -l | tr -d ' ')" \
    "the three modules' unit data lands under one session"
assert_eq "false" "$([ -e "$WORK/exec/classes" ] && echo true || echo false)" \
    "coverage-classes is not mistaken for a test type"

# ===============================================================================================
# The expected set, and what happens when the set is not complete
# ===============================================================================================

expected="$(cr_expected | tr '\n' ' ')"
assert_eq "$sessions" "$expected" "a complete run produces exactly the expected sessions"

output="$(cr_check "$WORK/exec" 2>&1)"
status=$?
assert_eq "0" "$status" "a complete set passes the check: $output"

# A shard whose artefact never arrived -- a leg killed by its timeout, or a dump that wrote
# nothing (#1819 warns rather than failing, and this is where that warning becomes visible).
complete_artefacts "$WORK/artefacts"
rm -rf "$WORK/artefacts/coverage-integration-3" "$WORK/artefacts/coverage-ui-oidc-1"
rm -rf "$WORK/exec"
cr_collect "$WORK/artefacts" "$WORK/exec" > /dev/null 2>&1
output="$(cr_check "$WORK/exec" 2>&1)"
status=$?
assert_eq "1" "$status" "a missing shard fails the check"
assert_contains "$output" "no execution data for: integration-3 ui-oidc" "and names every missing session"

# An artefact that arrived empty -- the upload matched no file, so the dump produced nothing.
complete_artefacts "$WORK/artefacts"
rm -f "$WORK/artefacts/coverage-kdsl-2/build/jacoco/kdsl-2.exec"
rm -rf "$WORK/exec"
cr_collect "$WORK/artefacts" "$WORK/exec" > /dev/null 2>&1
output="$(cr_check "$WORK/exec" 2>&1)"
status=$?
assert_eq "1" "$status" "an artefact with no execution data in it fails the check"
assert_contains "$output" "kdsl-2" "and names the session that produced nothing"

# A leg whose session was misnamed: two legs writing one session ID lose one of them silently.
complete_artefacts "$WORK/artefacts"
mv "$WORK/artefacts/coverage-ui-main-3/build/jacoco/ui-main-3.exec" \
   "$WORK/artefacts/coverage-ui-main-3/build/jacoco/ui-main-2.exec"
rm -rf "$WORK/exec"
cr_collect "$WORK/artefacts" "$WORK/exec" > /dev/null 2>&1
output="$(cr_check "$WORK/exec" 2>&1)"
status=$?
assert_eq "1" "$status" "a renamed leg fails the check"
assert_contains "$output" "no execution data for: ui-main-3" "as a missing session"

# An unexpected session -- a suffix that leaked onto a leg that is not sharded.
complete_artefacts "$WORK/artefacts"
mv "$WORK/artefacts/coverage-ui-ldap-1/build/jacoco/ui-ldap.exec" \
   "$WORK/artefacts/coverage-ui-ldap-1/build/jacoco/ui-ldap-1.exec"
rm -rf "$WORK/exec"
cr_collect "$WORK/artefacts" "$WORK/exec" > /dev/null 2>&1
output="$(cr_check "$WORK/exec" 2>&1)"
status=$?
assert_eq "1" "$status" "an unexpected session fails the check"
assert_contains "$output" "unexpected sessions: ui-ldap-1" "and is named as such"

# The shard counts are named, as INTEGRATION_SHARDS is in ci.yml: a matrix that grows and a count
# that does not fails loudly, which is the safe direction.
assert_contains "$(COVERAGE_INTEGRATION_SHARDS=6 cr_expected | tr '\n' ' ')" "integration-6" \
    "the integration shard count is a parameter"
assert_eq "unit " "$(COVERAGE_EXPECTED_SESSIONS='unit' cr_expected | tr '\n' ' ')" \
    "and the whole list can be stated outright"

# ===============================================================================================
# The denominator
# ===============================================================================================

TREE="$WORK/tree"
for module in ontrack-model ontrack-service ontrack-test-utils ontrack-it-utils \
              ontrack-kdsl ontrack-kdsl-acceptance ontrack-demo-seed ontrack-web-tests buildSrc; do
    mkdir -p "$TREE/$module/build/classes/kotlin/main/net/nemerosa"
    touch "$TREE/$module/build/classes/kotlin/main/net/nemerosa/Thing.class"
    mkdir -p "$TREE/$module/src/main/kotlin/net/nemerosa"
    touch "$TREE/$module/src/main/kotlin/net/nemerosa/Thing.kt"
done
# A module whose Java source set is empty: it must not be handed to jacococli at all.
mkdir -p "$TREE/ontrack-model/build/classes/java/main"

classfiles="$(cr_classfiles "$TREE" | sed "s#$TREE/##" | sort | tr '\n' ' ')"
assert_eq "ontrack-model/build/classes/kotlin/main ontrack-service/build/classes/kotlin/main " \
    "$classfiles" "only the production modules count, and only where there are classes"

sourcefiles="$(cr_sourcefiles "$TREE" | sed "s#$TREE/##" | sort | tr '\n' ' ')"
assert_eq "ontrack-model/src/main/kotlin ontrack-service/src/main/kotlin " \
    "$sourcefiles" "the sources follow the same exclusion list"

assert_eq "0" "$(cr_is_excluded ontrack-kdsl-acceptance && echo 0 || echo 1)" \
    "the exclusion list globs: ontrack-kdsl* takes in ontrack-kdsl-acceptance"
assert_eq "1" "$(cr_is_excluded ontrack-extension-github && echo 0 || echo 1)" \
    "an extension is production code"

# ===============================================================================================
# The JaCoCo version is read from buildSrc, not restated
# ===============================================================================================

version="$(cr_jacoco_version)"
assert_eq "$(sed -n 's/.*JACOCO_VERSION *= *"\([^"]*\)".*/\1/p' \
    "$SCRIPT_DIR/../buildSrc/src/main/kotlin/net/nemerosa/ontrack/build/Coverage.kt" | head -n 1)" \
    "$version" "the version comes from Coverage.kt"
assert_eq "9.9.9" "$(COVERAGE_JACOCO_VERSION=9.9.9 cr_jacoco_version)" "and can be overridden"

report_tests
