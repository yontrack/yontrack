import {Button, Divider, Form, Input, InputNumber, Select, Space, Typography} from "antd";
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
            <Divider titlePlacement="start" plain>Waiting for a condition</Divider>
            <Form.Item
                name={prefixedFormName(prefix, 'successCondition')}
                label="Success condition"
                extra={<>
                    CEL condition on a response telling that the call succeeded, for
                    example <Typography.Text code>{"response.body.status == 'SUCCEEDED'"}</Typography.Text>.
                    It sees <Typography.Text code>response.status</Typography.Text>,{' '}
                    <Typography.Text code>response.headers</Typography.Text> (lowercase names),{' '}
                    <Typography.Text code>response.body</Typography.Text> (parsed JSON)
                    and <Typography.Text code>attempt</Typography.Text>.
                    Leave it empty for any 2xx response to be a success. With it, the call is polled until a
                    condition holds.
                </>}
            >
                <Input placeholder="response.body.status == 'SUCCEEDED'"/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'failureCondition')}
                label="Failure condition"
                extra={<>
                    CEL condition on a response telling that the call failed, for
                    example <Typography.Text code>{"response.body.status == 'FAILED'"}</Typography.Text>.
                    Requires a success condition.
                </>}
            >
                <Input placeholder="response.body.status == 'FAILED'"/>
            </Form.Item>
            {
                acceptsBody &&
                <Form.Item
                    name={prefixedFormName(prefix, 'statusUrl')}
                    label="Status URL"
                    extra={<>
                        CEL expression giving, from the response of the {method}, the URL to poll with GET, for
                        example <Typography.Text code>{"response.headers['location']"}</Typography.Text> or{' '}
                        <Typography.Text code>{"'/deployments/' + response.body.id"}</Typography.Text>.
                        It must stay under the base URL of the configuration.
                        Without it, the conditions are evaluated on the response of the {method} only.
                    </>}
                    preserve={false}
                >
                    <Input placeholder="response.headers['location']"/>
                </Form.Item>
            }
            <Space size="large" wrap>
                <Form.Item
                    name={prefixedFormName(prefix, 'intervalSeconds')}
                    label="Interval (s)"
                    extra="Between two polls, at least 5 seconds."
                    initialValue={10}
                    rules={[{required: true, type: 'integer', min: 5, message: 'The interval must be at least 5 seconds.'}]}
                >
                    <InputNumber min={5} precision={0}/>
                </Form.Item>
                <Form.Item
                    name={prefixedFormName(prefix, 'timeoutSeconds')}
                    label="Timeout (s)"
                    extra="Maximum wait, capped by the HTTP notifications settings."
                    initialValue={300}
                    rules={[{required: true, type: 'integer', min: 5, message: 'The timeout must be at least 5 seconds.'}]}
                >
                    <InputNumber min={5} precision={0}/>
                </Form.Item>
            </Space>
        </>
    )
}
