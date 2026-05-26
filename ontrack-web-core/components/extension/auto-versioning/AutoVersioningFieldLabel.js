import {Space, Typography} from "antd";

export default function FieldLabel({fieldName, description}) {
    return (
        <Space direction="vertical" size={0}>
            <Typography.Text code style={{fontSize: '0.85em'}}>{fieldName}</Typography.Text>
            <Typography.Text type="secondary" style={{fontSize: '0.8em'}}>{description}</Typography.Text>
        </Space>
    )
}
