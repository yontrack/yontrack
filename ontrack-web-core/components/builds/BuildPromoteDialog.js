import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {DatePicker, Form, Input} from "antd";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";
import dayjs from "dayjs";
import {gql} from "graphql-request";
import {useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {
    gqlPromotionLevelFieldSet,
    orderedPromotionLevelFields,
    promotionLevelFieldInput,
    promotionLevelFieldRules,
    promotionLevelFieldValuePropName,
    toPromotionRunFieldValues,
} from "@components/promotionLevels/promotionLevelFields";

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
            return {
                buildId: Number(context.build.id),
                promotion: values.promotionLevel,
                description: values.description,
                dateTime: values.dateTime,
                // Shared with the mobile promote sheet - see
                // `components/promotionLevels/promotionLevelFields.js`.
                fieldValues: toPromotionRunFieldValues(values.fieldValues),
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
                                    ...PromotionLevelFieldSet
                                }
                            }
                        }
                    }
                    ${gqlPromotionLevelFieldSet}
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

    const currentFields = orderedPromotionLevelFields(selectedPromotion && promotionLevelFields[selectedPromotion])

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
                        rules={promotionLevelFieldRules(field)}
                        valuePropName={promotionLevelFieldValuePropName(field)}
                    >
                        {/*
                          The type-to-input mapping is shared with the mobile
                          promote sheet, deliberately: a field type added here
                          and not there is a required field a phone cannot fill,
                          and so a promotion level a phone cannot use. See
                          `components/promotionLevels/promotionLevelFields.js`.
                        */}
                        {promotionLevelFieldInput(field)}
                    </Form.Item>
                ))}
            </FormDialog>
        </>
    )
}
