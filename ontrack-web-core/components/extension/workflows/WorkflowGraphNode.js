import {Handle, Position} from "reactflow";
import {Card, Popconfirm, Space, Typography} from "antd";
import {useWorkflowNodeExecutor} from "@components/extension/workflows/WorkflowNodeExecutorContext";
import {FaCog, FaPencilAlt, FaTrashAlt, FaWrench} from "react-icons/fa";
import WorkflowNodeExecutorShortConfigWithHelp
    from "@components/extension/workflows/WorkflowNodeExecutorShortConfigWithHelp";
import ConfigureWorkflowNodeDialog, {
    useConfigureWorkflowNodeDialog,
} from "@components/extension/workflows/ConfigureWorkflowNodeDialog";
import WorkflowNodeTitle from "@components/extension/workflows/WorkflowNodeTitle";

export default function WorkflowGraphNode({data}) {

    const {edition, onGraphNodeChange} = data
    const executor = useWorkflowNodeExecutor(data.executorId, [data.executorId])

    const onSuccess = (values /*, context */) => {
        const {id, description, executorId, timeout, data: nodeData} = values
        console.log("onSuccess", values);
        const oldId = data.id
        if (onGraphNodeChange) {
            onGraphNodeChange({
                node: {
                    oldId,
                    id,
                    description,
                    executorId,
                    timeout,
                    data: nodeData,
                },
            })
        }
    }

    const nodeDialog = useConfigureWorkflowNodeDialog({
        onSuccess,
    })

    const configureNode = () => {
        nodeDialog.start({...data})
    }

    const deleteNode = () => {
        const oldId = data.id
        if (onGraphNodeChange) {
            onGraphNodeChange({
                node: {
                    oldId,
                    id: null,
                }
            })
        }
    }

    return (
        <>
            <Handle type="target" position={Position.Left}/>
            <Handle type="source" position={Position.Right}/>

            <Card
                title={
                    <>
                        <WorkflowNodeTitle node={data}/>
                    </>
                }
                size="small"
                styles={{
                    body: {
                        overflow: 'hidden'
                    },
                }}
                extra={
                    <>
                        {
                            edition &&
                            <Space>
                                <FaPencilAlt
                                    className="ot-action"
                                    title="Configure this node"
                                    onClick={configureNode}
                                />
                                <ConfigureWorkflowNodeDialog dialog={nodeDialog}/>
                                <Popconfirm
                                    title="Delete node"
                                    description="Are you sure to delete this node and all the links to and from this node?"
                                    onConfirm={deleteNode}
                                >
                                    <FaTrashAlt
                                        className="ot-action"
                                        color="red"
                                        title="Delete this node"
                                    />
                                </Popconfirm>
                            </Space>
                        }
                    </>
                }
            >
                {
                    executor &&
                    <Space orientation="vertical">
                        <Space>
                            <FaCog/>
                            {
                                !data.executorId &&
                                <Typography.Text italic type="secondary">No executor defined</Typography.Text>
                            }
                            {
                                data.executorId && executor &&
                                <Typography.Text>{executor?.displayName}</Typography.Text>
                            }
                        </Space>
                        <Space>
                            <FaWrench/>
                            {
                                !data.executorId &&
                                <Typography.Text italic type="secondary">No executor defined</Typography.Text>
                            }
                            {
                                data.executorId && executor &&
                                <WorkflowNodeExecutorShortConfigWithHelp
                                    executorId={executor.id}
                                    data={data.data}
                                />
                            }
                        </Space>
                    </Space>
                }
            </Card>
        </>
    )
}