import Link from "next/link"
import {Space} from "antd"
import JourneyChip from "@components/extension/environments/shared/JourneyChip"
import {decorationJourneyEntries} from "@components/extension/environments/journey/buildJourneyModel"
import {slotUri} from "@components/extension/environments/EnvironmentsLinksUtils"

/**
 * Where a build is, beside the build - on a branch's build list and on the pipeline view's timeline.
 *
 * Since #1794 it is the journey chip the build page's strip is made of, in its compact form: the
 * environment's icon and the state, with the environment named in the tooltip. A bare icon said
 * only "this build has something to do with this environment"; a chip says *what*.
 *
 * Every stub the server sends is a slot the build is deployed in - see `decorationJourneyEntries` -
 * so every chip here reads `Deployed`. That is not a redundancy: the decoration sits in a list where
 * most builds carry none at all, and a build carrying one is the exception being pointed at.
 *
 * The chip is a **link to the slot page**, not a drawer opener: a decoration is drawn on pages which
 * host no drawer, and a link works on every one of them.
 */
export default function BuildEnvironmentsDecorations({decoration}) {

    const entries = decorationJourneyEntries(decoration?.data)

    return (
        <Space size={0} wrap>
            {
                entries.map(entry => (
                    <Link
                        key={entry.slot.id}
                        href={slotUri(entry.slot)}
                        data-testid={`build-decoration-${entry.slot.id}`}
                    >
                        <JourneyChip entry={entry} showSlot={false} showIcon/>
                    </Link>
                ))
            }
        </Space>
    )
}
