import {Descriptions, Space, Typography} from "antd";

export default function Display({
                                    dockerImage,
                                    dockerCommand,
                                    commitMessage,
                                    env = [],
                                    secrets = [],
                                    timeoutSeconds,
                                }) {

    const envItems = (env ?? []).map(({name, value}) => ({
        key: name,
        label: name,
        children: <Typography.Text code>{value}</Typography.Text>,
    }))

    // Kubernetes Secret names and keys only: there is no secret value to hide
    const secretsItems = (secrets ?? []).map(({secret, key, env}) => ({
        key: env,
        label: env,
        children: <Typography.Text code>{`${secret}/${key}`}</Typography.Text>,
    }))

    const items = [
        {
            key: 'dockerImage',
            label: "Docker image",
            children: <Typography.Text code>{dockerImage}</Typography.Text>,
            span: 12,
        },
        {
            key: 'dockerCommand',
            label: "Docker command",
            children: <Typography.Text code>{dockerCommand}</Typography.Text>,
            span: 12,
        },
        {
            key: 'commitMessage',
            label: "Commit message",
            children: <Typography.Text code>{commitMessage}</Typography.Text>,
            span: 12,
        },
        {
            key: 'timeoutSeconds',
            label: "Timeout (seconds)",
            children: <Typography.Text code>{timeoutSeconds}</Typography.Text>,
            span: 12,
        },
        {
            key: 'env',
            label: "Environment variables",
            children: <Descriptions items={envItems}/>,
            span: 12,
        },
        {
            key: 'secrets',
            label: "Secrets (environment variable from secret/key)",
            children: <Descriptions items={secretsItems}/>,
            span: 12,
        },
    ]

    return (
        <div data-testid="av-post-processing-kubernetes">
            <Space orientation="vertical">
                <Typography.Text code>kubernetes</Typography.Text>
                <Descriptions
                    items={items}
                    span={12}
                />
            </Space>
        </div>
    )
}
