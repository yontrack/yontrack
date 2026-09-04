// @ts-check

import {NONE_STATUS_ID} from "@components/validationRuns/ValidationRunStatusConfig";

/**
 * What a pipeline card says about a build, worked out without React.
 *
 * These are the decisions the view would otherwise make inline in JSX, where the degradation cases -
 * no promotions, no validations, no release property - are the ones nobody looks at.
 */

/**
 * The version to show for a build, or `null` when there is none to show.
 *
 * `displayName` falls back to `name` on the backend, so for the many projects which never set a
 * release property the two are the same string. Printing it twice tells the user nothing, so the
 * version is dropped and the card collapses to one line.
 *
 * @param {{name?: string, displayName?: string}} [build]
 * @returns {string|null}
 */
export function buildVersion(build) {
    if (!build?.displayName) return null
    return build.displayName === build.name ? null : build.displayName
}

/**
 * The promotion medals to draw on a timeline card, highest first to be dropped last.
 *
 * "Highest" is the order the branch declares, not the order the runs arrive in and not the level's
 * id: promotion levels carry an ordinal position, and the branch's own list is in it.
 *
 * @param {Array<{promotionLevel?: {id: any}}>} [promotionRuns] The build's last run per level.
 * @param {Array<{id: any}>} [promotionLevels] The branch's levels, lowest first.
 * @param {number} [max] How many medals fit on a card.
 * @returns {{shown: Array<any>, overflow: number}} `shown` is in branch order, lowest first, so a
 *   row of cards reads consistently; `overflow` is what did not fit.
 */
export function topPromotionRuns(promotionRuns, promotionLevels, max = 3) {
    const order = new Map((promotionLevels ?? []).map((level, index) => [String(level.id), index]))
    const ranked = [...(promotionRuns ?? [])]
        // A run whose level is not in the branch's list cannot be placed; it sorts lowest rather
        // than being dropped, because hiding a promotion the build genuinely has would be worse.
        .sort((a, b) =>
            (order.get(String(a.promotionLevel?.id)) ?? -1) - (order.get(String(b.promotionLevel?.id)) ?? -1)
        )
    const overflow = Math.max(0, ranked.length - max)
    return {shown: ranked.slice(overflow), overflow}
}

/**
 * Restricts a build's validations to the selected validation stamp filter.
 *
 * This is the existing, server-stored, per-branch answer to "we have 15 validations", shared with
 * the legacy view so a user's filter follows them across views. With no filter selected, everything
 * shows: an unfiltered branch is not a filtered branch with an empty filter.
 *
 * @param {Array<{validationStamp?: {name?: string}}>} [validations]
 * @param {{vsNames?: Array<string>}} [selectedFilter]
 */
export function filterValidations(validations, selectedFilter) {
    const all = validations ?? []
    if (!selectedFilter?.vsNames) return all
    return all.filter(validation => selectedFilter.vsNames.includes(validation.validationStamp?.name))
}

/**
 * The status of a validation's most recent run, or `NONE` when it has never run.
 *
 * @param {{validationRuns?: Array<{lastStatus?: {statusID?: {id?: string}}}>}} [validation]
 * @returns {string}
 */
export function validationStatusId(validation) {
    return validation?.validationRuns?.[0]?.lastStatus?.statusID?.id ?? NONE_STATUS_ID
}

/**
 * The validation strip of a timeline card: one bar per stamp, plus how many passed.
 *
 * Returns no bars at all rather than a row of empty ones when the build has no validations. An empty
 * strip would claim the branch has stamps this build never ran, which is a different thing from the
 * build having nothing to say.
 *
 * @param {Array<any>} [validations] Already restricted to the active filter.
 * @returns {{bars: Array<{key: string, validationStamp: any, statusId: string}>, passed: number, total: number}}
 */
export function validationStrip(validations) {
    const bars = (validations ?? []).map(validation => ({
        key: String(validation.validationStamp?.id ?? validation.validationStamp?.name),
        validationStamp: validation.validationStamp,
        statusId: validationStatusId(validation),
    }))
    return {
        bars,
        passed: bars.filter(bar => bar.statusId === 'PASSED').length,
        total: bars.length,
    }
}

/**
 * The lowest promotion level the build has NOT reached, which is where a promote affordance belongs.
 *
 * Returns `null` once every level is reached: an affordance for nothing is worse than none.
 *
 * @param {Array<{id: any}>} [promotionLevels] The branch's levels, lowest first.
 * @param {Array<{promotionLevel?: {id: any}}>} [promotionRuns] The build's runs.
 */
export function nextPromotionLevel(promotionLevels, promotionRuns) {
    const reached = new Set((promotionRuns ?? []).map(run => String(run.promotionLevel?.id)))
    return (promotionLevels ?? []).find(level => !reached.has(String(level.id))) ?? null
}

/**
 * The decorations to draw on a timeline card, and how many did not fit.
 *
 * Decorations are the one element on a fixed-width card with no length ceiling of their own: any
 * extension may contribute one, and how many an instance has is a deployment fact core cannot know.
 * So the row is clipped like the promotion medals are.
 *
 * Unlike promotion levels, decorations carry no ordinal. There is nothing to rank on - core has no
 * business deciding that one extension's decoration matters more than another's - so the order the
 * backend returns is the order shown, and the ones that overflow are the last of it.
 *
 * The ceiling is FOUR rather than three because a deployed build already carries three: the build
 * link, the release, and the environments one. At three, the environments decoration - the one this
 * row was added for - would be the first thing dropped by any extension contributing a fourth, and
 * it sorts last so it would be dropped first. Four is headroom, not a limit anyone reasoned to.
 *
 * It counts decorations CONTRIBUTED, not glyphs drawn, and the two differ: `BuildLinkDecorationExtension`
 * contributes one for every build and draws nothing when the build has no links. So a link-less build
 * spends a slot on an invisible decoration, and `+1` can name something that would have drawn nothing.
 * Counting glyphs instead would mean core interpreting each extension's `data` to guess what it will
 * render, which is exactly the coupling the decoration seam exists to prevent - so it counts what it
 * can honestly see.
 *
 * @param {Array<{decorationType?: string}>} [decorations] The build's decorations, in server order.
 * @param {number} [max] How many decorations fit on a card.
 * @returns {{shown: Array<any>, overflow: number}}
 */
export function visibleDecorations(decorations, max = 4) {
    const all = decorations ?? []
    return {shown: all.slice(0, max), overflow: Math.max(0, all.length - max)}
}
