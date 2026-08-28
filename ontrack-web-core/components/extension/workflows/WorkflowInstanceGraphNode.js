import {Handle, Position} from "reactflow";
import {Card, Space, Typography} from "antd";
import WorkflowInstanceNodeStatus from "@components/extension/workflows/WorkflowInstanceNodeStatus";
import {FaPlay, FaStop, FaStopwatch, FaTag} from "react-icons/fa";
import TimestampText from "@components/common/TimestampText";
import DurationMs from "@components/common/DurationMs";

export default function WorkflowInstanceGraphNode({data}) {

    const {nodeExecution, workflowNode, selected, showDetails} = data

    return (
        <>
            <Handle type="target" position={Position.Left}/>
            <Handle type="source" position={Position.Right}/>
            <Card
                title={undefined}
                size="small"
                style={
                    selected ? {
                        border: 'solid 2px var(--ot-graph-node-border-selected)'
                    } : {}
                }
                bodyStyle={{
                    overflow: 'hidden'
                }}
            >
                <Space direction="vertical">
                    <Space>
                        <FaTag/>
                        <Typography.Text strong>{workflowNode.id}</Typography.Text>
                    </Space>
                    <Typography.Text italic>{workflowNode.executorId}</Typography.Text>
                    <WorkflowInstanceNodeStatus status={nodeExecution.status}/>
                    {
                        showDetails && <>
                            <Space>
                                <FaPlay/>
                                <TimestampText value={nodeExecution.startTime}/>
                            </Space>
                            {
                                nodeExecution.endTime &&
                                <Space>
                                    <FaStop/>
                                    <TimestampText value={nodeExecution.endTime}/>
                                </Space>
                            }
                            {
                                nodeExecution.endTime &&
                                <Space>
                                    <FaStopwatch/>
                                    <DurationMs ms={nodeExecution.durationMs}/>
                                </Space>
                            }
                        </>
                    }
                </Space>
            </Card>
        </>
    )

}