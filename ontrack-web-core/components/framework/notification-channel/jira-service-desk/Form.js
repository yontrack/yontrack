import {Button, Form, Input, InputNumber, Select, Space, Switch, Typography} from "antd";
import {FaExclamationTriangle, FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";
import SelectJiraServiceDeskRequestStatus from "@components/extension/jira/SelectJiraServiceDeskRequestStatus";

export default function JiraServiceDeskNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'configName')}
                label="Config"
                extra="Name of the Jira configuration to use for the connection."
                rules={[{required: true, message: 'Config name is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'serviceDeskId')}
                label="Service desk"
                extra="ID of the Service Desk"
                rules={[{required: true, message: 'Service desk is required.'}]}
            >
                <InputNumber/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'requestTypeId')}
                label="Request type"
                extra="ID of the Request Type"
                rules={[{required: true, message: 'Request type is required.'}]}
            >
                <InputNumber/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'useExisting')}
                label="Use existing"
                extra={
                    <>
                        <p>If checked, Ontrack will look for an existing issue before creating one.</p>
                        <p>The existing issue is identified by the search term below.</p>
                    </>
                }
            >
                <Switch/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'requestStatus')}
                label="Request status"
                extra={"If looking for existing tickets, which type of requests to look for (all by default)."}
            >
                <SelectJiraServiceDeskRequestStatus/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'searchTerm')}
                label="Search term"
                extra="Template to use as a search term if existing issues must be reused"
            >
                <Input/>
            </Form.Item>
            <Form.List
                name={prefixedFormName(prefix, 'fields')}
                label="Fields"
                extra="List of fields to create in the service desk ticket."
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
                                    <Space
                                        key={key}
                                        orientation="vertical"
                                        className="ot-form-list-item"
                                        align="baseline"
                                    >
                                        <Form.Item
                                            {...restField}
                                            name={[name, 'name']}
                                            label={<FaTrash onClick={() => remove(name)}/>}
                                            rules={[
                                                {
                                                    required: true,
                                                    message: 'Field name is required.',
                                                },
                                            ]}
                                        >
                                            <Input placeholder="Field name"/>
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
                                            <Input.TextArea
                                                rows={4}
                                                placeholder="Field value"
                                            />
                                        </Form.Item>
                                        <Typography.Text>
                                            <FaExclamationTriangle color="orange"/>
                                            &nbsp;
                                            This <b>must</b> be a JSON representation,
                                            even for simple text values. Text values themselves can be
                                            templated.
                                        </Typography.Text>
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
        </>
    )
}