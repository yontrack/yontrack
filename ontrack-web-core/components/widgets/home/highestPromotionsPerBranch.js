// @ts-check

import {topPromotionRuns} from "@components/branches/views/pipeline/pipelineFacts";

/**
 * What the "Highest promotions per branch" widget shows, worked out without React (#2006).
 */

/** The formats of the widget, the first one being the default. */
export const HIGHEST_PROMOTIONS_FORMATS = [
    {label: 'Table', value: 'TABLE'},
    {label: 'Ladder', value: 'LADDER'},
]

export const DEFAULT_HIGHEST_PROMOTIONS_FORMAT = 'TABLE'

/** The maximum number of branches a pattern brings when its entry gives none. */
export const DEFAULT_PATTERN_COUNT = 5

/**
 * The run of the highest promotion of a build, or `null` when the build has none.
 *
 * "Highest" is the order the branch declares its levels in, not the order the runs arrive in and not
 * the level's id - the rule of `topPromotionRuns` in `pipelineFacts.js`, which a run whose level is
 * not in the branch's list ranks lowest rather than dropping. Promotions are not
 * cumulative: a build can have GOLD without SILVER, so only the runs the build has are ranked.
 *
 * @param {Array<{promotionLevel?: {id: any}}>} [promotionRuns] The build's last run per level.
 * @param {Array<{id: any}>} [promotionLevels] The branch's levels, lowest first.
 * @returns {any|null}
 */
export function highestPromotionRun(promotionRuns, promotionLevels) {
    return topPromotionRuns(promotionRuns, promotionLevels, 1).shown[0] ?? null
}

/**
 * Whether an entry of the configuration names one branch or a pattern on the names of the branches.
 *
 * Exactly one of `branch` and `pattern` is expected - the form says so. An entry carrying both, as
 * an imported YAML can, is taken as naming its branch: what is named exactly is the least surprising
 * reading.
 *
 * @param {{branch?: string, pattern?: string}} [entry]
 * @returns {'BRANCH'|'PATTERN'|null} `null` when the entry names neither.
 */
export function entryKind(entry) {
    if (entry?.branch) return 'BRANCH'
    if (entry?.pattern) return 'PATTERN'
    return null
}

/**
 * The rows of the widget, one per branch, in the order of the configuration.
 *
 * - The branches of a pattern come in the order the query returned them, the most recently active
 *   first.
 * - A branch reached twice - listed explicitly AND matched by a pattern - shows once, at its first
 *   position.
 * - An entry with nothing to show is never dropped silently: an explicit branch which does not exist
 *   or cannot be seen is `NOT_FOUND`, and a pattern matching nothing is a single `NO_MATCH` row.
 * - A branch without any build is `NO_BUILD`; any other branch is a `BUILD` row, whose latest build
 *   may have no promotion at all - `highestRun` is then `null`.
 *
 * @param {Array<{project: string, branch?: string, pattern?: string, count?: number}>} entries The
 *   `branches` of the configuration.
 * @param {Array<Array<any>|undefined>} branchesPerEntry For each entry, the branches it resolved to,
 *   each with its `promotionLevels` and `builds` - the latest one only.
 * @returns {Array<{key: string, type: 'BUILD'|'NO_BUILD'|'NOT_FOUND'|'NO_MATCH', entry: any,
 *   branch?: any, build?: any, highestRun?: any}>}
 */
export function highestPromotionsRows(entries, branchesPerEntry) {
    const rows = []
    const seen = new Set()
    ;(entries ?? []).forEach((entry, index) => {
        const branches = branchesPerEntry?.[index] ?? []
        if (branches.length === 0) {
            rows.push({
                key: `entry-${index}`,
                type: entryKind(entry) === 'PATTERN' ? 'NO_MATCH' : 'NOT_FOUND',
                entry,
            })
        }
        branches.forEach(branch => {
            const id = String(branch.id)
            if (seen.has(id)) return
            seen.add(id)
            const build = branch.builds?.[0]
            if (build) {
                rows.push({
                    key: `branch-${id}`,
                    type: 'BUILD',
                    entry,
                    branch,
                    build,
                    highestRun: highestPromotionRun(build.promotionRuns, branch.promotionLevels),
                })
            } else {
                rows.push({key: `branch-${id}`, type: 'NO_BUILD', entry, branch})
            }
        })
    })
    return rows
}

/**
 * The ladder of a branch: all its promotion levels, lowest first, each with whether the latest build
 * was granted it, and which one is its highest.
 *
 * Nothing below the highest level is taken as granted - promotions are not cumulative, and a gap in
 * the ladder is precisely what it is there to show.
 *
 * @param {Array<{id: any}>} [promotionLevels] The branch's levels, lowest first.
 * @param {Array<{promotionLevel?: {id: any}}>} [promotionRuns] The build's last run per level.
 * @returns {Array<{promotionLevel: any, run: any|null, granted: boolean, highest: boolean}>}
 */
export function promotionLadder(promotionLevels, promotionRuns) {
    const highest = highestPromotionRun(promotionRuns, promotionLevels)
    const runs = new Map((promotionRuns ?? []).map(run => [String(run.promotionLevel?.id), run]))
    return (promotionLevels ?? []).map(promotionLevel => {
        const run = runs.get(String(promotionLevel.id)) ?? null
        return {
            promotionLevel,
            run,
            granted: run !== null,
            highest: run !== null && run === highest,
        }
    })
}
