import {Form, Input, Space, Switch, Typography} from "antd";
import Link from "next/link";
import ConfigurationPage from "@components/configurations/ConfigurationPage";
import GitHubConfigAuthenticationType from "@components/extension/github/GitHubConfigAuthenticationType";
import {FaGithub} from "react-icons/fa";
import GitHubConfigAppToken from "@components/extension/github/GitHubConfigAppToken";

export default function GitHubConfigurationsPage() {

    const columns = [
        {
            title: "Name",
            key: "name",
            dataIndex: "name"
        },
        {
            title: "URL",
            key: "url",
            dataIndex: "url",
            render: (value) => <Link href={value}>{value}</Link>,
        },
        {
            title: "Authentication",
            key: "authentication",
            render: (_, config) => <GitHubConfigAuthenticationType authenticationType={config.extra?.authenticationType}/>
        },
        {
            title: "Rate limits",
            key: "rateLimit",
            children: [
                {
                    title: "Core",
                    key: "rateLimit.core",
                    render: (_, config) => config.extra?.rateLimit ?
                        <Typography.Text>{config.extra.rateLimit.core.used}/{config.extra.rateLimit.core.limit}</Typography.Text> :
                        <Typography.Text>n/a</Typography.Text>

                },
                {
                    title: "GraphQL",
                    key: "rateLimit.graphql",
                    render: (_, config) => config.extra?.rateLimit ?
                        <Typography.Text>{config.extra.rateLimit.graphql.used}/{config.extra.rateLimit.graphql.limit}</Typography.Text> :
                        <Typography.Text>n/a</Typography.Text>
                },
            ],
        },
        {
            title: "User",
            key: "user",
            dataIndex: "user",
        },
        {
            title: <Space>
                <FaGithub/>
                GitHub App
            </Space>,
            key: 'app',
            children: [
                {
                    title: "App ID",
                    key: "app.id",
                    render: (_, config) => config.appId,
                },
                {
                    title: "Installation",
                    key: "app.installation",
                    render: (_, config) => config.appInstallationAccountName,
                },
                {
                    title: "Token",
                    key: "app.token",
                    render: (_ , config) => config.extra?.appToken &&
                        <GitHubConfigAppToken appToken={config.extra.appToken}/>
                },
            ],
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
            key="url"
            name="url"
            label="GitHub URL"
            extra="URL of the GitHub engine. Defaults to https://github.com if not defined."
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="user"
            name="user"
            label="Username"
            extra="Username used to connect to GitHub. Prefer using tokens or GitHub Apps."
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="password"
            name="password"
            label="Password"
            extra="Deprecated, removed in Yontrack 7: use a token or a GitHub App instead."
        >
            <Input.Password/>
        </Form.Item>,
        <Form.Item
            key="oauth2Token"
            name="oauth2Token"
            label="OAUth2 token"
            extra="Personal Access Token (PAT) used to connect to GitHub"
        >
            <Input.Password/>
        </Form.Item>,
        <Form.Item
            key="appId"
            name="appId"
            label="GitHub App ID"
            extra="ID of the GitHub App to use"
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="appPrivateKey"
            name="appPrivateKey"
            label="GitHub App PKey"
            extra="Private key for the GitHub App"
        >
            <Input.TextArea/>
        </Form.Item>,
        <Form.Item
            key="appInstallationAccountName"
            name="appInstallationAccountName"
            label="GitHub App Installation Account"
            extra="Optional. In case of several installations for this app, select the account where this app has been installed."
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="autoMergeToken"
            name="autoMergeToken"
            label="Auto merge token"
            extra="Token for an account used to approve pull requests for auto approval processes."
        >
            <Input.Password/>
        </Form.Item>,
        <Form.Item
            key="workflowSendId"
            name="workflowSendId"
            label="Send workflow ID"
            extra="Default for passing an id input to the dispatched workflows (auto-versioning post-processing, notifications). Needed only when this GitHub does not return the run of a dispatch. A workflow must declare the id input if and only if it is sent."
            valuePropName="checked"
            initialValue={false}
        >
            <Switch/>
        </Form.Item>,
    ]

    return (
        <>
            <ConfigurationPage
                pageTitle="GitHub configurations"
                configurationType="github"
                columns={columns}
                dialogItems={dialogItems}
            >
            </ConfigurationPage>
        </>
    )
}