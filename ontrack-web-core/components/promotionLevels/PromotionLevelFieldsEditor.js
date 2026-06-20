import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Button, Checkbox, Form, Input, Select, Space} from "antd";
import {gql} from "graphql-request";
import {DeleteOutlined, PlusOutlined} from "@ant-design/icons";

export const FIELD_TYPE_OPTIONS = [
    {label: 'Text', value: 'TEXT'},
    {label: 'Number', value: 'NUMBER'},
    {label: 'Boolean', value: 'BOOLEAN'},
    {label: 'Choice', value: 'CHOICE'},
    {label: 'Link', value: 'LINK'},
]

export function usePromotionLevelFieldsEditor({onSuccess}) {
    return useFormDialog({
        init: (form, promotionLevel) => {
            form.setFieldsValue({
                fields: (promotionLevel.fields || []).map(f => ({
                    name: f.name,
                    displayName: f.displayName,
                    description: f.description || '',
                    type: f.type,
                    required: f.required,
                    optionsText: (f.options || []).join(', '),
                }))
            })
        },
        prepareValues: (values, promotionLevel) => {
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
                promotionLevelId: Number(promotionLevel.id),
                fields,
            }
        },
        query: gql`
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
        `,
        userNode: 'setPromotionLevelFields',
        onSuccess,
    })
}

export default function PromotionLevelFieldsEditor({fieldsEditor}) {
    const form = fieldsEditor.form
    const watchedFields = Form.useWatch('fields', form) || []

    return (
        <FormDialog dialog={fieldsEditor} width={800}>
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
    )
}

export function FieldRow({name, watchedField, onRemove}) {
    const isChoice = watchedField?.type === 'CHOICE'
    return (
        <Space align="start" style={{display: 'flex', marginBottom: 8}}>
            <Form.Item name={[name, 'name']} rules={[{required: true, message: 'Name required'}]}>
                <Input placeholder="Name (key)"/>
            </Form.Item>
            <Form.Item name={[name, 'displayName']} rules={[{required: true, message: 'Label required'}]}>
                <Input placeholder="Display name"/>
            </Form.Item>
            <Form.Item name={[name, 'description']}>
                <Input placeholder="Description (optional)"/>
            </Form.Item>
            <Form.Item name={[name, 'type']} rules={[{required: true}]}>
                <Select options={FIELD_TYPE_OPTIONS} style={{width: 110}}/>
            </Form.Item>
            <Form.Item name={[name, 'required']} valuePropName="checked">
                <Checkbox>Required</Checkbox>
            </Form.Item>
            {isChoice && (
                <Form.Item name={[name, 'optionsText']} rules={[{required: true, message: 'Options required for CHOICE'}]}>
                    <Input placeholder="opt1, opt2, opt3" style={{width: 160}}/>
                </Form.Item>
            )}
            <Button danger icon={<DeleteOutlined/>} onClick={onRemove}/>
        </Space>
    )
}
