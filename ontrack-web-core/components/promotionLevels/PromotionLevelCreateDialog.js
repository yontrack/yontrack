import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import PromotionLevelFormItemDescription from "@components/promotionLevels/PromotionLevelFormItemDescription";
import PromotionLevelFormItemName from "@components/promotionLevels/PromotionLevelFormItemName";
import {FieldRow} from "@components/promotionLevels/PromotionLevelFieldsEditor";
import {EventsContext} from "@components/common/EventsContext";
import {gqlPromotionLevelFragment} from "@components/services/fragments";
import {gql} from "graphql-request";
import {useContext} from "react";
import {Button, Form} from "antd";
import {PlusOutlined} from "@ant-design/icons";

export const usePromotionLevelCreateDialog = () => {

    const eventsContext = useContext(EventsContext)

    return useFormDialog({
        prepareValues: (values, {branch}) => {
            const fields = (values.fields || []).map(f => ({
                name: f.name,
                displayName: f.displayName,
                description: f.description || null,
                type: f.type,
                required: !!f.required,
                options: f.type === 'CHOICE'
                    ? (f.optionsText || '').split(',').map(o => o.trim()).filter(Boolean)
                    : null,
            }))
            return {
                name: values.name,
                description: values.description ?? '',
                branchId: Number(branch.id),
                fields: fields.length > 0 ? fields : null,
            }
        },
        query: gql`
            mutation CreatePromotionLevel(
                $branchId: Int!,
                $name: String!,
                $description: String!,
                $fields: [PromotionLevelFieldInput!],
            ) {
                createPromotionLevelById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: $description,
                    fields: $fields,
                }) {
                    errors {
                        message
                    }
                    promotionLevel {
                        ...PromotionLevelData
                    }
                }
            }
            ${gqlPromotionLevelFragment}
        `,
        userNode: 'createPromotionLevelById',
        onSuccess: (createPromotionLevelById) => {
            eventsContext.fireEvent("promotionLevel.created", {...createPromotionLevelById.promotionLevel})
        }
    })
}

export default function PromotionLevelCreateDialog({dialog}) {
    const form = dialog.form
    const watchedFields = Form.useWatch('fields', form) || []

    return (
        <>
            <FormDialog dialog={dialog} width={800}>
                <PromotionLevelFormItemName/>
                <PromotionLevelFormItemDescription/>
                <Form.List name="fields">
                    {(fields, {add, remove}) => (
                        <>
                            {fields.map(({key, name}) => (
                                <FieldRow
                                    key={key}
                                    name={name}
                                    watchedField={watchedFields[name]}
                                    onRemove={() => remove(name)}
                                />
                            ))}
                            <Form.Item>
                                <Button
                                    type="dashed"
                                    onClick={() => add({type: 'TEXT', required: false})}
                                    icon={<PlusOutlined/>}
                                >
                                    Add field
                                </Button>
                            </Form.Item>
                        </>
                    )}
                </Form.List>
            </FormDialog>
        </>
    )
}