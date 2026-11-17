import {Descriptions, Space, Tag, Typography} from "antd";
import JsonDisplay from "@components/common/JsonDisplay";

const statusColor = (status) => {
    if (status >= 200 && status < 300) {
        return 'success'
    } else if (status >= 300 && status < 400) {
        return 'warning'
    } else {
        return 'error'
    }
}

export default function HttpNotificationChannelOutput({method, url, status, headers, body, truncated, attempts}) {

    const items = [
        {
            key: 'call',
            label: 'Call',
            children: <Space size={4} wrap>
                <Tag>{method}</Tag>
                <Typography.Text code copyable>{url}</Typography.Text>
            </Space>,
            span: 12,
        },
        {
            key: 'status',
            label: 'Status',
            children: status ?
                <Tag color={statusColor(status)}>{status}</Tag> :
                <Typography.Text type="secondary">No response</Typography.Text>,
            span: 6,
        },
        {
            key: 'attempts',
            label: 'Attempts',
            children: attempts ?? 0,
            span: 6,
        },
    ]

    const headerNames = headers ? Object.keys(headers) : []
    if (headerNames.length > 0) {
        items.push({
            key: 'headers',
            label: 'Response headers',
            children: <Descriptions
                size="small"
                column={1}
                items={headerNames.map(name => ({
                    key: name,
                    label: name,
                    children: <Typography.Text code>{headers[name]}</Typography.Text>,
                }))}
            />,
            span: 12,
        })
    }

    if (body !== null && body !== undefined) {
        items.push({
            key: 'body',
            label: truncated ? 'Response body (truncated)' : 'Response body',
            children: typeof body === 'string' ?
                <Typography.Paragraph>
                    <pre style={{whiteSpace: 'pre-wrap', maxWidth: '600px'}}>{body}</pre>
                </Typography.Paragraph> :
                <JsonDisplay value={JSON.stringify(body, null, 2)} width="600px" height="16em"/>,
            span: 12,
        })
    }

    return <Descriptions column={12} items={items}/>
}
