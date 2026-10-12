#!/usr/bin/env bash
#
# Publishes a Yontrack release.
#
# Called by .github/workflows/release.yml, which the GOLD promotion dispatches through the
# `github-workflow` notification channel. The logic lives here rather than inline in the
# workflow so that scripts/release-test.sh can exercise it against stubbed clients - and
# because most of what this file does is refuse to run, which is exactly the part worth
# testing.
#
# Usage: scripts/release.sh resolve|docs|wiki|body|publish|meta
#
# One process for every release, alpha, beta and GA alike (#2070): the version decides the stage
# cells - the name of the wiki page and the GitHub pre-release flag - and nothing else.
#
#   resolve  Refuses any branch but `main` and a release branch, finds the build the version
#            names, works out the version to publish, and refuses to publish over anything that
#            already exists. Outputs `version`, `rc_version`, `build`, `build_id`, `run_id`,
#            `sha` and `wiki_page`, which drive every later step, and `latest` and `prerelease`,
#            for the log: `publish` decides those two again from the version.
#   docs     Finds the docs artefact on the CI run recorded on the build, before anything is
#            published. Outputs `artifact_id`.
#   wiki     Checks the release page of the version's stage exists in the wiki and is reachable
#            from the index. A check, not a publication step - the page is written by a human
#            before GOLD.
#   body     Composes the GitHub release body: the wiki link first, the changelog second. Falls
#            back to the git range when Yontrack has no changelog to give, which is the normal
#            case on a freshly cut release branch.
#   publish  Creates the GitHub release with that body: a pre-release for an alpha or a beta,
#            Latest only for the highest GA.
#   meta     Records the wiki page on the build, as the `wiki-page` meta-info item.
#
# Environment:
#   YONTRACK_URL           Yontrack instance
#   YONTRACK_TOKEN         API token on it
#   RELEASE_BUILD_VERSION  version the GOLD promotion named, i.e. the build's display name
#                          at that point (`6.0.1-rc.700`, `6.0.0-beta.7.rc.700`)
#   RELEASE_VERSION        version to publish under; defaults to the base version
#   RELEASE_PROJECT        Yontrack project (default: yontrack)
#   RELEASE_BRANCH         Yontrack branch (default: main); `main` or a release branch
#   RELEASE_REPOSITORY     GitHub repository (default: yontrack/yontrack)
#   REL_WIKI_DIR           checkout of the wiki (default: wiki)
#   REL_VERSION            version being published, for the steps after `resolve`
#   REL_RC_VERSION         the rc version, i.e. the GHCR tag to re-tag from
#   REL_BUILD_ID           Yontrack build id, for the changelog boundary and the meta-info item
#   REL_RUN_ID             CI run holding the docs artefact
#   REL_SHA                commit the release targets, and the upper bound of the changelog
#                          fallback
#
# The Yontrack CLI must already be installed and configured; `gh` must be authenticated.

set -uo pipefail

# Build lookup, shared with scripts/demo-deploy.sh and scripts/demo-smoke.sh so that the
# workflows that deploy, verify and release a version all resolve the same build.
# shellcheck source=yontrack-build.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/yontrack-build.sh"

REL_PROJECT="${RELEASE_PROJECT:-yontrack}"
REL_BRANCH="${RELEASE_BRANCH:-main}"
REL_REPOSITORY="${RELEASE_REPOSITORY:-yontrack/yontrack}"

# Both organisations, both images. They are pushed together, so any one of them already
# carrying the tag means this release ran before.
REL_DOCKER_REPOSITORIES="nemerosa/ontrack nemerosa/ontrack-ui yontrack/yontrack yontrack/yontrack-ui"

rel_log() { echo "$*"; }
rel_fail() { echo "ERROR: $*" >&2; return 1; }

# GitHub sinks, no-ops when running outside Actions.
rel_output() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1=$2" >> "$GITHUB_OUTPUT"; return 0; }
rel_summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$1" >> "$GITHUB_STEP_SUMMARY"; return 0; }

# ---------------------------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------------------------

