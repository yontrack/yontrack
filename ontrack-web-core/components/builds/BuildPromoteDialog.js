import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Checkbox, DatePicker, Form, Input, InputNumber, Select} from "antd";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";
import dayjs from "dayjs";
import {gql} from "graphql-request";
import {useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";

const {TextArea} = Input;

export function useBuildPromoteDialog(config) {
    return useFormDialog({
        ...config,
        init: (form, context) => {
            form.setFieldsValue({
                promotionLevel: context.promotionLevel?.name,
                dateTime: dayjs(),
            })
        },
        prepareValues: (values, context) => {
            const fieldValues = values.fieldValues
                ? Object.entries(values.fieldValues)
                    .filter(([, v]) => v !== undefined && v !== null && v !== '')
                    .map(([name, value]) => ({name, value}))
                : []
            return {
                buildId: Number(context.build.id),
                promotion: values.promotionLevel,
                description: values.description,
                dateTime: values.dateTime,
                fieldValues: fieldValues.length > 0 ? fieldValues : undefined,
            }
        },
        query: gql`
            mutation PromoteBuild(
                $buildId: Int!,
                $promotion: String!,
                $description: String,
                $dateTime: LocalDateTime,
                $fieldValues: [PromotionRunFieldValueInput!]
            ) {
                createPromotionRunById(input: {
                    buildId: $buildId,
                    promotion: $promotion,
                    description: $description,
                    dateTime: $dateTime,
                    fieldValues: $fieldValues,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'createPromotionRunById',
    })
}

export default function BuildPromoteDialog({buildPromoteDialog}) {
    const client = useGraphQLClient()
    const [promotionLevelFields, setPromotionLevelFields] = useState({})

    const form = buildPromoteDialog.form
    const selectedPromotion = Form.useWatch('promotionLevel', form)
    const branch = buildPromoteDialog?.context?.build?.branch

    useEffect(() => {
        if (client && branch) {
            client.request(
                gql`
                    query GetBranchPromotionLevelFields($branchId: Int!) {
                        branches(id: $branchId) {
                            promotionLevels {
                                name
                                fields {
                                    name
                                    displayName
                                    description
                                    type
                                    required
                                    options
                                    position
                                }
                            }
                        }
                    }
                `,
                {branchId: Number(branch.id)}
            ).then(data => {
                const map = {}
                for (const pl of data.branches[0].promotionLevels) {
                    map[pl.name] = pl.fields
                }
                setPromotionLevelFields(map)
            })
        }
    }, [client, branch])

    const currentFields = (selectedPromotion && promotionLevelFields[selectedPromotion]) || []

    return (
        <>
            <FormDialog id="promotion-run-create-dialog" dialog={buildPromoteDialog}>
                <Form.Item
                    name="promotionLevel"
                    label="Promotion level to promote to"
                    rules={[{required: true, message: 'Promotion level is required.'}]}
                >
                    <SelectPromotionLevel
                        branch={branch}
                        useName={true}
                    />
                </Form.Item>
                <Form.Item
                    name="dateTime"
                    label="Date/time"
                    rules={[{required: true, message: 'Promotion time is required.'}]}
                >
                    <DatePicker showTime/>
                </Form.Item>
                <Form.Item
                    name="description"
                    label="Description"
                >
                    <TextArea rows={4}/>
                </Form.Item>
                {currentFields.map(field => (
                    <Form.Item
                        key={field.name}
                        name={['fieldValues', field.name]}
                        label={field.displayName}
                        tooltip={field.description}
                        rules={field.required ? [{required: true, message: `${field.displayName} is required.`}] : []}
                        valuePropName={field.type === 'BOOLEAN' ? 'checked' : 'value'}
                    >
                        {renderFieldInput(field)}
                    </Form.Item>
                ))}
            </FormDialog>
        </>
    )
}

function renderFieldInput(field) {
    switch (field.type) {
        case 'TEXT':
            return <Input/>
        case 'NUMBER':
            return <InputNumber style={{width: '100%'}}/>
        case 'BOOLEAN':
            return <Checkbox/>
        case 'CHOICE':
            return <Select options={field.options.map(o => ({label: o, value: o}))}
                           getPopupContainer={trigger => trigger.parentElement}/>
        case 'LINK':
            return <Input placeholder="https://..."/>
        default:
            return <Input/>
    }
}
