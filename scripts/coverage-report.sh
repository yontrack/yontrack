#!/usr/bin/env bash
#
# Turns the JaCoCo execution data the test jobs collect into reports (#1821).
#
# Called by the `coverage` job of .github/workflows/ci.yml, and by the local Gradle path of
# #1823. The logic lives here rather than inline in the workflow for the reason stated at the top
# of scripts/security-image-scan.sh, and for one more that #1823 turns into a requirement: there
# must be exactly one definition of what is measured, or CI and a local run can disagree about
# what "72.4" means and the number is worthless. Nothing here reads a $GITHUB_* variable.
#
# Usage: scripts/coverage-report.sh <command> ...
#
#   collect ARTEFACTS EXECDIR   Lays the downloaded `coverage-*` artefact directories out as
#                               EXECDIR/<type>/<session>/NNN.exec, one directory per backend test
#                               type (`unit`, `integration`, `kdsl`, `ui`) and one per session.
#   sessions EXECDIR            Prints the session IDs found under EXECDIR, one per line, sorted.
#   expected                    Prints the session IDs a complete run produces, one per line.
#   check EXECDIR               Fails, naming them, when the sessions found are not exactly the
#                               expected ones. See *Completeness* below.
#   classfiles [TREE]           Prints the class directories the reports measure -- the
#                               denominator. See *Denominator* below.
#   sourcefiles [TREE]          Prints the matching source directories.
#   report EXECDIR OUTDIR [TREE]
#                               Runs `jacococli report`: OUTDIR/<type>/{html,jacoco.xml} for each
#                               of the four types, and OUTDIR/merged/{html,jacoco.xml} over all of
#                               them. The merged report's *Sessions* page is the class-level
#                               origin view the design settles for.
#   execinfo EXECDIR            Prints what the execution data itself says about its sessions.
#                               Informational: `check` decides, not this.
#
# Environment:
#   COVERAGE_TREE               Root of the tree holding the production classes and sources
#                               (default: the repository this script lives in). In CI it is where
#                               the `coverage-classes` artefact was extracted; locally it is the
#                               checkout itself.
#   COVERAGE_JACOCO_CLI         Path to org.jacoco.cli-<version>-nodeps.jar. When unset the jar is
#                               downloaded from Maven Central into COVERAGE_CACHE_DIR. #1823's
#                               Gradle task resolves it from the `jacocoCli` configuration and
#                               passes it here, so that a local run needs no network.
#   COVERAGE_JACOCO_VERSION     Overrides the version read from buildSrc's `Coverage.kt`.
#   COVERAGE_CACHE_DIR          Where a downloaded CLI jar is kept (default: $TMPDIR/yontrack-coverage).
#   COVERAGE_EXPECTED_SESSIONS  Explicit expected session list, space separated. Overrides the
#                               three shard counts below.
#   COVERAGE_INTEGRATION_SHARDS Legs of the `integration` matrix (default 5).
#   COVERAGE_KDSL_SHARDS        Legs of the `kdsl` matrix (default 2).
#   COVERAGE_UI_MAIN_SHARDS     Legs of the `main` variant of the `ui-tests` matrix (default 3).
#
# Completeness
# ------------
# A missing `.exec` may never quietly deflate a figure. #1819 deliberately made a failed dump warn
# rather than fail its build, on the grounds that the missing file is the signal; this is where
# that signal is read. A shard whose artefact never arrived, or a dump that produced nothing,
# leaves a session out of `sessions`, and `check` then names it and fails. A merged figure computed
# over four fifths of the suite reads as a real coverage drop, which is worse than no figure at all
# -- so partial figures are never emitted, and the same shape `integration-report` already uses for
# its own artefacts (the named shard counts) is used here.
#
# An *unexpected* session fails too: it means a leg was renamed or a suffix leaked where it should
# not have, and two legs writing one session ID silently lose a leg.
#
# Denominator
# -----------
# The exclusion list below is the denominator decision of
# `docs/grilling/2026-09-coverage/README.md`, and this is the only place it is written down.
# Everything else counts, so a class no test touches counts as 0%. It is applied on the *report*,
# never on collection: collecting everything and excluding here keeps one definition, where a
# collection-time filter would have to be repeated in four jobs.