# The version an rc version is a candidate for: `6.0.1-rc.700` -> `6.0.1`,
# `6.0.0-beta.7.rc.700` -> `6.0.0-beta.7`, and in the legacy shape still built by release/5.5,
# `5.5.9-rc-41` -> `5.5.9` (see docs/adr/0020).
#
# This one line is the build-identity decision made concrete (see docs/adr/0006). Nothing is
# rebuilt for a release, so what ships is the rc build, published under the version its
# artefacts are named for.
#
# Only a trailing `-rc.<digits>`, `.rc.<digits>` or legacy `-rc-<digits>` is stripped. A
# feature-branch version carries the branch name and a short commit, and turning that into
# something that merely looks releasable is the accident rel_check_unpublished exists to catch -
# so it is left alone here and rejected by rel_valid_version below.
rel_base_version() {
    printf '%s' "$1" | sed -E 's/([-.]rc\.|-rc-)[0-9]+$//'
}

# A version as five numbers to compare field by field: major, minor, patch, stage, stage number.
# The stage is 0 for alpha, 1 for beta and 2 for a GA, which puts `6.0.0-alpha.N` <
# `6.0.0-beta.M` < `6.0.0`. Three shapes: a GA `X.Y.Z`, a pre-release `X.Y.Z-(alpha|beta).N`, and
# the legacy pre-release `X.Y-(alpha|beta).N`, published up to `6.0-beta.6` and never renamed,
# whose patch is 0 - so `6.0-beta.6` < `6.0.0-beta.7`, `6.0.1` is above `6.0-beta.M` and `5.5.7`
# below `6.0-alpha.0`. Fails on anything that is not a version.
rel_version_key() {
    local v="$1" stage=0
    if [[ "$v" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]} 2 0"
    elif [[ "$v" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-(alpha|beta)\.([0-9]+)$ ]]; then
        [ "${BASH_REMATCH[4]}" = "beta" ] && stage=1
        echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} ${BASH_REMATCH[3]} $stage ${BASH_REMATCH[5]}"
    elif [[ "$v" =~ ^([0-9]+)\.([0-9]+)-(alpha|beta)\.([0-9]+)$ ]]; then
        [ "${BASH_REMATCH[3]}" = "beta" ] && stage=1
        echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]} 0 $stage ${BASH_REMATCH[4]}"
    else
        return 1
    fi
}

# A version is three dot-separated numbers, or a pre-release, and nothing else. A build with no
# `release` property has its own name - a timestamp-run pair - as its display name, and
# publishing under that would tag Docker Hub with a timestamp.
rel_valid_version() {
    rel_version_key "$1" > /dev/null
}

# A pre-release: `X.Y.0-alpha.N` or `X.Y.0-beta.N`, what `VersionCalculator` builds from a
# `VERSION` of `X.Y-alpha` or `X.Y-beta` (#2068), or the legacy `X.Y-alpha.N` and `X.Y-beta.N` it
# built before (#1987). Released like a GA - wiki page, WIKI, RELEASE - but as a GitHub
# pre-release, never Latest (#2070).
rel_is_prerelease() {
    local key
    key="$(rel_version_key "$1")" || return 1
    [ "$(echo "$key" | cut -d' ' -f4)" != 2 ]
}

# The `X Y` of a version in `rel_valid_version`'s sense.
rel_major_minor() {
    rel_version_key "$1" | cut -d' ' -f1,2
}

# The wiki page a version's release notes live on, without its `.md`: the version with the
# pre-release number removed and the stage capitalised - the convention the wiki already follows.
# One page per stage, renamed from stage to stage by whoever writes the notes:
# `6.0.0-alpha.3` -> `Release-6.0.0-Alpha`, `6.0.0-beta.6` -> `Release-6.0.0-Beta`,
# `6.0.0` -> `Release-6.0.0`. Fails on anything that is not a version.
rel_wiki_page() {
    local v="$1"
    rel_valid_version "$v" || return 1
    if [[ "$v" =~ ^(.+)-alpha\.[0-9]+$ ]]; then
        printf 'Release-%s-Alpha' "${BASH_REMATCH[1]}"
    elif [[ "$v" =~ ^(.+)-beta\.[0-9]+$ ]]; then
        printf 'Release-%s-Beta' "${BASH_REMATCH[1]}"
    else
        printf 'Release-%s' "$v"
    fi
}

