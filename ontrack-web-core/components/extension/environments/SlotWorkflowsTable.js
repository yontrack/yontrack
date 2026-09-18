import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import SlotWorkflowDialog, {useSlotWorkflowDialog} from "@components/extension/environments/SlotWorkflowDialog";
import {Button, Space, Table} from "antd";
import {isAuthorized} from "@components/common/authorizations";
import {FaPlus} from "react-icons/fa";
import SlotWorkflowTrigger from "@components/extension/environments/SlotWorkflowTrigger";
import SlotWorkflowEditButton from "@components/extension/environments/SlotWorkflowEditButton";
import SlotWorkflowDeleteButton from "@components/extension/environments/SlotWorkflowDeleteButton";
import ShowWorkflowButton from "@components/extension/workflows/ShowWorkflowButton";

/**
 * The workflows of a slot - the second half of the slot page's **Setup** tab.
 *
 * @param {Object} slot The slot, with its authorizations.
 * @param {number} reloadCount Bumped by the page after any change, so the list is asked again. The
 *   slot object itself does not change when a workflow is added, so it cannot carry that signal.
 * @param {function} onChange Called after a change.
 */
export default function SlotWorkflowsTable({slot, reloadCount = 0, onChange}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [workflows, setWorkflows] = useState([])

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query SlotWorkflows($id: String!) {
                        slotById(id: $id) {
                            workflows {
                                id
                                trigger
                                workflow {
                                    name
                                    nodes {
                                        id
                                        executorId
                                        timeout
                                        data
                                        parents {
                                            id
                                        }
                                    }
                                }
                            }
                        }
                    }
                `,
                {id: slot.id}
            ).then(data => {
                setWorkflows(data.slotById.workflows)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, slot, reloadCount])

    const dialog = useSlotWorkflowDialog({
        onSuccess: onChange,
    })

    const addWorkflow = async () => {
        dialog.start({slot})
    }

    return (
        <>
            <SlotWorkflowDialog dialog={dialog}/>
            <Table
                dataSource={workflows}
                loading={loading}
                rowKey={slotWorkflow => slotWorkflow.id}
                pagination={false}
                size="small"
                data-testid={`slot-workflows-${slot.id}`}
                onRow={slotWorkflow => ({'data-testid': `slot-workflow-${slotWorkflow.id}`})}
                footer={() =>
                    <Space>
                        {
                            isAuthorized(slot, "slot", "edit") &&
                            <Button
                                icon={<FaPlus/>}
                                data-testid="slot-add-workflow"
                                onClick={addWorkflow}
                            >
                                Add workflow
                            </Button>
                        }
                    </Space>
                }
            >
                <Table.Column
                    key="trigger"
                    title="Trigger"
                    render={(_, slotWorkflow) => <SlotWorkflowTrigger trigger={slotWorkflow.trigger}/>}
                />
                <Table.Column
                    key="name"
                    title="Name"
                    render={(_, slotWorkflow) => <ShowWorkflowButton workflow={slotWorkflow.workflow}/>}
                />
                <Table.Column
                    key="actions"
                    title="Actions"
                    render={(_, slotWorkflow) => <Space>
                        {
                            isAuthorized(slot, "slot", "edit") &&
                            <SlotWorkflowEditButton slot={slot} slotWorkflow={slotWorkflow} onChange={onChange}/>
                        }
                        {
                            isAuthorized(slot, "slot", "edit") &&
                            <SlotWorkflowDeleteButton
                                slot={slot}
                                slotWorkflow={slotWorkflow}
                                onChange={onChange}
                            />
                        }
                    </Space>}
                />
            </Table>
        </>
    )
}