/**
 * What a phone user types into a filter, turned into what the server expects.
 *
 * Two of the mobile filters hand their text to a server-side **regular
 * expression** rather than to a substring match: `Project.branches(name:)` on
 * the project screen and `StandardBuildFilter.withDisplayName` on the branch
 * screen. Neither may be given the raw text. `release/1.0` would match
 * `release/1x0` on either - and a character the user meant literally, a lone `(`
 * say, breaks each of them in its own way:
 *
 * - `branches(name:)` reaches Postgres uncaught, so the pattern fails the whole
 *   query with an `INTERNAL_ERROR` and the project screen shows an error.
 * - `withDisplayName` is **caught**: the repository compiles it first and
 *   `StandardBuildFilterProvider.filterBranchBuildsWithPagination` turns the
 *   resulting `CoreBuildFilterInvalidException` into an empty page. Which is
 *   worse, not better - the screen says "No build matches" about a pattern that
 *   never ran.
 *
 * So both escape the typed text to a literal, which gives the case-insensitive
 * substring match a user moving between the two screens expects. They differ in
 * one thing only - who supplies the "case-insensitive" half - and that
 * difference is the server's, not a choice made here:
 *
 * | Argument          | Matched with | Case                       |
 * |-------------------|--------------|----------------------------|
 * | `branches(name:)` | `B.NAME ~ ?` | the pattern's, so `(?i)`   |
 * | `withDisplayName` | `… ~* ?`     | the operator's, so nothing |
 *
 * A pure module - no React - so the escaping can be tested on its own.
 */

/**
 * Every character that means something in a POSIX regular expression, and
 * therefore has to be escaped to stand for itself.
 */
const METACHARACTERS = /[\\^$.|?*+()[\]{}]/g

/**
 * The typed text as a pattern that matches exactly itself, anywhere.
 *
 * @param {string} [text] What the user typed.
 * @returns {string|null} `null` when nothing was typed - every caller must send
 *   `null` rather than `''`, because a blank pattern is not a filter and a
 *   screen must not call itself filtered when it is showing the whole list.
 */
function literalPattern(text) {
    const trimmed = typeof text === 'string' ? text.trim() : ''
    if (!trimmed) return null
    return trimmed.replace(METACHARACTERS, '\\$&')
}

/**
 * The `name` argument for `Project.branches`.
 *
 * `(?i)` is an embedded option, which Postgres' advanced regular expressions
 * support: the repository matches with `~`, which is case-sensitive, and a
 * phone user typing `MAIN` expects to find `main` - as they do on the project
 * list, whose filter is an `ILIKE`.
 *
 * @param {string} [text] What the user typed.
 * @returns {string|null} The pattern, or `null` when nothing was typed.
 */
export function branchNamePattern(text) {
    const pattern = literalPattern(text)
    return pattern && `(?i)${pattern}`
}

/**
 * The `withDisplayName` field of a `StandardBuildFilter`.
 *
 * No `(?i)`, unlike the branch filter above: the repository matches this one
 * with `~*`, which already ignores case, so the flag would be a prefix that
 * says nothing. It matches the build's **display name** - its release property
 * when it has one, its own name otherwise - which is what the mobile build
 * cards show, so the filter narrows by what the user can read.
 *
 * @param {string} [text] What the user typed.
 * @returns {string|null} The pattern, or `null` when nothing was typed.
 */
export function buildDisplayNamePattern(text) {
    return literalPattern(text)
}
