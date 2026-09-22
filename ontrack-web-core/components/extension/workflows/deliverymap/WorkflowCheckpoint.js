import {Space, Typography} from "antd";
import Link from "next/link";
import {FaProjectDiagram} from "react-icons/fa";
import WorkflowCheckpointRun from "@components/extension/workflows/deliverymap/workflowCheckpointRun";

/**
 * A workflow fired by a promotion, on the delivery map.
 *
 * It hangs off its promotion level by an *emits* edge: the workflow runs once the promotion has been
 * granted, and nothing waits for what it decides. Drawing it as a prerequisite would say the
 * promotion is gated by it - see ADR 0011.
 *
 * It names no build. Its build would always be the one its promotion level already names, so a
 * build line here would repeat that build one node to the right and lag behind it whenever the two
 * were fetched a moment apart.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function WorkflowCheckpoint({checkpoint}) {

    const {workflowInstanceId} = checkpoint.data ?? {}

    return (
        <Space orientation="vertical" size={0}>
            {/* The icon says WHAT this checkpoint is, the way a promotion level's medal and a slot's
                environment do. It is outside the link so that the name alone is the click target */}
            <Space size={4}>
                <Typography.Text type="secondary"><FaProjectDiagram/></Typography.Text>
                {/* The NAME is the link, as on a slot: the reactflow node itself stays selectable and
                    draggable, and a node which was entirely a link could not be either */}
                {
                    workflowInstanceId ?
                        <Link
                            href={`/extension/workflows/instances/${workflowInstanceId}`}
                            title="Workflow run, node by node"
                        >
                            {checkpoint.name}
                        </Link> :
                        <Typography.Text>{checkpoint.name}</Typography.Text>
                }
            </Space>
            {/* A promotion workflow is only reachable through the records of the runs which fired
                it, so one which never ran is never drawn at all - unlike a slot workflow */}
            <WorkflowCheckpointRun data={checkpoint.data} nothingText="No run recorded"/>
        </Space>
    )
}