set -uo pipefail

CR_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The modules that are test tooling, clients of the product or not JVM code at all. See
# *Denominator* above. Matched against the module directory name, with shell globbing.
CR_EXCLUDED_MODULES=(
    "ontrack-test-utils"
    "ontrack-it-utils"
    "ontrack-kdsl*"
    "ontrack-web-tests"
    "ontrack-web-core"
    "ontrack-demo-seed"
    "buildSrc"
)

# The four backend test types, in report order.
CR_TYPES=(unit integration kdsl ui)

cr_fail() { echo "ERROR: $*" >&2; return 1; }

# ---------------------------------------------------------------------------------------------
# The JaCoCo command line tool
# ---------------------------------------------------------------------------------------------

# The one place a JaCoCo version is spelled out is buildSrc's `Coverage.kt`, so it is read from
# there rather than restated. A shell script cannot call Kotlin, but the constant is a literal.
cr_jacoco_version() {
    if [ -n "${COVERAGE_JACOCO_VERSION:-}" ]; then
        echo "$COVERAGE_JACOCO_VERSION"
        return 0
    fi
    local source="$CR_REPO_ROOT/buildSrc/src/main/kotlin/net/nemerosa/ontrack/build/Coverage.kt" version
    version="$(sed -n 's/.*JACOCO_VERSION *= *"\([^"]*\)".*/\1/p' "$source" 2>/dev/null | head -n 1)"
    [ -n "$version" ] || { cr_fail "Could not read JACOCO_VERSION from $source"; return 1; }
    echo "$version"
}

cr_cli_jar() {
    if [ -n "${COVERAGE_JACOCO_CLI:-}" ]; then
        [ -f "$COVERAGE_JACOCO_CLI" ] || { cr_fail "No JaCoCo CLI jar at $COVERAGE_JACOCO_CLI"; return 1; }
        echo "$COVERAGE_JACOCO_CLI"
        return 0
    fi
    local version cache jar
    version="$(cr_jacoco_version)" || return 1
    cache="${COVERAGE_CACHE_DIR:-${TMPDIR:-/tmp}/yontrack-coverage}"
    jar="$cache/org.jacoco.cli-$version-nodeps.jar"
    if [ ! -f "$jar" ]; then
        mkdir -p "$cache" || return 1
        echo "Downloading the JaCoCo CLI $version" >&2
        curl -fsSL -o "$jar.tmp" \
            "https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/$version/org.jacoco.cli-$version-nodeps.jar" \
            || { rm -f "$jar.tmp"; cr_fail "Could not download the JaCoCo CLI $version"; return 1; }
        mv "$jar.tmp" "$jar"
    fi
    echo "$jar"
}

cr_cli() {
    local jar
    jar="$(cr_cli_jar)" || return 1
    java -jar "$jar" "$@"
}

# ---------------------------------------------------------------------------------------------
# Collecting the execution data
# ---------------------------------------------------------------------------------------------

# The backend test type an artefact belongs to, from its name. Empty for an artefact that carries
# no execution data (`coverage-classes`, `coverage-jest`), which is not an error.
cr_type_of_artefact() {
    case "$1" in
        coverage-unit) echo unit ;;
        coverage-integration-*) echo integration ;;
        coverage-kdsl-*) echo kdsl ;;
        coverage-ui-*) echo ui ;;
        *) echo "" ;;
    esac
}

# The session ID an execution data file carries.
#
# For the container legs (#1819) the file is *named* after its session -- `ui-main-2.exec` -- so
# the name is the answer. For the Gradle test JVMs (#1818) the file is named after the task
# (`test.exec`, `integrationTest.exec`), once per module, and the session is the type plus the
# shard the artefact name carries.
cr_session_of() {
    local artefact="$1" file="$2" base
    base="$(basename "$file" .exec)"
    case "$artefact" in
        coverage-unit) echo "unit" ;;
        coverage-integration-*) echo "integration-${artefact#coverage-integration-}" ;;
        *) echo "$base" ;;
    esac
}

