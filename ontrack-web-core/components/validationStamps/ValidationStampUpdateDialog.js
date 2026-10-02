import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {gqlValidationStampFragment} from "@components/services/fragments";
import {Form, Input} from "antd";
import {gql} from "graphql-request";
import {useState} from "react";
import SelectValidationDataType from "@components/validationStamps/SelectValidationDataType";
import Well from "@components/common/Well";
import ValidationDataTypeForm from "@components/framework/validation-data-type-form/ValidationDataTypeForm";

export const useValidationStampUpdateDialog = ({onSuccess}) => {

    const [dataType, setDataType] = useState()

    return useFormDialog({
        dataType, setDataType,
        init: (form, {validationStamp}) => {
            setDataType(validationStamp.dataType)
            return form.setFieldsValue({
                ...validationStamp,
                dataType: validationStamp.dataType?.descriptor?.id,
                config: validationStamp.dataType?.formConfig,
            })
        },
        prepareValues: (values, {validationStamp}) => {
            return {
                ...values,
                id: Number(validationStamp.id),
                dataTypeId: values.dataType,
                dataTypeConfig: values.config,
            }
        },
        query: gql`
            mutation UpdateValidationStamp(
                $id: Int!,
                $name: String!,
                $description: String!,
                $dataTypeId: String,
                $dataTypeConfig: JSON,
            ) {
                updateValidationStampById(input: {
                    id: $id,
                    name: $name,
                    description: $description,
                    dataType: $dataTypeId,
                    dataTypeConfig: $dataTypeConfig,
                }) {
                    errors {
                        message
                    }
                    validationStamp {
                        ...ValidationStampData
                    }
                }
            }
            ${gqlValidationStampFragment}
        `,
        userNode: 'updateValidationStampById',
        onSuccess,
    })
}

export default function ValidationStampUpdateDialog({validationStampUpdateDialog}) {

    const onValidationDataTypeSelected = (dataTypeId) => {
        validationStampUpdateDialog.setDataType(previous => ({
            ...previous,
            descriptor: {
                id: dataTypeId,
            }
        }))
    }

    return (
        <>
            <FormDialog dialog={validationStampUpdateDialog}>
                <Form.Item
                    name="name"
                    label="Name"
                    rules={[{required: true, message: 'Name is required.'}]}
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
                    validationStampUpdateDialog.dataType &&
                    <Form.Item
                        label="Validation data"
                    >
                        <Well>
                            <ValidationDataTypeForm
                                prefix="config"
                                dataType={validationStampUpdateDialog.dataType}
                            />
                        </Well>
                    </Form.Item>
                }
            </FormDialog>
        </>
    )
}