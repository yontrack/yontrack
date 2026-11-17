import {Descriptions, Space, Tag, Typography} from "antd";
import {FaArrowRight} from "react-icons/fa";
import JsonDisplay from "@components/common/JsonDisplay";

export default function HttpNotificationChannelConfig({
                                                          config,
                                                          method,
                                                          path,
                                                          headers,
                                                          body,
                                                          successCondition,
                                                          failureCondition,
                                                          statusUrl,
                                                          intervalSeconds,
                                                          timeoutSeconds,
                                                      }) {

    const items = [
        {
            key: 'call',
            label: 'Call',
            children: <Space size={4} wrap>
                <Tag>{method ?? 'GET'}</Tag>
                <Typography.Text code>{path || '/'}</Typography.Text>
                <Typography.Text>on</Typography.Text>
                <Typography.Text code>{config}</Typography.Text>
            </Space>,
            span: 12,
        },
    ]

    if (headers && headers.length > 0) {
        items.push({
            key: 'headers',
            label: 'Headers',
            children: <ul>
                {
                    headers.map(({name, value}) => (
                        <li key={name}>
                            <Space>
                                <Typography.Text code>{name}</Typography.Text>
                                <FaArrowRight/>
                                <Typography.Text code>{value}</Typography.Text>
                            </Space>
                        </li>
                    ))
                }
            </ul>,
            span: 12,
        })
    }

    if (body !== null && body !== undefined) {
        items.push({
            key: 'body',
            label: 'Body',
            children: <JsonDisplay value={JSON.stringify(body, null, 2)} width="600px" height="12em"/>,
            span: 12,
        })
    }

    if (successCondition) {
        items.push({
            key: 'successCondition',
            label: 'Success condition',
            children: <Typography.Text code>{successCondition}</Typography.Text>,
            span: 12,
        })
        if (failureCondition) {
            items.push({
                key: 'failureCondition',
                label: 'Failure condition',
                children: <Typography.Text code>{failureCondition}</Typography.Text>,
                span: 12,
            })
        }
        if (statusUrl) {
            items.push({
                key: 'statusUrl',
                label: 'Status URL',
                children: <Typography.Text code>{statusUrl}</Typography.Text>,
                span: 12,
            })
        }
        items.push({
            key: 'polling',
            label: 'Polling',
            children: <Typography.Text>
                Every {intervalSeconds ?? 10} s, for at most {timeoutSeconds ?? 300} s
            </Typography.Text>,
            span: 12,
        })
    }

    return <Descriptions column={12} items={items}/>
}