cr_collect() {
    local artefacts="${1:-}" execdir="${2:-}" dir name type session count target
    [ -n "$artefacts" ] || { cr_fail "No artefact directory"; return 1; }
    [ -n "$execdir" ] || { cr_fail "No output directory"; return 1; }
    [ -d "$artefacts" ] || { cr_fail "No artefact directory at $artefacts"; return 1; }
    mkdir -p "$execdir" || return 1

    for dir in "$artefacts"/*; do
        [ -d "$dir" ] || continue
        name="$(basename "$dir")"
        type="$(cr_type_of_artefact "$name")"
        [ -n "$type" ] || continue
        count=0
        while IFS= read -r exec_file; do
            session="$(cr_session_of "$name" "$exec_file")"
            target="$execdir/$type/$session"
            mkdir -p "$target" || return 1
            count=$((count + 1))
            cp "$exec_file" "$(printf '%s/%03d.exec' "$target" "$count")" || return 1
        done < <(find "$dir" -name '*.exec' -type f | sort)
        echo "$name: $count execution data file(s) as $type"
    done
}

# The sessions actually present: a session directory holding at least one .exec file.
cr_sessions() {
    local execdir="${1:-}" dir
    [ -n "$execdir" ] || { cr_fail "No execution data directory"; return 1; }
    for dir in "$execdir"/*/*; do
        [ -d "$dir" ] || continue
        [ -n "$(find "$dir" -name '*.exec' -type f -print -quit)" ] || continue
        basename "$dir"
    done | sort -u
}

# What a complete run produces. Either stated outright, or built from the shard counts -- the same
# shape as INTEGRATION_SHARDS in ci.yml, and named for the same reason: getting one wrong fails
# loudly, which is the safe direction.
cr_expected() {
    if [ -n "${COVERAGE_EXPECTED_SESSIONS:-}" ]; then
        tr ' ' '\n' <<< "$COVERAGE_EXPECTED_SESSIONS" | sed '/^$/d' | sort -u
        return 0
    fi
    local i
    {
        echo "unit"
        for ((i = 1; i <= ${COVERAGE_INTEGRATION_SHARDS:-5}; i++)); do echo "integration-$i"; done
        for ((i = 1; i <= ${COVERAGE_KDSL_SHARDS:-2}; i++)); do echo "kdsl-$i"; done
        for ((i = 1; i <= ${COVERAGE_UI_MAIN_SHARDS:-3}; i++)); do echo "ui-main-$i"; done
        echo "ui-ldap"
        echo "ui-oidc"
    } | sort -u
}

cr_check() {
    local execdir="${1:-}" found expected missing unexpected status=0
    found="$(cr_sessions "$execdir")" || return 1
    expected="$(cr_expected)" || return 1
    missing="$(comm -23 <(echo "$expected") <(echo "$found"))"
    unexpected="$(comm -13 <(echo "$expected") <(echo "$found"))"

    echo "Expected sessions: $(tr '\n' ' ' <<< "$expected")"
    echo "Sessions found:    $(tr '\n' ' ' <<< "$found")"

    if [ -n "$missing" ]; then
        echo "ERROR: no execution data for: $(tr '\n' ' ' <<< "$missing")" >&2
        echo "       a shard's artefact never arrived, or its dump produced nothing." >&2
        status=1
    fi
    if [ -n "$unexpected" ]; then
        echo "ERROR: unexpected sessions: $(tr '\n' ' ' <<< "$unexpected")" >&2
        echo "       two legs writing one session ID lose one of them without saying so." >&2
        status=1
    fi
    return $status
}

cr_execinfo() {
    local execdir="${1:-}" file
    while IFS= read -r file; do
        echo "--- $file"
        cr_cli execinfo "$file" 2>&1 | sed -n '/^Session/p'
    done < <(find "$execdir" -name '*.exec' -type f | sort)
}

# ---------------------------------------------------------------------------------------------
# The denominator
# ---------------------------------------------------------------------------------------------

cr_is_excluded() {
    local module="$1" pattern
    for pattern in "${CR_EXCLUDED_MODULES[@]}"; do
        # shellcheck disable=SC2053  # deliberate: the patterns are globs
        [[ "$module" == $pattern ]] && return 0
    done
    return 1
}

