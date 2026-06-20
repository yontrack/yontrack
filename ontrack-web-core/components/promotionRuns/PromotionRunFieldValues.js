import {useMemo} from "react";
import {Descriptions, Space, Typography} from "antd";

export default function PromotionRunFieldValues({fields, fieldValues}) {
    const fieldMap = useMemo(() => {
        const map = {}
        for (const f of (fields || [])) {
            map[f.name] = f
        }
        return map
    }, [fields])

    const items = fieldValues.map(fv => {
        const fieldDef = fieldMap[fv.name]
        const label = fieldDef?.displayName ?? fv.name
        const rawValue = fv.value
        let displayValue
        if (rawValue === null || rawValue === undefined) {
            displayValue = <Typography.Text type="secondary">—</Typography.Text>
        } else if (typeof rawValue === 'boolean' || fieldDef?.type === 'BOOLEAN') {
            displayValue = String(rawValue)
        } else if (fieldDef?.type === 'LINK') {
            displayValue = <Typography.Link href={String(rawValue)} target="_blank">{String(rawValue)}</Typography.Link>
        } else {
            displayValue = <Typography.Text code>{String(rawValue)}</Typography.Text>
        }
        return {key: fv.name, label, children: displayValue}
    })

    return (
        <Space direction="vertical" style={{width: '100%'}}>
            <Typography.Text strong>Field values</Typography.Text>
            <Descriptions items={items} column={2} size="small" bordered/>
        </Space>
    )
}
