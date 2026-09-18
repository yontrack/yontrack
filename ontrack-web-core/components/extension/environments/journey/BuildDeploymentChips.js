import {Space} from "antd"
import {useQuery} from "@components/services/GraphQL"
import JourneyChip from "@components/extension/environments/shared/JourneyChip"
import {gqlBuildCurrentDeployments} from "@components/extension/environments/journey/buildJourneyGraphQL"
import {currentDeploymentJourneyEntries} from "@components/extension/environments/journey/buildJourneyModel"

/**
 * Where a build is deployed, in one cell: a journey chip per slot still holding it (#1796).
 *
 * This is what the build search "Deployments" column and `ProjectPromotionWidget` used to draw as a
 * bare environment icon linking to the environments page - the icon of the *highest* environment
 * only, with no word for what it meant and nothing to say whether the build was still there or on
 * its way. The chip says "production — Deployed" and opens the slot drawer, which is the same
 * sentence and the same drawer the journey strip, the decorations and the matrix use.
 *
 * **Every current deployment, not only the highest.** The column is titled "Deployments"; a project
 * with a `canary` slot beside its plain production one is deployed in two places, and picking one of
 * them to show was the predecessor's doing rather than the question's.
 *
 * It owns its query but *not* the drawer: a table draws one of these per row, and a drawer per row
 * would mean every row's drawer opening at once on `?slot=<id>`. The container hosts the single
 * drawer and passes `onSlotClick` down.
 *
 * @param {Object} build The build, `{id}` at the least
 * @param {?function} onSlotClick Called with the slot of the clicked chip. Chips are inert without
 *   it, which is what a surface with nowhere to put a drawer wants.
 * @param {?string} testId What to label the cell with. A surface drawing the same build twice - the
 *   build search table, whose summary rows repeat the builds picked for a change log - passes a
 *   distinct one for the second, so that a test locator addresses one of them rather than both.
 */
export default function BuildDeploymentChips({build, onSlotClick, testId}) {

    const {data} = useQuery(
        gqlBuildCurrentDeployments,
        {
            variables: {buildId: Number(build?.id)},
            deps: [build?.id],
            condition: !!build?.id,
            initialData: null,
            dataFn: data => data.build?.currentDeployments ?? [],
        }
    )

    // Nothing at all while the answer is on its way, and nothing when it says the build is deployed
    // nowhere: this sits in a table cell, where a spinner per row is noise and "Deployed nowhere"
    // repeated down the column is noise of a more confident kind.
    const entries = currentDeploymentJourneyEntries(data)
    if (entries.length === 0) return null

    return (
        <Space size={[4, 4]} wrap data-testid={testId ?? `build-deployments-${build.id}`}>
            {
                entries.map(entry => (
                    <JourneyChip
                        key={entry.slot.id}
                        entry={entry}
                        onClick={onSlotClick}
                        showIcon
                    />
                ))
            }
        </Space>
    )
}
