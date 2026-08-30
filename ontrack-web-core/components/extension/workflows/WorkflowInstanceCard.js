import Link from "next/link";
import {Button, Card, Space, Typography} from "antd";
import {FaProjectDiagram} from "react-icons/fa";
import DurationMs from "@components/common/DurationMs";
import WorkflowInstanceStatus from "@components/extension/workflows/WorkflowInstanceStatus";
import WorkflowInstanceNodesProgress from "@components/extension/workflows/WorkflowInstanceNodesProgress";
import WorkflowInstanceNodeError from "@components/extension/workflows/WorkflowInstanceNodeError";

/**
 * Node statuses which count as a failure, in the order the error block prefers them.
 */
const FAILED_STATUSES = ['ERROR', 'CANCELLED', 'TIMEOUT']

/**
 * One workflow instance, summarised: name, status, duration, a link to the full instance page, the
 * node strip and — when the workflow did not succeed — the error output of the first failed node.
 *
 * This card renders a strip, not a graph: reading the exact edges is what "Open workflow" is for.
 */
export default function WorkflowInstanceCard({instance}) {

    const nodes = instance.workflow?.nodes ?? []
    const nodesExecutions = instance.nodesExecutions ?? []

    // First failed node, in the declaration order of the workflow, so that the node named is the
    // one the reader would reach first when scanning the strip.
    const failedNode = nodes
        .map(node => nodesExecutions.find(execution => execution.id === node.id))
        .find(execution => execution && FAILED_STATUSES.includes(execution.status))

    const failedExecutorId = failedNode
        ? nodes.find(node => node.id === failedNode.id)?.executorId
        : undefined

    return (
        <Card
            size="small"
            data-testid={`workflow-instance-card-${instance.id}`}
            title={
                <Space>
                    <Typography.Text strong>{instance.workflow?.name}</Typography.Text>
                    <WorkflowInstanceStatus status={instance.status}/>
                    {
                        /*
                         * `durationMs` is 0 until at least one node has finished, so a workflow
                         * which has only just been triggered would otherwise read as "0 ms", as if
                         * it had completed instantly.
                         */
                        instance.durationMs > 0 &&
                        <Typography.Text type="secondary" data-testid="workflow-instance-duration">
                            <DurationMs ms={instance.durationMs}/>
                        </Typography.Text>
                    }
                </Space>
            }
            extra={
                <Link href={`/extension/workflows/instances/${instance.id}`} passHref>
                    <Button size="small" data-testid={`workflow-instance-open-${instance.id}`}>
                        <Space>
                            <FaProjectDiagram/>
                            Open workflow
                        </Space>
                    </Button>
                </Link>
            }
        >
            <Space direction="vertical" className="ot-line">
                <WorkflowInstanceNodesProgress
                    nodes={nodes}
                    nodesExecutions={nodesExecutions}
                />
                <WorkflowInstanceNodeError node={failedNode} executorId={failedExecutorId}/>
            </Space>
        </Card>
    )
}
