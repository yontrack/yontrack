#!/usr/bin/env bash
#
# Tests for release.sh. `curl`, `gh`, `git` and the Yontrack CLI are stubbed on the PATH, so
# nothing here talks to a real instance, a real registry or a real repository: the stubs answer
# from canned files and record every call they receive, which is what the assertions read.
#
# The guards get most of the attention. Publishing is the one step in this pipeline that cannot
# be undone - a Docker Hub tag or a GitHub release under a version that already shipped is the
# worst failure mode in the design - so "it refuses" is worth more coverage here than "it
# publishes", which the workflow does with plain `docker push` anyway.
#
# Usage: scripts/release-test.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load release.sh as a library: defines the functions, runs nothing.
RELEASE_LIB_ONLY=1
export RELEASE_LIB_ONLY
# shellcheck source=release.sh
source "$SCRIPT_DIR/release.sh"

# Assertions, shared with the other shell suites.
# shellcheck source=shell-test-lib.sh
source "$SCRIPT_DIR/shell-test-lib.sh"

# ===========================================================================
# The stubs
# ===========================================================================

STUB_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/release-test.XXXXXX")" || {
    echo "FATAL: could not create a temporary directory" >&2
    exit 1
}
trap 'rm -rf "$STUB_ROOT"' EXIT
mkdir -p "$STUB_ROOT/bin"

# Two very different callers share `curl` here: the Yontrack GraphQL API, which answers a body
# followed by the status code (the shape `-w '\n%{http_code}'` produces), and the Docker Hub tag
# lookup, which asks for the status code and nothing else. They are told apart by the URL.
cat > "$STUB_ROOT/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

body=""
url=""
prev=""
for arg in "$@"; do
    case "$prev" in
        -d) body="$arg" ;;
    esac
    case "$arg" in
        http*) url="$arg" ;;
    esac
    prev="$arg"
done

echo "$url <- $body" >> "$REL_STUB_DIR/calls.log"

case "$url" in
    *hub.docker.com*)
        # One file per published repository:tag. Absent means 404, which is the case that
        # lets a release through.
        tag="${url##*/}"
        repo="${url%/tags/*}"
        repo="${repo##*/repositories/}"
        if [ -f "$REL_STUB_DIR/dockerhub/$(echo "$repo:$tag" | tr '/' '_')" ]; then
            echo "200"
        else
            echo "404"
        fi
        exit 0
        ;;
esac

if [ -f "$REL_STUB_DIR/graphql_transport_fails" ]; then
    echo "curl: (6) Could not resolve host" >&2
    exit 6
fi

if [ -f "$REL_STUB_DIR/build.json" ]; then
    cat "$REL_STUB_DIR/build.json"
else
    echo '{"data":null}'
fi
echo "$(cat "$REL_STUB_DIR/graphql_status" 2>/dev/null || echo 200)"
STUB
chmod +x "$STUB_ROOT/bin/curl"

# The Yontrack CLI: resolving the version to a build, and exporting the changelog.
cat > "$STUB_ROOT/bin/yontrack" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

echo "$*" >> "$REL_STUB_DIR/calls.log"

if [ "${1:-}" = "build" ] && [ "${2:-}" = "search" ]; then
    if [ -f "$REL_STUB_DIR/search_fails" ]; then
        echo "cannot reach Yontrack" >&2
        exit 1
    fi
    if [ -f "$REL_STUB_DIR/search_empty" ]; then
        exit 0
    fi
    cat "$REL_STUB_DIR/search.json"
    exit 0
fi

if [ "${1:-}" = "build" ] && [ "${2:-}" = "changelog" ]; then
    if [ -f "$REL_STUB_DIR/changelog_fails" ]; then
        echo "no previous release" >&2
        exit 1
    fi
    cat "$REL_STUB_DIR/changelog.md"
    exit 0
fi

echo "unexpected command: $*" >&2
exit 1
STUB
chmod +x "$STUB_ROOT/bin/yontrack"

# `gh api` against the run's artifacts.
cat > "$STUB_ROOT/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

echo "$*" >> "$REL_STUB_DIR/calls.log"

