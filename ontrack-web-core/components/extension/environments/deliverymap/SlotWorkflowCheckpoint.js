import {Space, Tag, Typography} from "antd";
import Link from "next/link";
import {FaProjectDiagram} from "react-icons/fa";
import WorkflowCheckpointRun from "@components/extension/workflows/deliverymap/workflowCheckpointRun";

/**
 * What each trigger means for the deployment, and therefore which way the map draws its edge.
 *
 * `CANDIDATE` and `RUNNING` are hard gates, unconditionally and with no admission rule involved, so
 * they are drawn as *requires* into the slot. `DONE` gates nothing, so it is drawn as *emits* out of
 * it. The tag names the trigger rather than paraphrasing it, because the trigger is the word the
 * slot's own configuration page uses.
 */
const triggers = {
    CANDIDATE: {
        label: "on candidate",
        title: "Runs before the deployment starts. The deployment cannot start until it passes.",
    },
    RUNNING: {
        label: "on running",
        title: "Runs while the deployment is under way. The deployment cannot finish until it passes.",
    },
    DONE: {
        label: "on done",
        title: "Runs once the deployment is done. Nothing waits for it.",
    },
}

/**
 * A workflow configured on a slot, on the delivery map.
 *
 * Unlike a promotion workflow, this one is CONFIGURATION: the slot lists it whether or not any
 * pipeline ever reached it, so it is drawn whether or not it has ever run. A `CANDIDATE` workflow
 * that never ran is not dormant - it is the reason nothing has ever deployed to that slot.
 *
 * @param checkpoint The checkpoint to draw
 */
export default function SlotWorkflowCheckpoint({checkpoint}) {

    const {trigger, workflowInstanceId} = checkpoint.data ?? {}
    // An unknown trigger still says its own name rather than nothing: this frontend may be older
    // than the backend it talks to
    const {label = trigger, title} = triggers[trigger] ?? {}

    return (
        <Space orientation="vertical" size={0}>
            {/* The same icon as a promotion's workflow: the two are different KINDS of checkpoint,
                for reasons of layout and ownership, but a reader sees one thing - a workflow */}
            <Space size={4}>
                <Typography.Text type="secondary"><FaProjectDiagram/></Typography.Text>
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
            {
                label &&
                <Tag variant="filled" title={title} data-testid="slot-workflow-trigger">{label}</Tag>
            }
            <WorkflowCheckpointRun data={checkpoint.data} nothingText="Not started"/>
        </Space>
    )
}
