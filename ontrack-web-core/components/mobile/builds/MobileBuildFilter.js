"use client"

/**
 * Finding a build on a branch, from a phone.
 *
 * **Two controls, and deliberately only two**: the build's name, and one
 * promotion level. `StandardBuildFilter` supports a dozen more - dates, build
 * links, properties, `since*` variants, validation stamps - and `BuildFilterDialog`
 * is what showing all of them looks like. Reproducing that on a phone is the
 * trap this component exists to avoid: someone reaching for their phone is
 * looking for *a build they can name*, or for the last one that reached a
 * promotion. Everything else is a desktop question.
 *
 * Both controls map straight onto the existing filter, so there is no server
 * work behind this screen:
 *
 * - the name becomes `withDisplayName`, escaped to a literal by
 *   `buildDisplayNamePattern` - the argument is a regular expression, and a
 *   version is mostly dots;
 * - the promotion becomes `withPromotionLevel`, which the repository matches as
 *   `PL.NAME = ?`. Exact, which is why the control is a list of the branch's own
 *   levels rather than a second text box.
 *
 * `count` is the third field the filter offers and this screen does not send it:
 * on the paginated path `standardFilterPagination` sizes the page from the
 * `size` argument and never reads `count`. The cap the phone needs is already
 * there, expressed as `MOBILE_BUILD_PAGE_SIZE`; sending a `count` beside it
 * would be a number with no effect that a later reader would trust.
 */

import {useState} from "react"
import {Select} from "antd"
import {MobileFilterInput, useMobileFilter} from "@components/mobile/entities/MobileFilter"
import {buildDisplayNamePattern} from "@components/mobile/entities/namePatterns"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import {MEDAL_SIZE} from "@components/mobile/builds/MobileBuildCard"

/**
 * What to say when a filter matched nothing.
 *
 * One sentence per shape of filter rather than one generic "no match": the
 * difference between "no build is called that" and "no build got that far" is
 * the difference between retyping and going somewhere else, and it is the only
 * thing the screen can tell the user at this point.
 *
 * A pure function so the wording can be tested without the screen.
 *
 * @param {string} [name] The trimmed text the user typed, if any.
 * @param {string} [promotionLevel] The promotion level's name, if one is picked.
 * @returns {string}
 */
export function noMatchingBuilds({name, promotionLevel}) {
    if (name && promotionLevel) return `No build named "${name}" has been promoted to ${promotionLevel}.`
    if (name) return `No build matches "${name}".`
    return `No build has been promoted to ${promotionLevel}.`
}

/**
 * The state behind the two controls.
 *
 * @returns {{
 *   byName: Object,
 *   promotionLevel: string|null,
 *   setPromotionLevel: function,
 *   filtering: boolean,
 *   input: Object|null,
 *   signature: string,
 *   noMatch: string|undefined,
 * }} `input` is the `StandardBuildFilter` to send, and `null` when nothing is
 *   filtered - so an unfiltered screen asks the exact query it asked before this
 *   filter existed. `signature` changes whenever what is being asked for
 *   changes, and is what a caller depends and pages off.
 */
export function useMobileBuildFilter() {

    // The typing, the debounce and the trim, shared with the project and branch
    // list filters - see `useMobileFilter`.
    const byName = useMobileFilter()

    const [promotionLevel, setPromotionLevel] = useState(null)

    const withDisplayName = buildDisplayNamePattern(byName.filter)
    // Normalised here and only here: antd clears a `Select` to `undefined`, the
    // query wants a `null` it can send, and the signature below has to see one
    // value for "no promotion" rather than two.
    const withPromotionLevel = promotionLevel || null

    const filtering = Boolean(withDisplayName || withPromotionLevel)

    return {
        byName,
        promotionLevel: withPromotionLevel,
        setPromotionLevel,
        filtering,
        input: filtering ? {withDisplayName, withPromotionLevel} : null,
        /*
         * A string rather than the filter object, which is new on every render
         * and so would refetch the branch forever if a dependency array held it.
         * Serialised rather than concatenated: a build name and a promotion level
         * can each hold anything a user can type, so any separator picked by hand
         * is one two different filters could collide on.
         */
        signature: JSON.stringify([withDisplayName, withPromotionLevel]),
        noMatch: filtering
            ? noMatchingBuilds({name: byName.filter, promotionLevel: withPromotionLevel})
            : undefined,
    }
}

/**
 * The controls themselves.
 *
 * @param {Object} filter From {@link useMobileBuildFilter}.
 * @param {Array} [promotionLevels] The branch's promotion levels. The control
 *   is **absent** when there are none: `withPromotionLevel` is an exact name
 *   match, so a branch with no levels offers no choice at all, and an empty
 *   dropdown reads as something broken rather than as something inapplicable.
 */
export function MobileBuildFilterControls({filter, promotionLevels = []}) {
    return (
        <div className="ot-mobile-filters">
            <MobileFilterInput
                filter={filter.byName}
                // Not "by name": the cards are headed by the display name, which
                // is the release when there is one, and that is what this
                // matches.
                placeholder="Filter by build or version"
                label="Filter the builds by name"
                testId="mobile-builds-filter"
            />
            {
                promotionLevels.length > 0 &&
                <Select
                    allowClear
                    value={filter.promotionLevel}
                    // Straight through, `undefined` and all: the hook is where
                    // antd's way of saying "cleared" becomes the query's.
                    onChange={filter.setPromotionLevel}
                    placeholder="Any promotion"
                    aria-label="Filter the builds by promotion"
                    data-testid="mobile-builds-promotion"
                    // The control fills the column, like the box above it - a
                    // Select left to itself is sized for a desktop form row.
                    style={{width: '100%'}}
                    options={
                        promotionLevels.map(promotionLevel => ({
                            // The name, because that is what `withPromotionLevel`
                            // matches - the id would silently find nothing.
                            value: promotionLevel.name,
                            label: (
                                <span className="ot-mobile-inline">
                                    <PromotionLevelImage
                                        promotionLevel={promotionLevel}
                                        size={MEDAL_SIZE}
                                    />
                                    <span>{promotionLevel.name}</span>
                                </span>
                            ),
                        }))
                    }
                />
            }
        </div>
    )
}
