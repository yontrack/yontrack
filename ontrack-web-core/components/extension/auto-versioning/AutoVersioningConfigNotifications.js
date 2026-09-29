import {Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import AutoVersioningConfigNotificationScope
    from "@components/extension/auto-versioning/AutoVersioningConfigNotificationScope";
import NotificationChannelConfig from "@components/extension/notifications/NotificationChannelConfig";

const {Column} = Table

/**
 * @param notifications Notifications of an auto-versioning configuration
 * @param sticky Passed to the table - `false` where it is nested in another table's expanded row
 */
export default function AutoVersioningConfigNotifications({notifications, sticky}) {
    return (
        <>
            {
                notifications && notifications.length > 0 &&
                <Table
                    dataSource={notifications}
                    pagination={false}
                    sticky={sticky}
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
                                <Space orientation="vertical">
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