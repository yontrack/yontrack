import {Form, Input, Space, Typography} from "antd";

export default function MailNotificationChannelConfig({to, cc, subject}) {
    return (
        <>
            <Space orientation="vertical">
                <Space>
                    To:
                    <Typography.Text code>{to}</Typography.Text>
                </Space>
                {
                    cc &&
                    <Space>
                        Cc:
                        <Typography.Text code>{cc}</Typography.Text>
                    </Space>
                }
                <Space>
                    Subject:
                    <Typography.Text code>{subject}</Typography.Text>
                </Space>
            </Space>
        </>
    )
}