# Whether a Yontrack branch is one a release is published from: `main`, or a release branch in
# either of its spellings - `release-6.0`, the escaped Yontrack name, or `release/6.0`, which the
# GOLD workflow has been seen to pass (see "Two names for one branch" in
# doc/dev-guide/patch-release.md). A `v<N>` major branch and a feature branch build development
# versions only: GOLD granted there would otherwise publish `7.0.0-rc.R` as `7.0.0`.
rel_releasable_branch() {
    [[ "$1" =~ ^(main|release[-/][0-9]+\.[0-9]+)$ ]]
}

# Whether $1 is strictly greater than $2, both versions in `rel_valid_version`'s sense.
#
# Field by field as numbers, rather than `sort -V`: the release runs on ubuntu-latest, but
# release-test.sh is run by hand on a developer machine too, and BSD `sort -V` is not GNU's. The
# comparison that matters either way is 5.3.10 against 5.3.9, which any string ordering gets
# backwards - and `6.0.0-beta.0` against `6.0.0`, which `sort -V` gets backwards too.
rel_version_gt() {
    local i ak bk
    local -a af bf
    ak="$(rel_version_key "$1")" || return 1
    bk="$(rel_version_key "$2")" || return 1
    read -r -a af <<< "$ak"
    read -r -a bf <<< "$bk"
    for i in 0 1 2 3 4; do
        [ "${af[i]}" -gt "${bf[i]}" ] && return 0
        [ "${af[i]}" -lt "${bf[i]}" ] && return 1
    done
    return 1
}

# Every released version, pre-releases included, from the git tags. `rel_valid_version`'s shape,
# and nothing else: the repository also carries `experimental-pipeline-<sha>` tags and `rc`
# candidates, and neither is a release.
rel_version_tags() {
    rel_git_tags | while read -r tag; do
        rel_valid_version "$tag" && printf '%s\n' "$tag"
    done
    # The loop's status is the last `rel_valid_version`, which is 1 whenever the last tag is not a
    # release - and under `pipefail` that becomes the function's status even though it emitted
    # every version it found. Both callers read this in a `$( )` and ignore the status; a future
    # one writing `rel_version_tags || return 1` would not.
    return 0
}

# Whether publishing $1 should move GitHub's "Latest release" badge, i.e. whether it is the
# highest version this repository has ever released.
#
# `gh release create` defaults `make_latest` to true. Left at that default, a 5.3.2 patch
# published after 5.4.0 takes the badge and flips the README's `shields.io/github/v/release`,
# announcing a patch of the *previous* minor as the current release (#1702).
#
# A pre-release is never Latest, and pre-releases are not in the comparison: a 5.5.8 patch
# published while 6.0 is in alpha is the highest GA, and GitHub would not give the badge to a
# pre-release anyway.
rel_is_latest() {
    local version="$1" tag
    rel_is_prerelease "$version" && return 1
    while read -r tag; do
        [ -n "$tag" ] || continue
        rel_is_prerelease "$tag" && continue
        rel_version_gt "$tag" "$version" && return 1
    done <<< "$(rel_version_tags)"
    return 0
}

# The released version below $1 its notes start at, or empty when nothing came before it. The
# lower boundary of the changelog fallback below.
#
# A GA starts at the highest GA below it: pre-releases are ignored, so 6.0.0 starts at 5.5.7 and
# its notes cover the whole of 6.0. A pre-release starts at the previous pre-release of its own
# X.Y, alpha or beta, legacy shape or not, or at the highest GA below it when it is the first:
# `6.0-alpha.0` starts at 5.5.7, `6.0-beta.0` at the last alpha, `6.0.0-beta.7` at `6.0-beta.6`.
rel_previous_version() {
    local version="$1" tag best="" best_pre=""
    while read -r tag; do
        [ -n "$tag" ] || continue
        rel_version_gt "$version" "$tag" || continue
        if rel_is_prerelease "$tag"; then
            rel_is_prerelease "$version" || continue
            # Same X.Y, from the key: what precedes the first hyphen is `6.0` in the legacy
            # `6.0-beta.5` and `6.0.0` in `6.0.0-beta.6`.
            [ "$(rel_major_minor "$tag")" = "$(rel_major_minor "$version")" ] || continue
            if [ -z "$best_pre" ] || rel_version_gt "$tag" "$best_pre"; then
                best_pre="$tag"
            fi
        elif [ -z "$best" ] || rel_version_gt "$tag" "$best"; then
            best="$tag"
        fi
    done <<< "$(rel_version_tags)"
    printf '%s' "${best_pre:-$best}"
}

