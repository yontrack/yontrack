import {Space, Typography} from "antd";

/**
 * Read-only display of the version rule guarding the version changes of an auto-versioning
 * configuration. There is no per-rule display component: rules are few and their configuration
 * is a small flat object, so the raw JSON is shown next to the rule ID.
 */
export default function AutoVersioningVersionRule({rule, config}) {
    return (
        <>
            {
                !rule && <Typography.Text type="secondary">None</Typography.Text>
            }
            {
                rule &&
                <Space>
                    <Typography.Text code>{rule}</Typography.Text>
                    {
                        config && <Typography.Text code>{JSON.stringify(config)}</Typography.Text>
                    }
                </Space>
            }
        </>
    )
}
