import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {EventsContext} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import {useContext, useState} from "react";
import {Form, Input} from "antd";
import SelectValidationDataType from "@components/validationStamps/SelectValidationDataType";
import ValidationDataTypeForm from "@components/framework/validation-data-type-form/ValidationDataTypeForm";
import Well from "@components/common/Well";

export const useValidationStampCreateDialog = () => {

    const eventsContext = useContext(EventsContext)
    const [dataTypeId, setDataTypeId] = useState()

    return useFormDialog({
        dataTypeId, setDataTypeId,
        init: () => {
            setDataTypeId(undefined)
        },
        prepareValues: (values, {branch}) => {
            return {
                ...values,
                description: values.description ?? '',
                branchId: Number(branch.id),
                dataTypeConfig: dataTypeId ? values.dataTypeConfig : undefined,
            }
        },
        query: gql`
            mutation CreateValidationStamp(
                $branchId: Int!,
                $name: String!,
                $description: String!,
                $dataType: String,
                $dataTypeConfig: JSON,
            ) {
                createValidationStampById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: $description,
                    dataType: $dataType,
                    dataTypeConfig: $dataTypeConfig,
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
                <Form.Item
                    name="dataType"
                    label="Data type"
                >
                    <SelectValidationDataType
                        allowClear
                        onValidationDataTypeSelected={id => dialog.setDataTypeId(id)}
                    />
                </Form.Item>
                {dialog.dataTypeId && (
                    <Form.Item label="Data type configuration">
                        <Well>
                            <ValidationDataTypeForm
                                prefix="dataTypeConfig"
                                dataType={{descriptor: {id: dialog.dataTypeId}, config: {}}}
                            />
                        </Well>
                    </Form.Item>
                )}
            </FormDialog>
        </>
    )
}
