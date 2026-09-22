import {Alert, Collapse, Empty, Space, Typography} from "antd";
import {useWorkflowNodeExecutor} from "@components/extension/workflows/WorkflowNodeExecutorContext";
import {FaCog, FaPlay, FaStop, FaStopwatch, FaTag, FaWrench} from "react-icons/fa";
import WorkflowInstanceNodeStatus from "@components/extension/workflows/WorkflowInstanceNodeStatus";
import TimestampText from "@components/common/TimestampText";
import DurationMs from "@components/common/DurationMs";
import WorkflowNodeExecutorConfig from "@components/extension/workflows/WorkflowNodeExecutorConfig";
import WorkflowNodeExecutorOutput from "@components/extension/workflows/WorkflowNodeExecutorOutput";

export default function WorkflowInstanceGraphInfoItems({selectedNode, className}) {

    const executor = useWorkflowNodeExecutor(selectedNode?.workflowNode?.executorId, [selectedNode])

    const items = [
        {
            key: 'node',
            label: <Space>
                <FaTag/>
                <Typography.Text strong>{selectedNode.workflowNode.id}</Typography.Text>
            </Space>,
            children: <Space orientation="vertical">
                <WorkflowInstanceNodeStatus status={selectedNode.nodeExecution.status}/>
                <Space>
                    <FaPlay/>
                    <TimestampText value={selectedNode.nodeExecution.startTime}/>
                    {
                        selectedNode.nodeExecution.endTime &&
                        <>
                            <FaStop/>
                            <TimestampText value={selectedNode.nodeExecution.endTime}/>
                        </>
                    }
                </Space>
                {
                    selectedNode.nodeExecution.endTime &&
                    <Space>
                        <FaStopwatch/>
                        <DurationMs ms={selectedNode.nodeExecution.durationMs}/>
                    </Space>
                }
            </Space>
        },
        {
            key: 'configuration',
            label: "Configuration",
            children: <>
                {
                    executor &&
                    <>
                        <Space>
                            <FaCog/>
                            <Typography.Text>{executor?.displayName}</Typography.Text>
                            (<Typography.Text code>{executor?.id}</Typography.Text>)
                        </Space>
                        <Space>
                            <FaWrench/>
                            <div>
                                <Typography.Text italic>Configuration</Typography.Text>
                                <div>
                                    <WorkflowNodeExecutorConfig
                                        executorId={executor.id}
                                        data={selectedNode.workflowNode.data}
                                    />
                                </div>
                            </div>
                        </Space>
                    </>
                }
            </>
        },
        {
            key: 'outcome',
            label: "Outcome",
            children: <>
                {/* Error */}
                {
                    selectedNode.nodeExecution.status === 'ERROR' &&
                    <>
                        <Alert
                            type="error"
                            title="Error"
                            description={selectedNode.nodeExecution.error}
                        />
                    </>
                }
                {/* Output */}
                {
                    selectedNode.nodeExecution.output &&
                    <WorkflowNodeExecutorOutput
                        executorId={executor.id}
                        nodeData={selectedNode.workflowNode.data}
                        data={selectedNode.nodeExecution.output}/>
                }
                {
                    !selectedNode.nodeExecution.output &&
                    <Empty description="No output"/>
                }
            </>
        }
    ]

    return (
        <>
            <Collapse
                items={items}
                className={className}
                defaultActiveKey={['node', 'outcome']}
            />
            {/*<Space orientation="vertical" className="ot-line">*/}
            {/*    /!* Execution ID and configuration *!/*/}
            {/*    <Typography.Text strong italic*/}
            {/*                     className="ot-workflow-node-info-title">Execution</Typography.Text>*/}
            {/*    /!* Execution ID and output *!/*/}
            {/*    <Typography.Text strong italic className="ot-workflow-node-info-title">Output</Typography.Text>*/}
            {/*    {*/}
            {/*        selectedNode.nodeExecution.output &&*/}
            {/*        <WorkflowNodeExecutorOutput executorId={executor.id}*/}
            {/*                                    nodeData={selectedNode.workflowNode.data}*/}
            {/*                                    data={selectedNode.nodeExecution.output}/>*/}
            {/*    }*/}
            {/*    {*/}
            {/*        !selectedNode.nodeExecution.output &&*/}
            {/*        <Empty description="No output"/>*/}
            {/*    }*/}
            {/*</Space>*/}
        </>
    )
}