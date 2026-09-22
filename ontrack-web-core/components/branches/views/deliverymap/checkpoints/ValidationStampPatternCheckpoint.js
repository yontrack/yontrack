import {useState} from "react";
import {Button, Popover, Space, theme, Typography} from "antd";
import {FaChevronDown, FaChevronRight} from "react-icons/fa";
import ValidationStampCheckpoint
    from "@components/branches/views/deliverymap/checkpoints/ValidationStampCheckpoint";
import {summariseMembers} from "@components/branches/views/deliverymap/checkpoints/aggregateSummary";

/**
 * The aggregate checkpoint: one node standing for every validation stamp matched by an auto
 * promotion pattern, rather than one node per stamp.
 *
 * It is labelled with the PATTERN, because that is what the configuration actually says: everything
 * matching this, not these forty named things.
 *
 * Expanding opens a POPOVER rather than growing the node or adding graph nodes. Both of the
 * alternatives break the layout, in the same way and for the same reason: elk is given each node's
 * size before anything is rendered, so a node which grows after the fact covers whatever elk placed
 * beneath it, and new nodes re-run the layout and reshuffle the map under the user's cursor. A
 * popover is drawn outside the flow and costs the layout nothing.
 *
 * @param checkpoint The aggregate checkpoint to draw
 */
export default function ValidationStampPatternCheckpoint({checkpoint}) {

    const {token} = theme.useToken()
    const [expanded, setExpanded] = useState(false)

    const members = checkpoint.members ?? []
    const {total, passed} = summariseMembers(members)

    return (
        <Space orientation="vertical" size={token.marginXXS} style={{width: '100%'}}>
            <Typography.Text code>{checkpoint.name}</Typography.Text>
            <Popover
                open={expanded}
                onOpenChange={setExpanded}
                trigger="click"
                placement="right"
                title={<Typography.Text code>{checkpoint.name}</Typography.Text>}
                content={
                    <Space orientation="vertical" size={token.marginXXS}>
                        {
                            members.map(member =>
                                <ValidationStampCheckpoint key={member.id} checkpoint={member}/>
                            )
                        }
                    </Space>
                }
            >
                <Button
                    type="text"
                    size="small"
                    icon={expanded ? <FaChevronDown/> : <FaChevronRight/>}
                    data-testid={`checkpoint-expand-${checkpoint.id}`}
                >
                    {`${passed} of ${total} passed`}
                </Button>
            </Popover>
        </Space>
    )
}
