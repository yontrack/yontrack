import Link from "next/link"
import {Drawer, Space, Typography} from "antd"
import {useQuery} from "@components/services/GraphQL"
import EnvironmentIcon from "@components/extension/environments/EnvironmentIcon"
import {slotUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import SlotSummary from "@components/extension/environments/shared/SlotSummary"
import {slotDisplayName} from "@components/extension/environments/shared/slotCellModel"
import {gqlSlotDrawerTitle} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * The shared slot quick view.
 *
 * Every screen of the redesign that shows a slot can open this: the matrix, the project slot graph,
 * the build journey strip, the widgets and the delivery map's checkpoints. That is the whole point
 * of it - one answer to "what is going on in this slot?", written once, rather than the four
 * different summaries the feature grew.
 *
 * The four sections themselves are [SlotSummary], because the slot page's header block is the same
 * four sections and the redesign says *the same component*. What is left here is what makes it a
 * drawer: the panel, its title, and the way out to the slot page.
 *
 * @param {string} slotId The slot to show - the drawer is addressed by `?slot=<id>`.
 * @param {boolean} open Whether it is up.
 * @param {function} onClose Close it.
 * @param {function} onDeploy Called with a slot when the user asks to deploy into it; the caller
 *   opens the deploy dialog. The drawer does not own the dialog, because a page holding both the
 *   matrix and the drawer wants one dialog, not one per open drawer.
 */
export default function SlotDrawer({slotId, open, onClose, onDeploy}) {
    return (
        <Drawer
            open={open}
            onClose={onClose}
            size={520}
            title={<SlotDrawerTitle slotId={slotId} open={open}/>}
            data-testid="slot-drawer"
            // Every opening asks the server again, and a closed drawer costs nothing.
            destroyOnHidden
        >
            {
                /*
                 * `key` so that switching slots while the drawer is open remounts the content.
                 * `useQuery` never puts `finished` back to false on a deps change, so without this
                 * the drawer would keep drawing the previous slot's Now / In flight / Next / Recent
                 * under the new slot's title until the new answer landed.
                 */
                open && slotId &&
                <SlotSummary key={slotId} slotId={slotId} onDeploy={onDeploy}/>
            }
        </Drawer>
    )
}

/**
 * The title is its own tiny query so the drawer has a name before the rest of it lands - opening
 * onto a blank header and having it fill in a moment later reads as a bug.
 */
function SlotDrawerTitle({slotId, open}) {
    const query = useQuery(
        gqlSlotDrawerTitle,
        {
            variables: {slotId},
            deps: [slotId],
            condition: !!slotId && open,
            dataFn: data => data.slotById,
        }
    )

    const slot = query.data
    if (!slot) return 'Slot'

    return (
        <Space size={8}>
            <EnvironmentIcon environmentId={slot.environment.id} size={16}/>
            <Typography.Text strong data-testid="slot-drawer-title">{slotDisplayName(slot)}</Typography.Text>
            <Link href={slotUri(slot)} data-testid="slot-drawer-open-slot">Open slot</Link>
        </Space>
    )
}
