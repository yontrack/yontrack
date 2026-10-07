import {Space, Typography} from "antd"
import JourneyChip from "@components/extension/environments/shared/JourneyChip"
import WhatsMissing from "@components/readiness/WhatsMissing"
import {slotDisplayNameWithoutProject} from "@components/extension/environments/shared/slotCellModel"

/**
 * The journey states in which the build has not reached the slot yet, and for which "What's
 * missing" (#2023) has something to say: an approval still to give is as missing for a deployment
 * in progress as it is for a build not deployed at all.
 */
const notReachedStates = ['ELIGIBLE', 'NOT_ELIGIBLE', 'IN_PROGRESS']

/**
 * Where a build is, in one line: one chip per slot of its project, in environment order.
 *
 * This is the answer to "where is my build?", which the build page used to give as an expandable
 * timeline - one row per slot, each with its own deploy button, its own current build and its own
 * disclosure triangle. A developer asking the question wants to read it, not to operate it, so the
 * strip states the five journey states and leaves everything operational to the drawer behind a
 * chip.
 *
 * **Every slot of the project is drawn, including the ones refusing the build.** An environment
 * missing from the strip would be indistinguishable from an environment that does not exist, and
 * "why is this build not in production?" is precisely the question the strip is here to answer -
 * `Not eligible`, with the refusing rule in the chip's tooltip.
 *
 * @param {?Array} journey The entries of `Build.journey`, already in environment order
 * @param {?function} onSlotClick Called with a slot when a chip is clicked - the caller opens the
 *   slot drawer. Chips are inert when absent.
 * @param {?Object} build The build, with its `id`. When given, every slot the build has not reached
 *   yet gets a "What's missing" control beside its chip.
 */
export default function BuildJourneyStrip({journey, onSlotClick, build}) {

    const entries = journey ?? []

    if (entries.length === 0) {
        return (
            <Typography.Text type="secondary" data-testid="build-journey-empty">
                This project has no deployment slot.
            </Typography.Text>
        )
    }

    return (
        <Space size={[4, 8]} wrap data-testid="build-journey-strip">
            {
                entries.map(entry => (
                    <Space key={entry.slot.id} size={0}>
                        <JourneyChip
                            entry={entry}
                            onClick={onSlotClick}
                            showIcon
                        />
                        {
                            build && notReachedStates.includes(entry.state) &&
                            <WhatsMissing
                                build={build}
                                slot={entry.slot}
                                label={slotDisplayNameWithoutProject(entry.slot)}
                                testId={`build-readiness-slot-${entry.slot.id}`}
                            />
                        }
                    </Space>
                ))
            }
        </Space>
    )
}
