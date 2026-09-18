import {Space, Typography} from "antd"
import JourneyChip from "@components/extension/environments/shared/JourneyChip"

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
 */
export default function BuildJourneyStrip({journey, onSlotClick}) {

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
