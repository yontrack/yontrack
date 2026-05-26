import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {EventsContext} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import {useContext} from "react";
import {Form, Input} from "antd";

export const useValidationStampCreateDialog = () => {

    const eventsContext = useContext(EventsContext)

    return useFormDialog({
        prepareValues: (values, {branch}) => {
            return {
                ...values,
                description: values.description ?? '',
                branchId: Number(branch.id),
            }
        },
        query: gql`
            mutation CreateValidationStamp(
                $branchId: Int!,
                $name: String!,
                $description: String!,
            ) {
                createValidationStampById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: $description,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'createValidationStampById',
        onSuccess: () => {
            eventsContext.fireEvent("validationStamp.created")
        }
    })
}

export default function ValidationStampCreateDialog({dialog}) {
    return (
        <>
            <FormDialog dialog={dialog}>
                <Form.Item
                    name="name"
                    label="Name"
                    rules={[
                        {required: true, message: 'Validation stamp name is required.',},
                        {
                            max: 40,
                            type: 'string',
                            message: 'Validation stamp name must be 40 characters long at a maximum.',
                        },
                        {
                            pattern: /[A-Za-z0-9._-]+/,
                            message: 'Validation stamp name must contain only letters, digits, dots, underscores or dashes.',
                        },
                    ]}
                >
                    <Input placeholder="Validation stamp name" allowClear/>
                </Form.Item>
                <Form.Item
                    name="description"
                    label="Description"
                >
                    <Input placeholder="Validation stamp description" allowClear/>
                </Form.Item>
            </FormDialog>
        </>
    )
}
