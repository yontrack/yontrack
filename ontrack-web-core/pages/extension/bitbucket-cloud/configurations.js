import {Form, Input, Select} from "antd";
import ConfigurationPage from "@components/configurations/ConfigurationPage";

const authTypeOptions = [
    {
        value: 'API_TOKEN',
        label: 'API token (Atlassian account email + API token)',
    },
    {
        value: 'ACCESS_TOKEN',
        label: 'Access token (workspace, project or repository access token)',
    },
]

export default function BitbucketCloudConfigurationsPage() {

    const columns = [
        {
            title: "Name",
            key: "name",
            dataIndex: "name"
        },
        {
            title: "Authentication",
            key: "authType",
            dataIndex: "authType",
            render: (value) => authTypeOptions.find(it => it.value === value)?.label ?? value,
        },
        {
            title: "Email",
            key: "email",
            dataIndex: "email",
        },
        {
            title: "Auto-merge email",
            key: "autoMergeEmail",
            dataIndex: "autoMergeEmail",
        },
    ]

    const dialogItems = [
        <Form.Item
            key="name"
            name="name"
            label="Configuration name"
            rules={[{required: true, message: 'Name is required.',},]}
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="authType"
            name="authType"
            label="Authentication type"
            initialValue="API_TOKEN"
            rules={[{required: true, message: 'Authentication type is required.',},]}
        >
            <Select options={authTypeOptions}/>
        </Form.Item>,
        <Form.Item
            key="credentials"
            noStyle
            shouldUpdate={(previous, current) => previous.authType !== current.authType}
        >
            {({getFieldValue}) =>
                getFieldValue('authType') === 'ACCESS_TOKEN' ?
                    <Form.Item
                        name="token"
                        label="Access token"
                        extra="Workspace or project access token (Premium plan), or repository access token. Used as a Bearer token."
                    >
                        <Input.Password/>
                    </Form.Item> :
                    <>
                        <Form.Item
                            name="email"
                            label="Email"
                            extra="Email of the Atlassian account owning the API token."
                            rules={[{required: true, message: 'Email is required for an API token.',},]}
                        >
                            <Input/>
                        </Form.Item>
                        <Form.Item
                            name="token"
                            label="API token"
                            extra="API token of the Atlassian account, with Bitbucket scopes."
                        >
                            <Input.Password/>
                        </Form.Item>
                    </>
            }
        </Form.Item>,
        <Form.Item
            key="autoMergeEmail"
            name="autoMergeEmail"
            label="Auto-merge email"
            extra="Email of the Atlassian account approving pull requests for the auto merge operations."
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="autoMergeToken"
            name="autoMergeToken"
            label="Auto-merge API token"
            extra="API token of the account approving pull requests for the auto merge operations."
        >
            <Input.Password/>
        </Form.Item>,
    ]

    return (
        <>
            <ConfigurationPage
                pageTitle="Bitbucket Cloud configurations"
                configurationType="bitbucket-cloud"
                columns={columns}
                dialogItems={dialogItems}
            >
            </ConfigurationPage>
        </>
    )
}
