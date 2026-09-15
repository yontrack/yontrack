import {Button, Form, Input, InputNumber, Select, Space, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";

export default function BitbucketPipelinesNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'config')}
                label="Config"
                extra="Name of the Bitbucket Cloud configuration to use for the connection."
                rules={[{required: true, message: 'Config name is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'workspace')}
                label="Workspace"
                extra="Slug of the Bitbucket Cloud workspace. Templated."
                rules={[{required: true, message: 'Workspace is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'repository')}
                label="Repository"
                extra="Slug of the repository. Templated."
                rules={[{required: true, message: 'Repository is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'branch')}
                label="Branch"
                extra="Branch to run the pipeline on. Templated."
                rules={[{required: true, message: 'Branch is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'pipeline')}
                label="Pipeline"
                extra="Name of a custom pipeline. Leave empty to run the default pipeline of the branch. Templated."
            >
                <Input/>
            </Form.Item>
            <Form.Item
                label="Variables"
                extra="Non-secured variables passed to the pipeline. Values are templated."
            >
                <Form.List name={prefixedFormName(prefix, 'variables')}>
                    {(fields, {add, remove}) => (
                        <Space direction="vertical" style={{width: '100%'}}>
                            {fields.map(({key, name, ...restField}) => (
                                <Space key={key}>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'name']}
                                        rules={[{required: true, message: 'Variable name is required.'}]}
                                    >
                                        <Input placeholder="Name"/>
                                    </Form.Item>
                                    <Form.Item
                                        {...restField}
                                        name={[name, 'value']}
                                        rules={[{required: true, message: 'Variable value is required.'}]}
                                    >
                                        <Input placeholder="Value"/>
                                    </Form.Item>
                                    <FaTrash onClick={() => remove(name)}/>
                                </Space>
                            ))}
                            <Button type="dashed" onClick={() => add()} block>
                                <Space>
                                    <FaPlus/>
                                    <Typography.Text>Add variable</Typography.Text>
                                </Space>
                            </Button>
                        </Space>
                    )}
                </Form.List>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'callMode')}
                label="Call mode"
                extra="How to call the pipeline."
                initialValue="ASYNC"
            >
                <Select
                    options={[
                        {value: 'ASYNC', label: 'ASYNC - Fires and forgets'},
                        {value: 'SYNC', label: 'SYNC - Waits for the pipeline to complete'},
                    ]}
                />
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'timeoutSeconds')}
                label="Timeout"
                extra="Timeout in seconds, when waiting for the pipeline"
                initialValue={30}
            >
                <InputNumber min={1} max={7200}/>
            </Form.Item>
        </>
    )
}