# Whether $1 is a GA following pre-releases of its own X.Y.Z - `6.0.0` after `6.0.0-beta.N`, or
# after the legacy `6.0-beta.N`, whose patch is 0. Its notes cover everything since the previous
# GA, not just since the last beta.
rel_follows_prereleases() {
    local version="$1" xyz tag
    rel_is_prerelease "$version" && return 1
    xyz="$(rel_version_key "$version" | cut -d' ' -f1-3)" || return 1
    while read -r tag; do
        [ -n "$tag" ] || continue
        rel_is_prerelease "$tag" || continue
        [ "$(rel_version_key "$tag" | cut -d' ' -f1-3)" = "$xyz" ] && return 0
    done <<< "$(rel_version_tags)"
    return 1
}

# ---------------------------------------------------------------------------------------------
# Yontrack
# ---------------------------------------------------------------------------------------------

# Runs a GraphQL query against Yontrack and echoes the response body.
#
# Fails when the transport fails, when the status is not 2xx, or when the payload carries
# GraphQL errors - a 200 with an `errors` array is how an expired token looks.
rel_graphql() {
    local query="$1" payload response body status errors
    payload="$(jq -nc --arg q "$query" '{query: $q}')" || return 1

    response="$(curl -sS --max-time 30 -w '\n%{http_code}' \
        -X POST "${YONTRACK_URL%/}/graphql" \
        -H 'Content-Type: application/json' \
        -H "X-Ontrack-Token: ${YONTRACK_TOKEN:-}" \
        -d "$payload" 2>&1)" || {
        rel_fail "Could not reach ${YONTRACK_URL%/}/graphql: $response"
        return 1
    }

    status="${response##*$'\n'}"
    body="${response%$'\n'*}"

    case "$status" in
        2*) ;;
        *) rel_fail "Yontrack answered HTTP $status: $body"; return 1 ;;
    esac

    errors="$(echo "$body" | jq -r '(.errors // []) | map(.message) | join("; ")')"
    [ -n "$errors" ] && { rel_fail "Yontrack answered with errors: $errors"; return 1; }

    echo "$body"
}

# Everything the release needs off the build, in one call: the commit it targets and the CI run
# that holds its docs artefact.
#
# The run is read from the GitHub workflow run property, not from run info: `yontrack ci config`
# sets it on every build and records the run id as a *number*, ready for the `gh` API, where a
# URL would have to be parsed. See doc/dev-guide/docs-artifact.md.
rel_build_details() {
    rel_graphql "query {
        build(id: $1) {
            name
            displayName
            gitCommitProperty { value }
            buildGitHubWorkflowRunProperty { value }
        }
    }"
}

# The id of the build released as $1, or nothing when there is none: promoted to RELEASE, with
# $1 as its `release` property. Across the project and not on REL_BRANCH: the previous GA may
# have been released from another branch - 5.5.8 from `release/5.5` for a 6.0.0 on `main`.
rel_released_build_id() {
    rel_graphql "query {
        builds(project: \"$REL_PROJECT\", buildProjectFilter: {
            promotionName: \"RELEASE\",
            property: \"net.nemerosa.ontrack.extension.general.ReleasePropertyType\",
            propertyValue: \"$1\",
            maximumCount: 1
        }) { id }
    }" | jq -r '.data.builds[0].id // empty'
}

# ---------------------------------------------------------------------------------------------
# Guards
# ---------------------------------------------------------------------------------------------

