import {Button, Form, Input, InputNumber, Select, Space, Typography} from "antd";
import {FaPlus, FaTrash} from "react-icons/fa";
import {prefixedFormName} from "@components/form/formUtils";

export default function GitHubWorkflowNotificationChannelForm({prefix}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'config')}
                label="Config"
                extra="Name of the GitHub configuration to use for the connection."
                rules={[{required: true, message: 'Config name is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'owner')}
                label="Owner"
                extra="Repository owner for the workflow"
                rules={[{required: true, message: 'Owner is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'repository')}
                label="Repository"
                extra="Repository name for the workflow"
                rules={[{required: true, message: 'Repository is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'workflowId')}
                label="Workflow ID"
                extra="Workflow ID (like main.yml)"
                rules={[{required: true, message: 'Workflow ID is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'reference')}
                label="Reference"
                extra="Git reference (branch, tag or commit SHA) for the workflow"
                rules={[{required: true, message: 'Reference is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.List
                name={prefixedFormName(prefix, 'inputs')}
                label="Inputs"
                extra="Inputs to send to the workflow."
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
                                                    message: 'Input name is required.',
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
                                                    message: 'Input value is required.',
                                                },
                                            ]}
                                        >
                                            <Input placeholder="Value"/>
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
                                    <Typography.Text>Add input</Typography.Text>
                                </Space>
                            </Button>
                        </div>
                    </>
                )}
            </Form.List>
            <Form.Item
                name={prefixedFormName(prefix, 'callMode')}
                label="Call mode"
                extra="How to call the workflow."
                initialValue="ASYNC"
            >
                <SelectGitHubWorkflowNotificationChannelConfigCallMode/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'timeoutSeconds')}
                label="Timeout"
                extra="Timeout in seconds"
                initialValue={30}
            >
                <InputNumber min={1} max={7200}/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'sendId')}
                label="Send workflow ID"
                extra="Passing an id input to the workflow. Needed only when GitHub does not return the run of a dispatch. The workflow must declare the id input if and only if it is sent."
            >
                <SelectGitHubWorkflowSendId/>
            </Form.Item>
        </>
    )
}

export function SelectGitHubWorkflowSendId({value, onChange}) {
    const options = [
        {
            value: 'DEFAULT',
            label: 'Default (from the GitHub configuration)',
        },
        {
            value: 'YES',
            label: 'Yes - sends the id input',
        },
        {
            value: 'NO',
            label: 'No - does not send the id input',
        },
    ]

    const selected = value === true ? 'YES' : value === false ? 'NO' : 'DEFAULT'

    return (
        <>
            <Select
                options={options}
                value={selected}
                onChange={(it) => onChange(it === 'YES' ? true : it === 'NO' ? false : null)}
            />
        </>
    )
}

export function SelectGitHubWorkflowNotificationChannelConfigCallMode({value, onChange}) {
    const options = [
        {
            value: 'ASYNC',
            label: 'ASYNC - Fires and forgets',
        },
        {
            value: 'SYNC',
            label: 'SYNC - Waits for run completion',
        },
    ]

    return (
        <>
            <Select
                options={options}
                value={value}
                onChange={onChange}
            />
        </>
    )
}