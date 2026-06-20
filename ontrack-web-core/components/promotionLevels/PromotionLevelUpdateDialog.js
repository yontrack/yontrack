import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {getPromotionLevelById, gqlPromotionLevelFragment} from "@components/services/fragments";
import {gql} from "graphql-request";
import {EventsContext} from "@components/common/EventsContext";
import {useContext, useRef} from "react";
import PromotionLevelFormItemDescription from "@components/promotionLevels/PromotionLevelFormItemDescription";
import PromotionLevelFormItemName from "@components/promotionLevels/PromotionLevelFormItemName";
import {FieldRow} from "@components/promotionLevels/PromotionLevelFieldsEditor";
import {Button, Form} from "antd";
import {PlusOutlined} from "@ant-design/icons";

const SET_FIELDS_MUTATION = gql`
    mutation SetPromotionLevelFields(
        $promotionLevelId: Int!,
        $fields: [PromotionLevelFieldInput!]!
    ) {
        setPromotionLevelFields(input: {
            promotionLevelId: $promotionLevelId,
            fields: $fields,
        }) {
            errors {
                message
            }
        }
    }
`

export const usePromotionLevelUpdateDialog = () => {

    const client = useGraphQLClient()
    const eventsContext = useContext(EventsContext)
    const pendingFieldsRef = useRef(null)

    return useFormDialog({
        init: (form, {id}) => {
            getPromotionLevelById(client, id).then(pl => {
                form.setFieldsValue({
                    ...pl,
                    fields: (pl.fields || []).map(f => ({
                        name: f.name,
                        displayName: f.displayName,
                        description: f.description || '',
                        type: f.type,
                        required: f.required,
                        optionsText: (f.options || []).join(', '),
                    }))
                })
            })
        },
        prepareValues: (values, {id}) => {
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
            pendingFieldsRef.current = {promotionLevelId: Number(id), fields}
            return {
                id: Number(id),
                name: values.name,
                description: values.description ?? '',
            }
        },
        query: gql`
            mutation UpdatePromotionLevel(
                $id: Int!,
                $name: String!,
                $description: String!,
            ) {
                updatePromotionLevelById(input: {
                    id: $id,
                    name: $name,
                    description: $description,
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
        userNode: 'updatePromotionLevelById',
        onSuccess: async (updatePromotionLevelById) => {
            if (pendingFieldsRef.current) {
                await client.request(SET_FIELDS_MUTATION, pendingFieldsRef.current)
                pendingFieldsRef.current = null
            }
            eventsContext.fireEvent("promotionLevel.updated", {...updatePromotionLevelById.promotionLevel})
        }
    })
}

export default function PromotionLevelUpdateDialog({promotionLevelUpdateDialog}) {
    const form = promotionLevelUpdateDialog.form
    const watchedFields = Form.useWatch('fields', form) || []

    return (
        <>
            <FormDialog dialog={promotionLevelUpdateDialog} width={800}>
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