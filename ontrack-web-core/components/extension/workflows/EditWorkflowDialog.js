import {Col, Input, Modal, Row, Space} from "antd";
import {useState} from "react";
import WorkflowGraph from "@components/extension/workflows/WorkflowGraph";
import {useReactFlow} from "reactflow";
import FormErrors from "@components/form/FormErrors";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {getUserErrors} from "@components/services/graphql-utils";
import WorkflowDownloadJSONSchema from "@components/extension/workflows/WorkflowDownloadJSONSchema";

export const useEditWorkflowDialog = ({onSuccess}) => {

    const [open, setOpen] = useState(false)
    const [workflow, setWorkflow] = useState({name: '', nodes: []})

    return {
        open,
        setOpen,
        workflow,
        setWorkflow,
        onSuccess,
        start: (workflow) => {
            // Deep copy of the workflow or create an empty one
            let localWorkflow
            if (workflow) {
                localWorkflow = JSON.parse(JSON.stringify(workflow))
            } else {
                localWorkflow = {
                    name: '',
                    nodes: [],
                }
            }
            setWorkflow(localWorkflow)
            setOpen(true)
        }
    }

}

export default function EditWorkflowDialog({dialog}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(false)
    const [formErrors, setFormErrors] = useState([])

    const onCancel = () => {
        dialog.setOpen(false)
    }

    // Workflow name
    const onNameChange = (e) => {
        const name = e.target.value
        dialog.setWorkflow(old => ({
            ...old,
            name,
        }))
    }

    // Gets the nodes & edges of the graph
    const reactFlow = useReactFlow()

    const convertGraphToWorkflow = (nodes, edges) => {
        // Getting the nodes
        const wNodes = []
        const wIndex = {}
        nodes.forEach(({data}) => {
            const {id, description, executorId, timeout, data: nodeData} = data
            const wNode = {
                id,
                description,
                executorId,
                timeout,
                data: nodeData,
                parents: [],
            };
            wNodes.push(wNode)
            wIndex[id] = wNode
        })
        // Getting the relationships
        edges.forEach(({source, target}) => {
            const parent = wIndex[source]
            const child = wIndex[target]
            if (parent && child) {
                child.parents.push({id: parent.id})
            }
        })
        // Workflow
        return {
            name: dialog.workflow.name,
            nodes: wNodes,
        }
    }

    const onSave = async () => {
        // Loading mode
        setLoading(true)
        setFormErrors([])
        try {
            // Gets the workflow graph from the context
            const nodes = reactFlow.getNodes()
            const edges = reactFlow.getEdges()
            // Converts the graph into a workflow definition
            const workflow = convertGraphToWorkflow(nodes, edges)
            // Checks the workflow (name, cycle, etc.)
            const data = await client.request(
                gql`
                    mutation ValidateWorkflow($workflow: JSON!) {
                        validateJsonWorkflow(input: {workflow: $workflow}) {
                            errors {
                                message
                            }
                            validation {
                                errors
                            }
                        }
                    }
                `,
                {workflow}
            )
            // Error management
            const userNode = data.validateJsonWorkflow
            let errors = getUserErrors(userNode)
            if (!errors) {
                errors = userNode.validation.errors
            }
            if (errors && errors.length > 0) {
                setFormErrors(errors)
            } else {
                dialog.setOpen(false)
                dialog.onSuccess(workflow)
            }
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <Modal
                open={dialog.open}
                closable={false}
                onCancel={onCancel}
                confirmLoading={loading}
                onOk={onSave}
                okText="Save"
                width={900}
            >
                <Space orientation="vertical" className="ot-line">
                    <FormErrors errors={formErrors}/>
                    {/* Workflow name */}
                    <Row>
                        <Col span={18}>
                            <Input
                                placeholder="Workflow name"
                                value={dialog.workflow.name}
                                onChange={onNameChange}
                            />
                        </Col>
                        <Col span={6}>
                            <WorkflowDownloadJSONSchema/>
                        </Col>
                    </Row>
                    {/* Workflow nodes in edition mode */}
                    <WorkflowGraph
                        workflowNodes={dialog.workflow.nodes}
                        edition={true}
                    />
                </Space>
            </Modal>
        </>
    )
}