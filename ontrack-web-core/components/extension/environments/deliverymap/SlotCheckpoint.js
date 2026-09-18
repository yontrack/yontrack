import {useContext} from "react";
import {Space, Tag, Typography} from "antd";
import {FaBan} from "react-icons/fa";
import CheckpointArrival from "@components/branches/views/deliverymap/checkpoints/CheckpointArrival";
import JourneyChip from "@components/extension/environments/shared/JourneyChip";
import {DeliveryMapSlotContext} from "@components/extension/environments/deliverymap/deliveryMapSlotContext";

/**
 * A slot on the delivery map.
 *
 * The kind the core has never heard of: the environments extension contributes it, and this is the
 * component the checkpoint registry dispatches to for it.
 *
 * It is the one kind whose build may belong to **another branch** - a slot names what is deployed in
 * it, and what is deployed in production is a fact about the project rather than about the branch
 * being read. That exception is deliberate and is recorded in ADR 0009; it is drawn distinctly, in
 * words, because a build name alone gives no hint that it comes from somewhere else.
 *
 * A slot this branch can never reach is drawn too, and says so instead of naming a build. Leaving it
 * out would be the map answering "how do I get to production?" with silence.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function SlotCheckpoint({checkpoint}) {

    const {slotId, unreachable, otherBranch} = checkpoint.data ?? {}

    const {onSlotClick} = useContext(DeliveryMapSlotContext)

    /*
     * The journey chip's entry, built from what the checkpoint already carries rather than fetched.
     * The chip needs a slot only to name it in its tooltip, and the checkpoint's own name IS that
     * name - the server builds it from the environment and the qualifier - so a row of slots on the
     * map costs no extra request.
     */
    const entry = {
        state: 'DEPLOYED',
        nonEligibleRules: [],
        slot: {id: slotId, environment: {name: checkpoint.name}},
    }

    return (
        <Space direction="vertical" size={0}>
            {/* The drawer rather than the slot page, as every other surface of the redesign does it:
                the map is expensive to lay out and a reader clicking a slot came to look at it, not
                to leave. The drawer carries the way out to the slot page for when they did mean to
                leave. */}
            {/* A `Typography.Link` rather than an anchor: there is no href behind it, and an `<a>`
                with none is a link a browser cannot open in a new tab and a reader cannot preview */}
            <Typography.Link
                data-testid={`slot-checkpoint-name-${slotId}`}
                title="What is going on in this slot"
                disabled={!onSlotClick}
                onClick={onSlotClick ? () => onSlotClick(slotId) : undefined}
            >
                {checkpoint.name}
            </Typography.Link>
            {
                unreachable ?
                    // Short enough to fit the width `checkpointTypes` reserves for the node.
                    // Nothing here may overflow it: elk lays the map out against that width, and
                    // a node drawn wider than it was laid out covers whatever sits beside it.
                    //
                    // Deliberately NOT a `Not eligible` journey chip: the chip says a build cannot
                    // be deployed here, and what this says is that no build of THIS BRANCH ever can,
                    // which is the question the map is answering and a stronger statement.
                    <Typography.Text
                        type="secondary"
                        italic
                        title="An admission rule of this slot excludes this branch, so no build of it can ever be deployed here, however far it is promoted."
                    >
                        <Space size={4}>
                            <FaBan/>
                            Unreachable from this branch
                        </Space>
                    </Typography.Text> :
                    <>
                        {/* The chip sits on the build's own line rather than on one of its own: the
                            node's height is reserved per kind, before anything is rendered, and a
                            fourth line would be drawn outside what elk laid out. */}
                        <Space size={4} wrap={false}>
                            <CheckpointArrival arrival={checkpoint.arrival} nothingText="Never deployed"/>
                            {
                                // Only when something is actually there. "Deployed" beside
                                // "Never deployed" would be a contradiction, and the state of a
                                // build the map is not naming is not a thing the chip can state.
                                checkpoint.arrival &&
                                <JourneyChip entry={entry} showSlot={false}/>
                            }
                        </Space>
                        {
                            otherBranch &&
                            <Tag color="orange" bordered={false}>{`from ${otherBranch}`}</Tag>
                        }
                    </>
            }
        </Space>
    )
}
