import {Form, Input} from "antd";
import ConfigurationPage from "@components/configurations/ConfigurationPage";
import HttpAuthenticationFormItems from "@components/extension/notifications/http/HttpAuthenticationFormItems";
import HttpAuthenticationSummary from "@components/extension/notifications/http/HttpAuthenticationSummary";

export default function HttpConfigurationsPage() {

    const columns = [
        {
            title: "Name",
            key: "name",
            dataIndex: "name"
        },
        {
            title: "Base URL",
            key: "url",
            dataIndex: "url",
        },
        {
            title: "Authentication",
            key: "authentication",
            render: (_, config) => <HttpAuthenticationSummary authenticationType={config.authentication?.type}/>,
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
            label="Base URL"
            extra="Absolute http or https URL, without query nor credentials. The HTTP calls made through this configuration stay under it."
            rules={[{required: true, message: 'Base URL is required.',},]}
        >
            <Input/>
        </Form.Item>,
        <HttpAuthenticationFormItems key="authentication"/>,
    ]

    return (
        <>
            <ConfigurationPage
                pageTitle="HTTP configurations"
                configurationType="http"
                columns={columns}
                dialogItems={dialogItems}
            >
            </ConfigurationPage>
        </>
    )
}
