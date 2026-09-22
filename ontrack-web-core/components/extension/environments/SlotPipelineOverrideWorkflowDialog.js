import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Alert, Form, Input} from "antd";
import {gql} from "graphql-request";

export const useSlotPipelineOverrideWorkflowDialog = ({onSuccess}) => {
    return useFormDialog({
        prepareValues: (values, {deployment, slotWorkflow}) => {
            return {
                pipelineId: deployment.id,
                slotWorkflowId: slotWorkflow.id,
                message: values.message,
            }
        },
        query: gql`
            mutation OverridePipelineWorkflow(
                $pipelineId: String!,
                $slotWorkflowId: String!,
                $message: String!,
            ) {
                overridePipelineWorkflow(input: {
                    pipelineId: $pipelineId,
                    slotWorkflowId: $slotWorkflowId,
                    message: $message,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'overridePipelineWorkflow',
        onSuccess,
    })
}

export default function SlotPipelineOverrideWorkflowDialog({dialog}) {
    return (
        <>
            <FormDialog
                dialog={dialog}
            >
                <Form.Item>
                    <Alert
                        type="warning"
                        title="By overriding the result of this workflow, you may bypass some controls. This action will be logged."
                        showIcon={true}
                    />
                </Form.Item>
                <Form.Item
                    name="message"
                    label="Message"
                    extra="Justification message for the override"
                    rules={[
                        {
                            required: true,
                            message: 'Message is required.',
                        },
                    ]}
                >
                    <Input/>
                </Form.Item>
            </FormDialog>
        </>
    )
}