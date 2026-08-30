import {Alert, Space, Typography} from "antd";
import {FaTimesCircle} from "react-icons/fa";

/**
 * The error output of a failed node, inlined on a workflow card.
 *
 * Height-clamped by `.ot-workflow-node-error` so that a long stack trace cannot take over the page —
 * the full output stays on the workflow instance page.
 */
export default function WorkflowInstanceNodeError({node, executorId}) {
    if (!node?.error) return null
    return (
        <Alert
            type="error"
            data-testid="workflow-node-error"
            message={
                <Space size="small">
                    <FaTimesCircle color="red"/>
                    <Typography.Text strong>{node.id}</Typography.Text>
                    {
                        executorId &&
                        <Typography.Text type="secondary">{`· ${executorId}`}</Typography.Text>
                    }
                </Space>
            }
            description={
                <Typography.Text className="ot-workflow-node-error" type="danger">
                    {node.error}
                </Typography.Text>
            }
        />
    )
}
