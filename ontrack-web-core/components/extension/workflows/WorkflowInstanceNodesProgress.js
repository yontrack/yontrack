import {Space, Tooltip, Typography} from "antd";
import {FaCheckCircle, FaClock, FaHourglass, FaRegCircle, FaSpinner, FaStop, FaTimesCircle} from "react-icons/fa";
import workflowNodeDepths from "@components/extension/workflows/workflowNodeDepths";

/**
 * Maximum number of chips displayed in one column before the overflow chip kicks in.
 */
export const MAX_NODES_PER_COLUMN = 4

/**
 * Node statuses which count as a failure. A failed node is never hidden by the clamp.
 */
const FAILED_STATUSES = ['ERROR', 'CANCELLED', 'TIMEOUT']

const statusIcons = {
    CREATED: <FaRegCircle color="gray"/>,
    WAITING: <FaHourglass color="blue"/>,
    STARTED: <FaSpinner color="green" className="anticon-spin"/>,
    CANCELLED: <FaStop color="red"/>,
    TIMEOUT: <FaClock color="red"/>,
    ERROR: <FaTimesCircle color="red"/>,
    SUCCESS: <FaCheckCircle color="green"/>,
}

const statusOf = (node) => node.execution?.status ?? 'CREATED'

/**
 * Reduces a column to the chips actually displayed, keeping at most [MAX_NODES_PER_COLUMN] of them.
 *
 * Failed nodes are always kept — the point of the strip is to make a failure visible, so the clamp
 * must never be what hides it. The kept nodes are returned in their declaration order.
 */
export function clampColumn(column, max = MAX_NODES_PER_COLUMN) {
    if (column.length <= max) return {shown: column, overflow: 0}
    const failed = column.filter(node => FAILED_STATUSES.includes(statusOf(node)))
    const kept = new Set(failed.slice(0, max).map(node => node.id))
    for (const node of column) {
        if (kept.size >= max) break
        kept.add(node.id)
    }
    return {
        shown: column.filter(node => kept.has(node.id)),
        overflow: column.length - kept.size,
    }
}

function WorkflowNodeChip({node}) {
    const status = statusOf(node)
    return (
        <Tooltip title={node.execution?.error ?? node.description ?? node.id}>
            <Space
                size="small"
                data-testid={`workflow-node-chip-${node.id}`}
                data-status={status}
                className="ot-workflow-node-chip"
            >
                {statusIcons[status]}
                <Typography.Text
                    ellipsis
                    type={FAILED_STATUSES.includes(status) ? "danger" : undefined}
                >
                    {node.id}
                </Typography.Text>
            </Space>
        </Tooltip>
    )
}

/**
 * A one-line summary of the nodes of a workflow instance.
 *
 * The columns are derived from the graph (see `workflowNodeDepths`), so nodes which can run
 * concurrently share a column. There is deliberately no column label and no joining arrow: a
 * column boundary is not an edge, and reading exact edges is what the instance page is for.
 */
export default function WorkflowInstanceNodesProgress({nodes, nodesExecutions, max = MAX_NODES_PER_COLUMN}) {

    const columns = workflowNodeDepths(nodes, nodesExecutions)

    if (columns.length === 0) return null

    return (
        <div className="ot-workflow-nodes-progress" data-testid="workflow-nodes-progress">
            {
                columns.map((column, index) => {
                    const {shown, overflow} = clampColumn(column, max)
                    return (
                        <div
                            key={index}
                            className="ot-workflow-nodes-progress-column"
                            data-testid={`workflow-node-column-${index}`}
                        >
                            {
                                shown.map(node => <WorkflowNodeChip key={node.id} node={node}/>)
                            }
                            {
                                overflow > 0 &&
                                <Typography.Text
                                    type="secondary"
                                    data-testid={`workflow-node-overflow-${index}`}
                                >
                                    {`+${overflow} more`}
                                </Typography.Text>
                            }
                        </div>
                    )
                })
            }
        </div>
    )
}
