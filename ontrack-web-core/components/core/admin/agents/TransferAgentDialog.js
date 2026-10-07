import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Typography} from "antd";
import {gql} from "graphql-request";
import SelectHumanAccount from "@components/core/admin/agents/SelectHumanAccount";

export const useTransferAgentDialog = ({onSuccess}) => {
    return useFormDialog({
        init: (form, {agent}) => {
            form.setFieldsValue({
                owner: agent.owner?.email,
            })
        },
        prepareValues: (values, {agent}) => ({
            ...values,
            id: Number(agent.id),
        }),
        query: gql`
            mutation TransferAgent($id: Int!, $owner: String!) {
                transferAgent(input: {id: $id, owner: $owner}) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'transferAgent',
        onSuccess,
    })
}

export default function TransferAgentDialog({dialog}) {
    return (
        <FormDialog
            dialog={dialog}
            id="transfer-agent"
            okText="Transfer"
            header={
                dialog.context?.agent &&
                <Typography.Paragraph>
                    Transferring <Typography.Text strong>{dialog.context.agent.fullName}</Typography.Text> to
                    another person, who becomes accountable for it.
                </Typography.Paragraph>
            }
        >
            <Form.Item
                name="owner"
                label="New owner"
                rules={[{required: true, message: "The new owner is required."}]}
            >
                <SelectHumanAccount/>
            </Form.Item>
        </FormDialog>
    )
}
