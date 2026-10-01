import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Input} from "antd";
import {gql} from "graphql-request";
import SelectValidationDataType from "@components/validationStamps/SelectValidationDataType";
import Well from "@components/common/Well";
import ValidationDataTypeForm from "@components/framework/validation-data-type-form/ValidationDataTypeForm";
import {useState} from "react";

export const usePredefinedValidationStampUpdateDialog = ({onChange}) => {

    const [dataType, setDataType] = useState()

    return useFormDialog({
        onSuccess: onChange,
        dataType, setDataType,
        init: (form, {pvs}) => {
            setDataType(pvs.dataType)
            form.setFieldsValue({
                name: pvs.name,
                description: pvs.description,
                dataType: pvs.dataType?.descriptor?.id,
                config: pvs.dataType?.formConfig,
            })
        },
        prepareValues: (values, {pvs}) => {
            return {
                ...values,
                id: Number(pvs.id),
                description: values.description ?? '',
                dataType: values.dataType,
                dataTypeConfig: values.config,
            }
        },
        query: gql`
            mutation CreatePredefinedValidationStamp(
                $id: Int!,
                $name: String!,
                $description: String!,
                $dataType: String,
                $dataTypeConfig: JSON,
            ) {
                updatePredefinedValidationStamp(input: {
                    id: $id,
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
        userNode: 'updatePredefinedValidationStamp',
    })
}

export default function PredefinedValidationStampUpdateDialog({dialog}) {

    const onValidationDataTypeSelected = (dataTypeId) => {
        dialog.setDataType(previous => ({
            ...previous,
            descriptor: {
                id: dataTypeId,
            }
        }))
    }

    return (
        <>
            <FormDialog dialog={dialog}>
                <Form.Item
                    name="name"
                    label="Name"
                    rules={[
                        {required: true, message: "Name is required"},
                    ]}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="description"
                    label="Description"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="dataType"
                    label="Data type"
                >
                    <SelectValidationDataType onValidationDataTypeSelected={onValidationDataTypeSelected}/>
                </Form.Item>
                {/* Validation data config (depends on the selected data type) */}
                {
                    dialog.dataType &&
                    <Form.Item
                        label="Validation data"
                    >
                        <Well>
                            <ValidationDataTypeForm
                                prefix="config"
                                dataType={dialog.dataType}
                            />
                        </Well>
                    </Form.Item>
                }
            </FormDialog>
        </>
    )
}