# Prints the directories under TREE matching one of the given `build/classes/...` or `src/main/...`
# relative paths, for the modules that are not excluded and that are not empty.
cr_module_dirs() {
    local tree="$1" file_glob="$2" dir module relative
    shift 2
    for relative in "$@"; do
        while IFS= read -r dir; do
            module="$(basename "${dir%/"$relative"}")"
            cr_is_excluded "$module" && continue
            # An empty directory makes jacococli neither fail nor useful; skip it so the report's
            # inputs are exactly the modules that produced something.
            [ -n "$(find "$dir" -name "$file_glob" -type f -print -quit)" ] || continue
            echo "$dir"
        done < <(find "$tree" -type d -path "*/$relative" -not -path "*/node_modules/*" | sort)
    done
}

cr_classfiles() {
    local tree="${1:-${COVERAGE_TREE:-$CR_REPO_ROOT}}"
    cr_module_dirs "$tree" '*.class' "build/classes/java/main" "build/classes/kotlin/main"
}

cr_sourcefiles() {
    local tree="${1:-${COVERAGE_TREE:-$CR_REPO_ROOT}}"
    cr_module_dirs "$tree" '*' "src/main/java" "src/main/kotlin"
}

# ---------------------------------------------------------------------------------------------
# The reports
# ---------------------------------------------------------------------------------------------

# report NAME OUTDIR TREE EXECFILE...
cr_report_one() {
    local name="$1" outdir="$2" tree="$3"
    shift 3
    local -a args=(report "$@")
    local dir classes=0
    while IFS= read -r dir; do
        args+=(--classfiles "$dir")
        classes=$((classes + 1))
    done < <(cr_classfiles "$tree")
    while IFS= read -r dir; do args+=(--sourcefiles "$dir"); done < <(cr_sourcefiles "$tree")
    # No class files at all is never a 0% report: it is a wrong COVERAGE_TREE, and a report over
    # nothing would read as total failure of every suite at once.
    [ "$classes" -gt 0 ] || { cr_fail "No class files to report on under $tree"; return 1; }
    mkdir -p "$outdir" || return 1
    args+=(--html "$outdir/html" --xml "$outdir/jacoco.xml" --name "$name" --encoding UTF-8)
    echo "Reporting $name from $# execution data file(s)"
    cr_cli "${args[@]}" || { cr_fail "jacococli could not report $name"; return 1; }
}

cr_report() {
    local execdir="${1:-}" outdir="${2:-}" tree="${3:-${COVERAGE_TREE:-$CR_REPO_ROOT}}" type
    [ -n "$execdir" ] || { cr_fail "No execution data directory"; return 1; }
    [ -n "$outdir" ] || { cr_fail "No output directory"; return 1; }

    local -a all=() files=()
    local file
    for type in "${CR_TYPES[@]}"; do
        files=()
        while IFS= read -r file; do files+=("$file"); done \
            < <(find "$execdir/$type" -name '*.exec' -type f 2>/dev/null | sort)
        [ "${#files[@]}" -gt 0 ] && all+=("${files[@]}")
        # A type with no data still gets a report: every included class at 0%, which is the honest
        # picture and keeps the four XML files the figures are computed from always present.
        cr_report_one "$type" "$outdir/$type" "$tree" "${files[@]+"${files[@]}"}" || return 1
    done
    cr_report_one "merged" "$outdir/merged" "$tree" "${all[@]+"${all[@]}"}" || return 1
}

cr_main() {
    local command="${1:-}"
    [ $# -gt 0 ] && shift
    case "$command" in
        collect) cr_collect "$@" ;;
        sessions) cr_sessions "${1:-}" ;;
        expected) cr_expected ;;
        check) cr_check "${1:-}" ;;
        classfiles) cr_classfiles "${1:-}" ;;
        sourcefiles) cr_sourcefiles "${1:-}" ;;
        report) cr_report "$@" ;;
        execinfo) cr_execinfo "${1:-}" ;;
        *)
            echo "Usage: $0 collect|sessions|expected|check|classfiles|sourcefiles|report|execinfo ..." >&2
            return 1
            ;;
    esac
}

if [ -z "${COVERAGE_REPORT_LIB_ONLY:-}" ]; then
    cr_main "$@"
    exit $?
fi