# Indirections, so the tests can drive the guards without a registry or a tag database.

# The repository's tags: all of them, or just the one named. The workflow checks out with
# `fetch-depth: 0`, so every tag the repository has is here.
rel_git_tags() {
    if [ $# -gt 0 ]; then
        git tag -l "$1"
    else
        git tag -l
    fi
}

# The commits in a range, as a Markdown list. An indirection like the two above, so the changelog
# fallback can be driven without a real history.
rel_git_log() { git log --no-merges --pretty=format:'* %s (%h)' "$1..$2"; }

# The HTTP status of a Docker Hub tag: 200 when published, 404 when not.
rel_docker_status() {
    curl -sS --max-time 30 -o /dev/null -w '%{http_code}' \
        "https://hub.docker.com/v2/repositories/$1/tags/$2"
}

# Refuses a version that has already been published.
#
# Silently overwriting a published tag is the worst failure mode in this design, and it costs
# ten lines to make impossible. In practice it also self-corrects - `writeVersion` derives the
# base version from `git tag -l`, so publishing 5.3.0 makes the next main build compute 5.3.1 -
# but nothing *enforces* that, which is the whole point of checking.
rel_check_unpublished() {
    local version="$1" tag repo status

    tag="$(rel_git_tags "$version")" || return 1
    [ -n "$tag" ] && {
        rel_fail "Git tag $version already exists: this version has already been released."
        return 1
    }

    for repo in $REL_DOCKER_REPOSITORIES; do
        status="$(rel_docker_status "$repo" "$version")" || {
            rel_fail "Could not ask Docker Hub about $repo:$version."
            return 1
        }
        case "$status" in
            404) ;;
            200)
                rel_fail "$repo:$version is already on Docker Hub: this version has already been released."
                return 1
                ;;
            *)
                rel_fail "Docker Hub answered HTTP $status for $repo:$version."
                return 1
                ;;
        esac
    done

    return 0
}

# ---------------------------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------------------------

rel_resolve() {
    # Before anything else, the version included: a `v<N>` build is a development build whatever
    # it is named, and nothing about it is worth looking up.
    rel_releasable_branch "$REL_BRANCH" || {
        rel_fail "Cannot release from the branch $REL_BRANCH: only main and a release branch (release/X.Y or release-X.Y) are released. A v<N> major branch is never released - see doc/dev-guide/major-branch.md."
        return 1
    }

    local rc_version="${RELEASE_BUILD_VERSION:-}"
    [ -n "$rc_version" ] || { rel_fail "RELEASE_BUILD_VERSION is not set: nothing to release."; return 1; }

    # The GOLD workflow can only pass the version: `${build}` renders the build's display name,
    # which is the release property, and no templating source exposes the raw name. So the
    # version is resolved back to a build here, by the same lookup the demo workflows use.
    local json build_id build_name
    json="$(yontrack_build_by_display_name "$REL_PROJECT" "$REL_BRANCH" "$rc_version")" || return 1
    [ -z "$json" ] && {
        rel_fail "No build named $rc_version in $REL_PROJECT/$REL_BRANCH."
        return 1
    }
    build_id="$(echo "$json" | jq -r '.Id // empty')"
    build_name="$(echo "$json" | jq -r '.Name // empty')"
    [ -z "$build_name" ] && { rel_fail "Could not read a build out of: $json"; return 1; }

    local version="${RELEASE_VERSION:-}"
    local base_version
    base_version="$(rel_base_version "$rc_version")"
    [ -n "$version" ] || version="$base_version"
    # The image is re-tagged, never rebuilt, and it displays the base version it was stamped with
    # at build time (see docs/adr/0006). Publishing it under anything else ships an application
    # that shows a version it was not released as.
    [ "$version" = "$base_version" ] || {
        rel_fail "Cannot publish $rc_version as $version: its image was built as $base_version and displays that version. Only $base_version can be published from this build."
        return 1
    }
    rel_valid_version "$version" || {
        rel_fail "'$version' is not a version. A build with no release property has its own name as its display name, which is not something to publish under."
        return 1
    }

    local details sha run_id
    details="$(rel_build_details "$build_id")" || return 1
    sha="$(echo "$details" | jq -r '.data.build.gitCommitProperty.value.commit // empty')"
    [ -z "$sha" ] && { rel_fail "Build $build_name records no Git commit: there is nothing to tag."; return 1; }

    # Picked by workflow name rather than by position: `configureBuild` writes one entry today,
    # but the property is a list and reading [0] would silently pick the wrong run the day
    # anything else records itself against a build.
    run_id="$(echo "$details" \
        | jq -r '[(.data.build.buildGitHubWorkflowRunProperty.value.workflows // [])[] | select(.name == "CI")][0].runId // empty')"
    [ -z "$run_id" ] && {
        rel_fail "Build $build_name records no CI workflow run: the docs artefact cannot be found without one."
        return 1
    }

    rel_check_unpublished "$version" || return 1

    # Decided here, next to the other tag-driven guard, because it has to reach
    # `gh release create` as an explicit `--latest=<true|false>` and `resolve` is what feeds that
    # step. See `rel_is_latest`.
    local latest=false
    rel_is_latest "$version" && latest=true
    local prerelease=false
    rel_is_prerelease "$version" && prerelease=true
    local wiki_page
    wiki_page="$(rel_wiki_page "$version")"

    rel_log "Releasing $rc_version (build $build_name) as $version, from CI run $run_id on $sha."
    rel_log "Latest release: $latest."
    rel_log "Pre-release: $prerelease."
    rel_log "Release notes: $wiki_page."
    rel_output version "$version"
    rel_output rc_version "$rc_version"
    rel_output build "$build_name"
    rel_output build_id "$build_id"
    rel_output run_id "$run_id"
    rel_output sha "$sha"
    rel_output latest "$latest"
    rel_output prerelease "$prerelease"
    rel_output wiki_page "$wiki_page"
    rel_summary "Releasing \`$rc_version\` as \`$version\` from [CI run $run_id](${GITHUB_SERVER_URL:-https://github.com}/$REL_REPOSITORY/actions/runs/$run_id)."
    return 0
}

