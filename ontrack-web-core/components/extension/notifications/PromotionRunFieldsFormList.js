import {Button, Form, Input, Space, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";

/**
 * Fields to set on the promotion runs created by a notification.
 */
export default function PromotionRunFieldsFormList({prefix}) {
    return (
        <Form.List
            name={prefixedFormName(prefix, 'fields')}
            label="Fields"
            extra="Fields to set on the promotion run."
        >
            {(fields, {add, remove}) => (
                <>
                    <div
                        style={{
                            display: 'flex',
                            rowGap: 16,
                            flexDirection: 'column',
                        }}
                    >
                        {fields.map(({key, name, ...restField}) => (
                            <>
                                <Space key={key}>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'name']}
                                        rules={[
                                            {
                                                required: true,
                                                message: 'Field name is required.',
                                            },
                                        ]}
                                    >
                                        <Input placeholder="Name"/>
                                    </Form.Item>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'value']}
                                        rules={[
                                            {
                                                required: true,
                                                message: 'Field value is required.',
                                            },
                                        ]}
                                    >
                                        <Input placeholder="[template] Value"/>
                                    </Form.Item>
                                    <FaTrash
                                        onClick={() => {
                                            remove(name)
                                        }}
                                    />
                                </Space>
                            </>
                        ))}
                        <Button type="dashed" onClick={() => add()} block>
                            <Space>
                                <FaPlus/>
                                <Typography.Text>Add field</Typography.Text>
                            </Space>
                        </Button>
                    </div>
                </>
            )}
        </Form.List>
    )
}
