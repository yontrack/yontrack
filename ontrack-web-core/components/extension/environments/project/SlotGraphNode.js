import {createContext, useContext} from "react"
import {Handle, Position} from "reactflow"
import {Card, Space, Typography} from "antd"
import SlotCell from "@components/extension/environments/shared/SlotCell"
import EnvironmentImage from "@components/extension/environments/shared/EnvironmentImage"
import {slotDisplayNameWithoutProject} from "@components/extension/environments/shared/slotCellModel"

/**
 * What a node does when it is activated, read from context rather than carried in its `data`.
 *
 * React Flow's nodes are built once, by the effect which lays them out, and whatever the handler
 * closed over at that moment is what it would keep using - a router query from before the qualifier
 * was switched, say, which would then be written back over the current one on the next click.
 * Context is read at render time by the node itself and cannot go stale.
 */
export const SlotGraphNodeContext = createContext({onSlotClick: undefined})

/**
 * One node of the project's slot graph, which **is** a slot cell (#1795).
 *
 * The whole point of the phase: the graph and the matrix are two arrangements of the same unit, so a
 * reader who has learnt to read one cell has learnt to read the other. The node adds only what the
 * graph needs and the matrix's table already had - the environment's name and icon, which a matrix
 * carries in its column header and a free-floating node has nowhere else to get.
 */
export default function SlotGraphNode({data}) {

    const {onSlotClick} = useContext(SlotGraphNodeContext)
    const slot = data?.slot

    if (!slot) return null

    return (
        <>
            <Handle type="target" position={Position.Left}/>
            <Handle type="source" position={Position.Right}/>

            <Card
                size="small"
                data-testid={`slot-graph-node-${slot.id}`}
                styles={{body: {padding: '0.5em 0.75em'}}}
                style={{
                    width: 240,
                    border: 'solid 2px var(--ot-graph-node-border)',
                    backgroundColor: 'var(--ot-graph-node-bg)',
                }}
            >
                <Space orientation="vertical" size={2} className="ot-line">
                    <Space size={6}>
                        <EnvironmentImage environment={slot.environment}/>
                        <Typography.Text strong>
                            {slotDisplayNameWithoutProject(slot)}
                        </Typography.Text>
                    </Space>
                    <SlotCell slot={slot} onClick={onSlotClick}/>
                </Space>
            </Card>
        </>
    )
}
