import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select, Space, Tag, Typography} from "antd";

export default function SelectNotificationChannel({value, onChange, onSelectedNotificationChannel, style, allowClear}) {

    const {data: channels, loading, finished} = useQuery(
        gql`
            query GetNotificationChannels {
                notificationChannels {
                    type
                    enabled
                }
            }
        `,
        {
            dataFn: data => data.notificationChannels,
        }
    )

    const options = (channels ?? []).map((channel) => ({
        value: channel.type,
        label: <Space>
            <Tag>{channel.type}</Tag>
            {
                !channel.enabled && <Typography.Text type="secondary">(disabled)</Typography.Text>
            }
        </Space>
    }))

    const onLocalChange = (value) => {
        if (onChange) onChange(value)
        if (onSelectedNotificationChannel) {
            onSelectedNotificationChannel(value)
        }
    }

    return (
        <>
            <Select
                options={options}
                loading={loading || !finished}
                value={value}
                onChange={onLocalChange}
                style={style}
                allowClear={allowClear}
            />
        </>
    )

}