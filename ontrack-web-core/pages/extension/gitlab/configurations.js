import {Form, Input, Switch} from "antd";
import Link from "next/link";
import ConfigurationPage from "@components/configurations/ConfigurationPage";
import YesNo from "@components/common/YesNo";

export default function GitLabConfigurationsPage() {

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
            title: "Ignore SSL",
            key: "ignoreSslCertificate",
            dataIndex: "ignoreSslCertificate",
            render: (value) => <YesNo value={value}/>,
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
            label="GitLab URL"
            extra="URL of the GitLab instance, like https://gitlab.com."
            rules={[{required: true, message: 'URL is required.',},]}
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="token"
            name="token"
            label="Token"
            extra="Personal access token used by Yontrack to connect to GitLab, with the api scope."
        >
            <Input.Password/>
        </Form.Item>,
        <Form.Item
            key="ignoreSslCertificate"
            name="ignoreSslCertificate"
            label="Ignore SSL certificate"
            extra="Check to accept any SSL certificate of the GitLab server."
            valuePropName="checked"
            initialValue={false}
        >
            <Switch/>
        </Form.Item>,
    ]

    return (
        <>
            <ConfigurationPage
                pageTitle="GitLab configurations"
                configurationType="gitlab"
                columns={columns}
                dialogItems={dialogItems}
            >
            </ConfigurationPage>
        </>
    )
}
