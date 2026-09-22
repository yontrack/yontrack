import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Alert, Form, Input, Space, Typography} from "antd";
import {gql} from "graphql-request";

export const useForceDeploymentDialog = ({onForced}) => {
    return useFormDialog({
        init: (form) => {
            form.setFieldValue("message", "")
        },
        prepareValues: (values, {deployment}) => {
            return {
                ...values,
                deploymentId: deployment.id,
            }
        },
        query: gql`
            mutation ForceDeployment(
                $deploymentId: String!,
                $message: String!,
            ) {
                finishSlotPipelineDeployment(input: {
                    pipelineId: $deploymentId,
                    forcing: true,
                    message: $message,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'finishSlotPipelineDeployment',
        onSuccess: onForced,
    })
}

export default function ForceDeploymentDialog({dialog}) {
    return (
        <>
            <FormDialog dialog={dialog}>
                <Alert
                    type="error"
                    showIcon
                    title="Forcing the deployment"
                    description={
                        <Space orientation="vertical">
                            <Typography.Paragraph>
                                You are about to force this deployment to be marked as completed, regardless of its
                                status in Ontrack or in reality. Are you sure to want to carry on?
                            </Typography.Paragraph>
                            <Typography.Paragraph>
                                You need to enter a message to justify this action.
                            </Typography.Paragraph>
                        </Space>
                    }
                />
                <Form.Item
                    key="message"
                    name="message"
                    label="Message"
                    rules={[
                        {
                            required: true,
                            message: 'A message is required to force the completion of the deployment',
                        },
                    ]}
                >
                    <Input/>
                </Form.Item>
            </FormDialog>
        </>
    )
}