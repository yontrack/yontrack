import {Button, Form, Input, Select, Space, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";
import SelectConfiguration from "@components/configurations/SelectConfiguration";
import HttpBodyInput from "@components/extension/notifications/http/HttpBodyInput";
import {httpBodyRule} from "@components/extension/notifications/http/httpBody";

const methodOptions = ['GET', 'POST', 'PUT'].map(method => ({value: method, label: method}))

export default function HttpNotificationChannelForm({prefix}) {

    const form = Form.useFormInstance()
    const method = Form.useWatch(prefixedFormName(prefix, 'method'), form) ?? 'GET'
    const acceptsBody = method === 'POST' || method === 'PUT'

    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'config')}
                label="HTTP configuration"
                extra="HTTP configuration to call: its base URL and its authentication."
                rules={[{required: true, message: 'HTTP configuration is required.'}]}
            >
                <SelectConfiguration configurationType="http"/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'method')}
                label="Method"
                initialValue="GET"
                rules={[{required: true, message: 'Method is required.'}]}
            >
                <Select options={methodOptions} style={{width: '10em'}}/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'path')}
                label="Path"
                extra="Path, with an optional query, appended to the base URL of the configuration. Templated. It must stay under the base URL."
            >
                <Input placeholder="/deployments?project=${project}"/>
            </Form.Item>
            <Form.Item
                label="Headers"
                extra="Extra headers. Their values are templated. Authorization, and the headers set by the configuration, are refused."
            >
                <Form.List name={prefixedFormName(prefix, 'headers')}>
                    {(fields, {add, remove}) => (
                        <Space orientation="vertical" style={{width: '100%'}}>
                            {fields.map(({key, name, ...restField}) => (
                                <Space key={key} align="baseline">
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'name']}
                                        rules={[{required: true, message: 'Header name is required.'}]}
                                    >
                                        <Input placeholder="Name" aria-label="Header name"/>
                                    </Form.Item>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'value']}
                                        rules={[{required: true, message: 'Header value is required.'}]}
                                    >
                                        <Input placeholder="Value" aria-label="Header value"/>
                                    </Form.Item>
                                    <Button
                                        type="text"
                                        icon={<FaTrash/>}
                                        aria-label="Remove header"
                                        onClick={() => remove(name)}
                                    />
                                </Space>
                            ))}
                            <Button type="dashed" onClick={() => add()} block>
                                <Space>
                                    <FaPlus/>
                                    <Typography.Text>Add header</Typography.Text>
                                </Space>
                            </Button>
                        </Space>
                    )}
                </Form.List>
            </Form.Item>
            {
                acceptsBody &&
                <Form.Item
                    name={prefixedFormName(prefix, 'body')}
                    label="Body"
                    extra="JSON body, sent as application/json. Its string values are templated; numbers and booleans are sent as they are. Leave it empty to send no body."
                    rules={[httpBodyRule]}
                    preserve={false}
                >
                    <HttpBodyInput/>
                </Form.Item>
            }
        </>
    )
}