# The docs artefact, checked before anything is published.
#
# 90 days of artefact retention is a real release deadline: a build left waiting for GOLD longer
# than that loses its documentation. Publishing a release with no docs is worse than not
# publishing, and re-running CI is the deliberate, human-triggered exception to "nothing is
# rebuilt at release time" - so the message names the one thing that fixes it.
rel_docs() {
    local run_id="${REL_RUN_ID:-}" version="${REL_VERSION:-}" sha="${REL_SHA:-}"
    [ -n "$run_id" ] || { rel_fail "REL_RUN_ID is not set: no run to look in."; return 1; }

    local artifacts id
    artifacts="$(gh api "repos/$REL_REPOSITORY/actions/runs/$run_id/artifacts?per_page=100")" || {
        rel_fail "Could not list the artefacts of run $run_id."
        return 1
    }

    # An expired artefact and one that was never uploaded are the same problem to whoever has
    # to fix it, and they have the same fix.
    id="$(echo "$artifacts" \
        | jq -r '[.artifacts[]? | select(.name == "docs-site" and .expired == false)][0].id // empty')"
    [ -z "$id" ] && {
        rel_fail "docs artifact expired for $version; re-run CI on $sha to regenerate"
        return 1
    }

    rel_log "Docs artefact $id found on run $run_id."
    rel_output artifact_id "$id"
    return 0
}

