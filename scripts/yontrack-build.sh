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
