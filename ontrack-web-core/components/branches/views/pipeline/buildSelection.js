// @ts-check

/**
 * Which build the pipeline view has selected, decided without React and without the router so the
 * rules can be read - and tested - on their own.
 *
 * The invariant the whole view rests on: THE SELECTION IS ALWAYS VISIBLE. A selection pointing at a
 * build the timeline is not showing would leave the inspector describing something the user cannot
 * see, so a selection which drops out of the loaded page is replaced rather than kept.
 */

/**
 * Name of the query parameter naming the selected build.
 *
 * Live, like the `?view=` parameter beside it: it stays in the URL so any selection is linkable.
 */
export const buildSelectionParam = 'build'

/**
 * Ids are compared as strings throughout. GraphQL hands out `ID!` as a string and the query string
 * is a string, but a caller with a build object in hand often has a number, and a `===` between the
 * two would silently never match.
 *
 * @param {any} id
 * @returns {string|null}
 */
const asId = (id) => (id === undefined || id === null || id === '') ? null : String(id)

/**
 * Resolves the selected build.
 *
 * @param {Array<{id: any}>} [builds] Loaded builds, most recent first.
 * @param {any} [requestedId] What the URL, or the user, asked for.
 * @returns {string|null} The id of the build to select, or `null` when there is nothing to select.
 */
export function resolveSelectedBuildId(builds, requestedId) {
    if (!builds || builds.length === 0) return null
    const requested = asId(requestedId)
    if (requested && builds.some(build => asId(build.id) === requested)) return requested
    // No request, or a request naming a build which is not loaded: the most recent build is what a
    // branch is read from by default.
    return asId(builds[0].id)
}

/**
 * Whether another page of builds has to be loaded before a pending build can be selected.
 *
 * A stage card names the latest build at its level, which for an early level can sit far below the
 * loaded page. Clicking it loads up to it rather than doing nothing.
 *
 * @param {Array<{id: any}>} [builds] Loaded builds.
 * @param {any} [pendingId] Build being waited for, if any.
 * @param {{nextPage?: any}} [pageInfo] Page info of the last loaded page.
 */
export function needsMoreBuilds(builds, pendingId, pageInfo) {
    const pending = asId(pendingId)
    if (!pending) return false
    if ((builds ?? []).some(build => asId(build.id) === pending)) return false
    // Nothing more to load: the build was deleted, or the active filter excludes it. Either way the
    // wait ends here instead of spinning on an empty next page.
    return Boolean(pageInfo?.nextPage)
}

/**
 * Finds a loaded build by id, comparing ids the way the rest of this module does.
 *
 * @param {Array<{id: any}>} [builds]
 * @param {any} [id]
 */
export function findBuildById(builds, id) {
    const wanted = asId(id)
    if (!wanted) return null
    return (builds ?? []).find(build => asId(build.id) === wanted) ?? null
}