# The wiki release page: written by a human before GOLD, checked here.
#
# `WIKI` is a validation on the build like the other three, but it records a check rather than a
# publication - the page is what makes the GitHub release worth opening, and the release links
# to it, so a missing or unreachable page makes the release a dead end.
#
# Checked for every release, a pre-release included (#2070): its page is its stage's,
# `Release-6.0.0-Beta`, renamed from `Release-6.0.0-Alpha` when the betas start. WIKI is reported
# whatever the stage, so RELEASE follows, and so does everything keyed off it - self.dev keeps an
# alpha out with an admission rule of its own. See `.yontrack/ci.yaml`.
rel_wiki() {
    local version="${REL_VERSION:-}" dir="${REL_WIKI_DIR:-wiki}" page
    [ -n "$version" ] || { rel_fail "REL_VERSION is not set: no page to look for."; return 1; }
    page="$(rel_wiki_page "$version")" || { rel_fail "'$version' is not a version: no page to look for."; return 1; }

    [ -f "$dir/$page.md" ] || {
        rel_fail "The wiki has no $page.md. Write the release notes - or rename the previous stage's page - before granting GOLD."
        return 1
    }

    # The link target, not the version: the archive pages name versions in prose, so matching
    # the version alone would pass on a sentence mentioning it.
    grep -qF "]($page)" "$dir/Home.md" || {
        rel_fail "$page.md is not linked from Home.md: a page nobody can reach from the index is a page nobody reads."
        return 1
    }

    rel_log "The wiki has $page.md and links it from the index."
    return 0
}

# The changelog since the last released build.
#
# `--to` is given explicitly because this workflow has no build context of its own: it must not
# register a build, and without `--to` the CLI reads the id out of the environment a build
# registration would have left behind.
#
# The previous RELEASE build of the branch is the boundary - `6.0.0-beta.6` from `6.0.0-beta.5`,
# `6.0.1` from `6.0.0` - except for a GA following pre-releases of its own X.Y.Z: the last
# RELEASE there is a beta, and `6.0.0` covers everything since 5.5.x. That one measures from the
# previous GA's build, and fails when it cannot be found, so that the git range from the previous
# GA's tag stands in rather than the changelog since the last beta.
rel_changelog() {
    local version="${REL_VERSION:-}" previous from
    local -a boundary=(--from-promotion RELEASE)
    if rel_follows_prereleases "$version"; then
        previous="$(rel_previous_version "$version")"
        [ -n "$previous" ] || return 1
        from="$(rel_released_build_id "$previous")" || return 1
        [ -n "$from" ] || return 1
        boundary=(--from "$from")
    fi
    yontrack build changelog export \
        --to "${REL_BUILD_ID:-}" \
        "${boundary[@]}" \
        --format markdown
}

# The commits between the previous release and this one.
#
# The fallback for a patch: a freshly cut `release/5.3` has no previously RELEASE-promoted build
# to measure from - 5.3.1 was released from a build on `main` - so `--from-promotion RELEASE`
# comes back empty, on the one kind of release where "what changed" is the whole point. The git
# range IS the changelog there: it is literally the cherry-picks (#1702). Also the fallback of a
# GA whose previous GA's build cannot be found, measured from that GA's tag.
#
# Fails, rather than inventing a range, when there is no previous release or no commit to measure
# to. `release.yml` checks out with `fetch-depth: 0`, so every tag and ref is present when there
# is one.
rel_git_changelog() {
    local version="${REL_VERSION:-}" sha="${REL_SHA:-}" previous
    [ -n "$sha" ] || return 1
    previous="$(rel_previous_version "$version")"
    [ -n "$previous" ] || return 1
    rel_git_log "$previous" "$sha"
}

# The GitHub release body: the wiki link first, the changelog second.
#
# End-user prose lives in the wiki, internals live in the release where developers look for
# them, and neither is duplicated. The order is the convention: whoever opens the release sees
# the readable page first. One shape for every release, alpha, beta and GA alike (#2070): only
# the page differs.
rel_body() {
    local version="${REL_VERSION:-}" page changelog
    [ -n "$version" ] || { rel_fail "REL_VERSION is not set: nothing to describe."; return 1; }
    page="$(rel_wiki_page "$version")" || { rel_fail "'$version' is not a version: nothing to describe."; return 1; }

    # A changelog that cannot be computed must not sink the release. The first release after
    # this pipeline lands has no previous RELEASE build to measure from, and the part a human
    # wrote - the wiki page - is there either way.
    #
    # Yontrack's changelog is preferred whenever it says anything: it carries the issue links and
    # the semantic sections, which a raw git log does not. Only when it comes back empty - or
    # fails, which reads the same to whoever opens the release - does the git range stand in.
    changelog="$(rel_changelog 2>/dev/null)" || changelog=""
    if [ -z "$changelog" ]; then
        changelog="$(rel_git_changelog 2>/dev/null)" || changelog=""
    fi

    printf '## Release notes\n\n'
    printf '%s/%s/wiki/%s\n\n' "${GITHUB_SERVER_URL:-https://github.com}" "$REL_REPOSITORY" "$page"
    rel_body_changes "$changelog"
    return 0
}

