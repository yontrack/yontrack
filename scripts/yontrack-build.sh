#!/usr/bin/env bash
#
# Build lookup shared by scripts/demo-deploy.sh and scripts/demo-smoke.sh: one deploys the
# build a version names, the other reports a validation on it, and both have to find it the
# same way or they act on different builds.
#
# Sourced, never executed. The Yontrack CLI must already be installed and configured; needs
# 5.2.0 or later for `build search --with-display-name`.

# Turns a literal into a regex matching exactly it, and nothing else.
#
# `--with-display-name` is matched case-insensitively and *partially* by the server, so an
# unanchored "5.3.0-rc-4" would also match 5.3.0-rc-45 and act on the wrong build. Anchoring
# alone is not enough either: the dots in a version are regex wildcards.
yontrack_exact_pattern() {
    printf '^%s$' "$(printf '%s' "$1" | sed 's/[][^$.*+?(){}|\\]/\\&/g')"
}

# A build named explicitly, as {Id,Name,DisplayName}.
#
# A build's display name is its release property when it has one and its own name otherwise,
# so this matches whichever of the two a human would quote.
#
# Usage: yontrack_build_by_display_name PROJECT BRANCH NAME
yontrack_build_by_display_name() {
    yontrack build search \
        --project "$1" \
        --branch "$2" \
        --with-display-name "$(yontrack_exact_pattern "$3")" \
        --count 1 \
        --output json
}

# The meta-info item holding a build's rc version: the version CI built and pushed to GHCR as,
# `6.0-alpha.0-rc-562` on main. Unlike the `release` property, which release.yml rewrites to the
# base version when the build goes GOLD, nothing renames it, so the demo slot deploys it and the
# smoke test finds the build by it (#1999). .yontrack/ci.yaml reads it by this same name.
YONTRACK_RC_VERSION_META="rc-version"

# Records the rc version on a build, next to its other meta-info items. Called by ci.yml right
# after it sets the `release` property to the same value.
#
# Usage: yontrack_record_rc_version BUILD_ID VERSION
yontrack_record_rc_version() {
    # shellcheck disable=SC2016  # GraphQL variables, not shell ones
    yontrack graphql \
        --fail-on-user-errors \
        --query 'mutation($input: SetBuildMetaInfoPropertyByIdInput!) { setBuildMetaInfoPropertyById(input: $input) { errors { message } } }' \
        --vars-json "$(jq -cn --argjson id "$1" --arg name "$YONTRACK_RC_VERSION_META" --arg value "$2" \
            '{input: {id: $id, append: true, items: [{name: $name, value: $value}]}}')"
}

# The build CI built as this rc version, as {Id,Name,DisplayName}, or nothing when no build
# carries it - every build registered before the item existed.
#
# The server matches the value with `ilike` and no wildcard, so this is exact: a version holds
# no `%` and no `_`.
#
# Usage: yontrack_build_by_rc_version PROJECT BRANCH VERSION
yontrack_build_by_rc_version() {
    yontrack build search \
        --project "$1" \
        --branch "$2" \
        --with-property net.nemerosa.ontrack.extension.general.MetaInfoPropertyType \
        --with-property-value "$YONTRACK_RC_VERSION_META:$3" \
        --count 1 \
        --output json \
        --accept-not-found
}

# A build's rc version, or nothing when CI never recorded it. Rendered through the same template
# engine, and from the same item, as the demo slot's `image.tag`.
#
# Usage: yontrack_rc_version_of BUILD_ID
yontrack_rc_version_of() {
    local json
    json="$(yontrack graphql \
        --query "query(\$id: Int!) { build(id: \$id) { rcVersion: render(format: \"text\", template: \"\${build.meta?name=$YONTRACK_RC_VERSION_META}\") } }" \
        --vars-json "$(jq -cn --argjson id "$1" '{id: $id}')")" || return 1
    echo "$json" | jq -r '.build.rcVersion // empty'
}
