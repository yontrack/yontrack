import {Form, Input} from "antd";
import Link from "next/link";
import ConfigurationPage from "@components/configurations/ConfigurationPage";

export default function JenkinsConfigurationsPage() {

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
            title: "User",
            key: "user",
            dataIndex: "user"
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
            label="Jenkins URL"
            rules={[{required: true, message: 'URL is required.',},]}
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="user"
            name="user"
            label="Jenkins username"
        >
            <Input/>
        </Form.Item>,
        <Form.Item
            key="password"
            name="password"
            label="Jenkins password"
        >
            <Input.Password/>
        </Form.Item>,
    ]

    return (
        <>
            <ConfigurationPage
                pageTitle="Jenkins configurations"
                configurationType="jenkins"
                columns={columns}
                dialogItems={dialogItems}
            >
            </ConfigurationPage>
        </>
    )
}