# The `Changes` section of the release body, saying so when there is nothing to show.
rel_body_changes() {
    printf '## Changes\n\n'
    if [ -n "$1" ]; then
        printf '%s\n' "$1"
    else
        printf '_No changelog available._\n'
    fi
}

# The GitHub release, with the body above as its notes.
#
# `--target` is the commit the build was made from, read off the build itself: release.yml runs
# on `main` as it is today, which is not where the release is.
#
# `--latest` is spelled out because `gh` leaves GitHub's `make_latest` at its default of true,
# and a 5.3.2 patch published after 5.4.0 would take the repository's "Latest release" badge and
# flip the README's shields.io version (#1702). Every alpha and beta is `--prerelease`, and never
# Latest: a beta published after 5.5.8 must not take the badge (#1987). Both decided here, from the
# version and the tags, by the same predicates `resolve` reported them with - the tag list has not
# moved in between, since the tag is what this step creates.
rel_publish() {
    local version="${REL_VERSION:-}" sha="${REL_SHA:-}" latest=false body
    local -a prerelease=()
    [ -n "$version" ] || { rel_fail "REL_VERSION is not set: nothing to publish."; return 1; }
    [ -n "$sha" ] || { rel_fail "REL_SHA is not set: nothing to tag."; return 1; }
    rel_is_latest "$version" && latest=true
    rel_is_prerelease "$version" && prerelease=(--prerelease)

    body="$(rel_body)" || return 1
    printf '%s\n' "$body"
    # `${a[@]+...}`: an empty array is an unbound variable to bash 3.2 under `set -u`, and
    # release-test.sh is run by hand on macOS too.
    printf '%s\n' "$body" | gh release create "$version" \
        --title "$version" \
        --target "$sha" \
        --latest="$latest" \
        ${prerelease[@]+"${prerelease[@]}"} \
        --notes-file -
}

# The wiki page recorded on the build, as the `wiki-page` meta-info item: what the `#releases`
# announcement links, `Release-6.0.0-Beta` for a beta, rather than a page name rebuilt from the
# version in a template. `append` keeps the build's other items - `rc-version` among them.
rel_meta() {
    local build_id="${REL_BUILD_ID:-}" version="${REL_VERSION:-}" page answer errors
    [ -n "$build_id" ] || { rel_fail "REL_BUILD_ID is not set: no build to record the page on."; return 1; }
    page="$(rel_wiki_page "$version")" || { rel_fail "'$version' is not a version: no page to record."; return 1; }

    answer="$(rel_graphql "mutation {
        setBuildMetaInfoPropertyById(input: {
            id: $build_id,
            append: true,
            items: [{name: \"wiki-page\", value: \"$page\"}]
        }) { errors { message } }
    }")" || return 1
    errors="$(echo "$answer" | jq -r '(.data.setBuildMetaInfoPropertyById.errors // []) | map(.message) | join("; ")')"
    [ -n "$errors" ] && { rel_fail "Could not record the wiki page on build $build_id: $errors"; return 1; }

    rel_log "Recorded wiki-page=$page on build $build_id."
    return 0
}

rel_main() {
    case "${1:-}" in
        resolve) rel_resolve ;;
        docs) rel_docs ;;
        wiki) rel_wiki ;;
        body) rel_body ;;
        publish) rel_publish ;;
        meta) rel_meta ;;
        *)
            rel_fail "Usage: $0 resolve|docs|wiki|body|publish|meta"
            return 1
            ;;
    esac
}

# Sourced by the test suite, which wants the functions and nothing else.
if [ -n "${RELEASE_LIB_ONLY:-}" ]; then
    return 0
fi

rel_main "$@"
