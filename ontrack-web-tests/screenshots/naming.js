/**
 * The `<version>-<slug>.png` file naming the wiki release notes expect.
 *
 * The convention is defined by the release-notes agent in the wiki checkout (issue #1670);
 * this is the half that produces files matching it.
 */

/**
 * Lower-case kebab. Single dashes only, and never leading or trailing, so that the version and
 * the slug stay legible either side of the joining dash.
 */
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/

/**
 * Loose enough for `5.3.0` and `5.3.0-rc-41`, strict enough that the result is a plain file
 * name: no separators, no whitespace, and no leading dot, so a version read from a workflow
 * input cannot write outside the output directory.
 */
const VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

/**
 * Checked on its own by the capture, before the browser is started: a bad version should fail
 * in a second rather than after the first shot.
 *
 * @returns {string} the version, unchanged
 */
const assertVersion = (version) => {
    if (!VERSION_PATTERN.test(version ?? '')) {
        throw new Error(`Not a usable version for a file name: ${JSON.stringify(version)}`)
    }
    return version
}

/** @returns {string} the slug, unchanged */
const assertSlug = (slug) => {
    if (!SLUG_PATTERN.test(slug ?? '')) {
        throw new Error(`Not a lower-case kebab slug: ${JSON.stringify(slug)}`)
    }
    return slug
}

/**
 * @param {string} version The version of the build the demo is running, e.g. `5.3.0`.
 * @param {string} slug The catalogue entry's slug, e.g. `dashboard`.
 * @returns {string} `<version>-<slug>.png`
 */
const screenshotFileName = (version, slug) => `${assertVersion(version)}-${assertSlug(slug)}.png`

module.exports = {screenshotFileName, assertVersion, SLUG_PATTERN}
