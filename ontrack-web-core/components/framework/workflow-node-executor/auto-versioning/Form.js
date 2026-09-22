import {Alert, Form, Input} from "antd";

export default function AutoVersioningWorkflowNodeExecutorForm() {

    return (
        <>
            <Form.Item>
                <Alert
                    type="warning"
                    title="Full configuration is only available through code."
                />
            </Form.Item>
            <Form.Item name={['data', 'targetProject']}
                       label="Target project"
                       extra="(template) Name of the project to update"
                       rules={[
                           {
                               required: true,
                               message: 'Target project name is required.',
                           },
                       ]}
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetBranch']}
                       label="Target branch"
                       extra="(template) Name of the branch to update"
                       rules={[
                           {
                               required: true,
                               message: 'Target branch name is required.',
                           },
                       ]}
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetVersion']}
                       label="Target version"
                       extra="(template) Version to set in the target branch"
                       rules={[
                           {
                               required: true,
                               message: 'Target version is required.',
                           },
                       ]}
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetPath']}
                       label="Target path"
                       extra="Path to update in the repository of the target branch"
                       rules={[
                           {
                               required: true,
                               message: 'Target path is required.',
                           },
                       ]}
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetProperty']}
                       label="Target property"
                       extra="Property to change"
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetRegex']}
                       label="Target regex"
                       extra="Regular expression used to identify the version to change"
            >
                <Input/>
            </Form.Item>
            <Form.Item name={['data', 'targetPropertyType']}
                       label="Target property type"
                       extra="Type of the file to update"
            >
                <Input/>
            </Form.Item>
        </>
    )

}