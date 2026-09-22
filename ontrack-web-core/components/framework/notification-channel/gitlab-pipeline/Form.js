import {Button, Form, Input, InputNumber, Select, Space, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";

export default function GitLabPipelineNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'config')}
                label="Config"
                extra="Name of the GitLab configuration to use for the connection."
                rules={[{required: true, message: 'Config name is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'project')}
                label="Project"
                extra="Full path of the GitLab project, subgroups included, like group/subgroup/project. Templated."
                rules={[{required: true, message: 'Project is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'ref')}
                label="Ref"
                extra="Branch or tag to run the pipeline on. Templated."
                rules={[{required: true, message: 'Ref is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                label="Variables"
                extra="Variables passed to the pipeline. Values are templated. They are not masked: secrets belong in the CI/CD variables of the GitLab project."
            >
                <Form.List name={prefixedFormName(prefix, 'variables')}>
                    {(fields, {add, remove}) => (
                        <Space orientation="vertical" style={{width: '100%'}}>
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
