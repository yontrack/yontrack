import {Modal, Space, Typography} from "antd";
import {useState} from "react";
import WorkflowGraph from "@components/extension/workflows/WorkflowGraph";

export function useShowWorkflowDialog() {

    const [open, setOpen] = useState(false)
    const [workflow, setWorkflow] = useState({name: '', nodes: []})

    return {
        open,
        setOpen,
        workflow,
        start: (workflow) => {
            setWorkflow(workflow)
            setOpen(true)
        }
    }
}

export default function ShowWorkflowDialog({dialog}) {

    const onCancel = () => {
        dialog.setOpen(false)
    }

    return (
        <>
            <Modal
                open={dialog.open}
                closable={false}
                onCancel={onCancel}
                footer={null}
                width={900}
            >
                <Space orientation="vertical" className="ot-line">
                    <Typography.Title level={3}>{dialog.workflow.name}</Typography.Title>
                    <WorkflowGraph
                        workflowNodes={dialog.workflow.nodes}
                    />
                </Space>
            </Modal>
        </>
    )
}