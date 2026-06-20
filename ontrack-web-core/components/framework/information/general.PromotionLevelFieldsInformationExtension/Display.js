import {Table, Tag, Typography} from "antd";

export default function Display({info}) {
    const fields = info.data || []
    const columns = [
        {title: "Name", key: "name", render: (_, field) => <><strong>{field.displayName}</strong> <Typography.Text type="secondary" code>{field.name}</Typography.Text></>},
        {
            title: "Type", dataIndex: "type", key: "type",
            render: (type) => <Tag>{type}</Tag>
        },
        {
            title: "Required", dataIndex: "required", key: "required",
            render: (required) => required ? "✓" : "–"
        },
        {
            title: "Options", key: "options",
            render: (_, field) => field.type === 'CHOICE'
                ? (field.options || []).join(', ')
                : null
        },
        {title: "Description", dataIndex: "description", key: "description"},
    ]
    return (
        <Table
            dataSource={fields}
            columns={columns}
            rowKey="name"
            size="small"
            pagination={false}
        />
    )
}
