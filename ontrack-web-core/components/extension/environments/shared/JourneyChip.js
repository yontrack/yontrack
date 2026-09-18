import {Tag, Tooltip} from "antd"
import SlotAdmissionRuleSummary from "@components/extension/environments/SlotAdmissionRuleSummary"
import {slotDisplayNameWithoutProject} from "@components/extension/environments/shared/slotCellModel"

/**
 * The five states of `BuildSlotJourney`, and how each one reads.
 *
 * The order of the map is the order the server's enum declares, which is how far the build got.
 * `color` is an antd `Tag` preset rather than a raw value so the chip follows the theme in both
 * light and dark.
 */
export const journeyStates = {
    DEPLOYED: {label: 'Deployed', color: 'success', tooltip: 'This build is deployed here now.'},
    SUPERSEDED: {label: 'Superseded', color: 'default', tooltip: 'This build was deployed here and a newer one has replaced it.'},
    IN_PROGRESS: {label: 'In progress', color: 'processing', tooltip: 'This build has a deployment under way here.'},
    ELIGIBLE: {label: 'Eligible', color: 'blue', tooltip: 'This build can be deployed here.'},
    NOT_ELIGIBLE: {label: 'Not eligible', color: 'warning', tooltip: 'This build cannot be deployed here.'},
}

/**
 * A build's state in one slot, in one chip.
 *
 * The shared atom of the build journey strip, the build decorations, the build search "Deployments"
 * column, `ProjectPromotionWidget` and the delivery map's slot checkpoints. Those consumers arrive
 * in later phases of the redesign; the chip ships first because every one of them has to say the
 * same five things in the same five words, and five surfaces each inventing their own wording is
 * exactly the state the redesign is undoing.
 *
 * **The reason lives in the tooltip, not on the chip.** A strip of chips is read by scanning, and
 * "Not eligible" plus a hover is a strip that can be scanned; "Not eligible: GOLD promotion is
 * required" is a paragraph per environment. The refusing rules come from the server as
 * `nonEligibleRules` and are drawn by the same rule components the deployment page uses.
 *
 * @param {Object} entry One entry of `Build.journey`, as `BuildJourneyData` describes it.
 * @param {function} onClick Called with the entry's slot - its consumers open the slot drawer. The
 *   chip is inert when absent.
 * @param {boolean} showSlot Whether to name the environment on the chip itself. The journey strip
 *   does (one chip per environment); a decoration beside a build does not.
 */
export default function JourneyChip({entry, onClick, showSlot = true}) {

    if (!entry) return null

    const state = journeyStates[entry.state] ?? {
        label: entry.state,
        color: 'default',
        tooltip: '',
    }

    const slotName = slotDisplayNameWithoutProject(entry.slot)

    const rules = entry.nonEligibleRules ?? []

    const tooltip =
        entry.state === 'NOT_ELIGIBLE' && rules.length > 0 ?
            <>
                {`${slotName}: not eligible.`}
                {
                    rules.map(rule => (
                        <div key={rule.id}>
                            <SlotAdmissionRuleSummary ruleId={rule.ruleId} ruleConfig={rule.ruleConfig}/>
                        </div>
                    ))
                }
            </> :
            `${slotName}: ${state.tooltip}`

    const activate = onClick ? () => onClick(entry.slot) : undefined

    return (
        <Tooltip title={tooltip}>
            <Tag
                color={state.color}
                data-testid={`journey-chip-${entry.slot?.id}`}
                data-state={entry.state}
                onClick={activate}
                style={{cursor: activate ? 'pointer' : undefined, marginInlineEnd: 4}}
            >
                {showSlot ? `${slotName} — ${state.label}` : state.label}
            </Tag>
        </Tooltip>
    )
}
