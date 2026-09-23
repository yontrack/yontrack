import {Space, Table, Typography} from "antd";
import AutoVersioningConfigNotificationScope
    from "@components/extension/auto-versioning/AutoVersioningConfigNotificationScope";
import NotificationChannelConfig from "@components/extension/notifications/NotificationChannelConfig";

const {Column} = Table

export default function AutoVersioningConfigNotifications({notifications}) {
    return (
        <>
            {
                notifications && notifications.length > 0 &&
                <Table
                    dataSource={notifications}
                    pagination={false}
                >

                    <Column
                        key="scope"
                        title="Scope"
                        render={(_, notification) => <AutoVersioningConfigNotificationScope
                            scopes={notification.scope}
                        />}
                    />

                    <Column
                        key="notification"
                        title="Notification"
                        render={(_, notification) => (
                            <>
                                <Space direction="vertical">
                                    <Typography.Text code>{notification.channel}</Typography.Text>
                                    <NotificationChannelConfig
                                        channel={notification.channel}
                                        config={notification.config}
                                    />
                                    {
                                        notification.notificationTemplate &&
                                        <>
                                            <Typography.Text>Custom template:</Typography.Text>
                                            <Typography.Paragraph code>{notification.notificationTemplate}</Typography.Paragraph>
                                        </>
                                    }
                                </Space>
                            </>
                        )
                        }
                    />

                </Table>
            }
        </>
    )
}