if [ "${1:-}" = "api" ]; then
    if [ -f "$REL_STUB_DIR/artifacts_fails" ]; then
        echo "gh: Not Found (HTTP 404)" >&2
        exit 1
    fi
    cat "$REL_STUB_DIR/artifacts.json"
    exit 0
fi

echo "unexpected command: $*" >&2
exit 1
STUB
chmod +x "$STUB_ROOT/bin/gh"

# `git tag -l <version>`, the local half of the "already published" guard.
cat > "$STUB_ROOT/bin/git" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail

echo "$*" >> "$REL_STUB_DIR/calls.log"

if [ "${1:-}" = "tag" ]; then
    wanted="${3:-}"
    if [ -f "$REL_STUB_DIR/tags" ] && grep -qxF "$wanted" "$REL_STUB_DIR/tags"; then
        echo "$wanted"
    fi
    exit 0
fi

echo "unexpected command: $*" >&2
exit 1
STUB
chmod +x "$STUB_ROOT/bin/git"

PATH="$STUB_ROOT/bin:$PATH"
export PATH

# Resets the stubs to a healthy default: 5.3.0-rc-100 is build 100, its CI run is 42, nothing
# is published under 5.3.0 yet, the docs artefact is there, and the wiki page is written.
setup_stub() {
    REL_STUB_DIR="$(mktemp -d "$STUB_ROOT/case.XXXXXX")" || { echo "FATAL: mktemp failed" >&2; exit 1; }
    export REL_STUB_DIR
    : > "$REL_STUB_DIR/calls.log"
    mkdir -p "$REL_STUB_DIR/dockerhub"

    cat > "$REL_STUB_DIR/search.json" <<'JSON'
{"Id":"100","Name":"20260901055547-100","DisplayName":"5.3.0-rc-100"}
JSON

    cat > "$REL_STUB_DIR/build.json" <<'JSON'
{"data":{"build":{
  "id": 100,
  "name": "20260901055547-100",
  "displayName": "5.3.0-rc-100",
  "gitCommitProperty": {"value": {"commit": "abc1234def5678"}},
  "buildGitHubWorkflowRunProperty": {"value": {"workflows": [
    {"runId": 42, "url": "https://github.com/yontrack/yontrack/actions/runs/42", "name": "CI", "runNumber": 100, "event": "push"}
  ]}}
}}}
JSON

    cat > "$REL_STUB_DIR/artifacts.json" <<'JSON'
{"artifacts":[
  {"id": 900, "name": "other", "expired": false},
  {"id": 901, "name": "docs-site", "expired": false}
]}
JSON

    cat > "$REL_STUB_DIR/changelog.md" <<'MD'
* [#1672](https://github.com/yontrack/yontrack/issues/1672) release.yml
MD

    : > "$REL_STUB_DIR/tags"

    # The wiki, as the workflow leaves it: a plain clone in a directory.
    REL_WIKI_DIR="$REL_STUB_DIR/wiki"
    export REL_WIKI_DIR
    mkdir -p "$REL_WIKI_DIR"
    echo "# Yontrack 5.3.0" > "$REL_WIKI_DIR/Release-5.3.0.md"
    cat > "$REL_WIKI_DIR/Home.md" <<'MD'
# Release notes

## Releases

* [5.3.0](Release-5.3.0)

## Archive

* [v5](v5-release-notes)
MD

    RELEASE_PROJECT=yontrack
    RELEASE_BRANCH=main
    RELEASE_REPOSITORY=yontrack/yontrack
    RELEASE_BUILD_VERSION=5.3.0-rc-100
    RELEASE_VERSION=""
    YONTRACK_URL=https://yontrack.example.com
    YONTRACK_TOKEN=token
    export RELEASE_PROJECT RELEASE_BRANCH RELEASE_REPOSITORY RELEASE_BUILD_VERSION \
        RELEASE_VERSION YONTRACK_URL YONTRACK_TOKEN

    GITHUB_OUTPUT="$REL_STUB_DIR/github_output"
    export GITHUB_OUTPUT
    : > "$GITHUB_OUTPUT"
}

calls() { cat "$REL_STUB_DIR/calls.log"; }
outputs() { cat "$GITHUB_OUTPUT"; }

# Marks a repository:tag as already on Docker Hub.
publish_docker() { touch "$REL_STUB_DIR/dockerhub/$(echo "$1" | tr '/' '_')"; }

# ===========================================================================
# rel_base_version (pure)
# ===========================================================================

# The version to publish is the rc version with its candidate suffix taken off. This is the
# whole of the "build identity" decision in one function: an rc build is what ships, under the
# base version its artefacts are named for.

assert_eq "5.3.0" "$(rel_base_version 5.3.0-rc-100)" \
    "rel_base_version strips the release-candidate suffix"

assert_eq "5.3.0" "$(rel_base_version 5.3.0)" \
    "rel_base_version leaves a version with no suffix alone"

assert_eq "5.3.10" "$(rel_base_version 5.3.10-rc-7)" \
    "rel_base_version handles a two-digit patch"

# Only a trailing `-rc-<digits>` is a candidate suffix. A feature-branch version carries the
# branch name and a short commit, and mangling it into something that looks releasable is
# exactly the accident the guards below exist to catch - so it must not even be attempted.
assert_eq "5.3.0-my-branch-abc1234" "$(rel_base_version 5.3.0-my-branch-abc1234)" \
    "rel_base_version does not touch a feature-branch version"

assert_eq "5.3.0-rc-x" "$(rel_base_version 5.3.0-rc-x)" \
    "rel_base_version only strips a numeric candidate suffix"

# ===========================================================================
# resolve: the version to publish
# ===========================================================================

setup_stub
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "0" "$rc" "resolve: succeeds on a build that has never been released"
assert_contains "$(outputs)" "version=5.3.0" "resolve: publishes under the base version"
assert_contains "$(outputs)" "build=20260901055547-100" "resolve: names the build to validate"
assert_contains "$(outputs)" "run_id=42" "resolve: carries the CI run that holds the docs"
assert_contains "$(outputs)" "sha=abc1234def5678" "resolve: carries the commit the release targets"
assert_contains "$(calls)" "--with-display-name" "resolve: finds the build by its display name"

# The rc version is the GHCR tag the images are re-tagged *from*, so the publication needs both
# versions: the one that was built and the one being published.
assert_contains "$(outputs)" "rc_version=5.3.0-rc-100" "resolve: carries the rc version to re-tag from"
assert_contains "$(outputs)" "build_id=100" "resolve: carries the build id the changelog measures to"

# The CI run recorded on the build is picked by workflow name, not by position: the property is
# a list, and reading [0] would pick the wrong run the day anything else records itself there.
setup_stub
cat > "$REL_STUB_DIR/build.json" <<'JSON'
{"data":{"build":{
  "id": 100,
  "name": "20260901055547-100",
  "displayName": "5.3.0-rc-100",
  "gitCommitProperty": {"value": {"commit": "abc1234def5678"}},
  "buildGitHubWorkflowRunProperty": {"value": {"workflows": [
    {"runId": 7, "name": "Demo smoke", "runNumber": 3, "event": "workflow_dispatch"},
    {"runId": 42, "name": "CI", "runNumber": 100, "event": "push"}
  ]}}
}}}
JSON
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "0" "$rc" "resolve: succeeds when the build records more than one run"
assert_contains "$(outputs)" "run_id=42" "resolve: picks the CI run, not the first one"

# A build carrying no commit cannot be tagged, and `gh release create --target` would fail
# halfway through the publication rather than before it.
setup_stub
cat > "$REL_STUB_DIR/build.json" <<'JSON'
{"data":{"build":{
  "id": 100,
  "name": "20260901055547-100",
  "displayName": "5.3.0-rc-100",
  "gitCommitProperty": null,
  "buildGitHubWorkflowRunProperty": {"value": {"workflows": [{"runId": 42, "name": "CI"}]}}
}}}
JSON
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: fails when the build records no commit"

# Yontrack being unreachable must not read as "this version was never released": the guards are
# only worth something if a failure to check them stops the release.
setup_stub
touch "$REL_STUB_DIR/graphql_transport_fails"
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: fails when Yontrack cannot be reached"

# An explicit override is allowed: the base version is a default, not a rule.
setup_stub
out="$(RELEASE_VERSION=5.4.0 rel_resolve 2>&1)"; rc=$?
assert_eq "0" "$rc" "resolve: succeeds with an explicit version"
assert_contains "$(outputs)" "version=5.4.0" "resolve: an explicit version overrides the base version"

setup_stub
echo "5.3.0" > "$REL_STUB_DIR/tags"
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: refuses a version whose git tag already exists"
assert_contains "$out" "5.3.0" "resolve: names the version it refused"
assert_contains "$out" "already" "resolve: says the tag is already there"

# All four repositories are guarded, not just the first: the legacy `nemerosa/*` pair and the
# `yontrack/*` pair are pushed together, so any one of them already carrying the tag means the
# release ran before.
for repo in nemerosa/ontrack nemerosa/ontrack-ui yontrack/yontrack yontrack/yontrack-ui; do
    setup_stub
    publish_docker "$repo:5.3.0"
    out="$(rel_resolve 2>&1)"; rc=$?
    assert_eq "1" "$rc" "resolve: refuses when $repo:5.3.0 is already on Docker Hub"
    assert_contains "$out" "$repo" "resolve: names the repository that already has the tag"
done

# An rc tag on Docker Hub is not what is being guarded - only the version about to be
# published. Guarding the rc version would refuse every release, since rc images are what CI
# pushes to GHCR.
setup_stub
publish_docker "yontrack/yontrack:5.3.0-rc-100"
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "0" "$rc" "resolve: an existing rc tag does not block the release"

setup_stub
touch "$REL_STUB_DIR/search_empty"
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: fails when Yontrack does not know the version"
assert_contains "$out" "5.3.0-rc-100" "resolve: names the version it could not find"

# A build with no `release` property has its own name as its display name, and that name is not
# a version. Publishing under it would tag Docker Hub with a timestamp.
setup_stub
cat > "$REL_STUB_DIR/search.json" <<'JSON'
{"Id":"100","Name":"20260901055547-100","DisplayName":"20260901055547-100"}
JSON
out="$(RELEASE_BUILD_VERSION=20260901055547-100 rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: refuses a build whose display name is not a version"
assert_contains "$out" "version" "resolve: says what is wrong with it"

# ===========================================================================
# docs: the artefact has to be there before anything is published
# ===========================================================================

setup_stub
out="$(REL_RUN_ID=42 REL_VERSION=5.3.0 REL_SHA=abc1234def5678 rel_docs 2>&1)"; rc=$?
assert_eq "0" "$rc" "docs: succeeds when the artefact is on the run"
assert_contains "$(outputs)" "artifact_id=901" "docs: names the artefact to download"
assert_contains "$(calls)" "actions/runs/42/artifacts" "docs: asks the CI run recorded on the build"

# The message is the point of this step. 90 days of artefact retention is a real release
# deadline, and the operator has to be told the one thing that fixes it.
setup_stub
cat > "$REL_STUB_DIR/artifacts.json" <<'JSON'
{"artifacts":[{"id": 901, "name": "docs-site", "expired": true}]}
JSON
out="$(REL_RUN_ID=42 REL_VERSION=5.3.0 REL_SHA=abc1234def5678 rel_docs 2>&1)"; rc=$?
assert_eq "1" "$rc" "docs: fails on an expired artefact"
assert_contains "$out" "docs artifact expired for 5.3.0" "docs: names the version in the message"
assert_contains "$out" "re-run CI on abc1234def5678" "docs: says which commit to re-run"

setup_stub
echo '{"artifacts":[]}' > "$REL_STUB_DIR/artifacts.json"
out="$(REL_RUN_ID=42 REL_VERSION=5.3.0 REL_SHA=abc1234def5678 rel_docs 2>&1)"; rc=$?
assert_eq "1" "$rc" "docs: fails when the artefact was never uploaded"
assert_contains "$out" "docs artifact expired for 5.3.0" "docs: reports a missing artefact the same way"

setup_stub
touch "$REL_STUB_DIR/artifacts_fails"
out="$(REL_RUN_ID=42 REL_VERSION=5.3.0 REL_SHA=abc1234def5678 rel_docs 2>&1)"; rc=$?
assert_eq "1" "$rc" "docs: fails when the run cannot be read"

# A build with no GitHub workflow run property has no run to download from. That is a broken
# build registration, not an expired artefact, and it reads differently.
setup_stub
cat > "$REL_STUB_DIR/build.json" <<'JSON'
{"data":{"build":{
  "id": 100,
  "name": "20260901055547-100",
  "displayName": "5.3.0-rc-100",
  "gitCommitProperty": {"value": {"commit": "abc1234def5678"}},
  "buildGitHubWorkflowRunProperty": null
}}}
JSON
out="$(rel_resolve 2>&1)"; rc=$?
assert_eq "1" "$rc" "resolve: fails when the build records no CI run"
assert_contains "$out" "run" "resolve: says the CI run is missing"

# ===========================================================================
# wiki: a check, not a publication step
# ===========================================================================

setup_stub
out="$(REL_VERSION=5.3.0 rel_wiki 2>&1)"; rc=$?
assert_eq "0" "$rc" "wiki: passes when the page exists and is linked"

setup_stub
rm "$REL_WIKI_DIR/Release-5.3.0.md"
out="$(REL_VERSION=5.3.0 rel_wiki 2>&1)"; rc=$?
assert_eq "1" "$rc" "wiki: fails when the release page is missing"
assert_contains "$out" "Release-5.3.0.md" "wiki: names the page it expected"

# A page nobody can reach from the index is a page nobody reads, and the GitHub release links
# to it: an unreachable page makes the release notes a dead end.
setup_stub
cat > "$REL_WIKI_DIR/Home.md" <<'MD'
# Release notes

## Releases

## Archive

* [v5](v5-release-notes)
MD
out="$(REL_VERSION=5.3.0 rel_wiki 2>&1)"; rc=$?
assert_eq "1" "$rc" "wiki: fails when the page is not linked from the index"
assert_contains "$out" "Home.md" "wiki: names the index"

# The archive pages mention versions in prose. Matching on the version alone would pass on
# `v5-release-notes` naming 5.3.0 in a sentence, so the link target is what is matched.
setup_stub
cat > "$REL_WIKI_DIR/Home.md" <<'MD'
# Release notes

## Releases

Nothing here yet, but 5.3.0 is coming.

## Archive

* [v5](v5-release-notes)
MD
out="$(REL_VERSION=5.3.0 rel_wiki 2>&1)"; rc=$?
assert_eq "1" "$rc" "wiki: a version mentioned in prose is not a link"

# ===========================================================================
# body: the wiki link first, the changelog second
# ===========================================================================

# End-user prose lives in the wiki, internals in the release where developers look for them,
# and neither is duplicated. The order is the whole convention: whoever opens the release sees
# the readable page first.

setup_stub
body="$(REL_VERSION=5.3.0 REL_BUILD_ID=100 rel_body 2>/dev/null)"; rc=$?
assert_eq "0" "$rc" "body: succeeds"
assert_contains "$body" "wiki/Release-5.3.0" "body: links the wiki page"
assert_contains "$body" "#1672" "body: carries the commit changelog"

wiki_at="${body%%wiki/Release-5.3.0*}"
assert_not_contains "$wiki_at" "#1672" "body: puts the wiki link before the changelog"

assert_contains "$(calls)" "--from-promotion RELEASE" \
    "body: takes the changelog since the last released build"

# `--to` is spelled out because this workflow registers no build of its own, so the CLI has no
# build context in the environment to read the upper boundary from.
assert_contains "$(calls)" "--to 100" "body: measures the changelog to the build being released"

# The first release after this pipeline lands has no previous RELEASE build to compare against,
# and a changelog that cannot be computed must not sink the release: the wiki page is the part
# a human wrote, and it is still there.
setup_stub
touch "$REL_STUB_DIR/changelog_fails"
body="$(REL_VERSION=5.3.0 REL_BUILD_ID=100 rel_body 2>/dev/null)"; rc=$?
assert_eq "0" "$rc" "body: survives a changelog that cannot be computed"
assert_contains "$body" "wiki/Release-5.3.0" "body: still links the wiki page"

report_tests
