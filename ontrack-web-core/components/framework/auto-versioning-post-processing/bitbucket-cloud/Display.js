import {Descriptions, Space, Typography} from "antd";

export default function Display({
                                    dockerImage,
                                    dockerCommand,
                                    commitMessage,
                                    config,
                                    workspace,
                                    repository,
                                    pipeline,
                                    branch,
                                }) {

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
            key: 'config',
            label: "Specific Bitbucket Cloud config",
            children: <Typography.Text code>{config}</Typography.Text>,
            span: 12,
        },
        {
            key: 'workspace',
            label: "Specific workspace for the pipeline",
            children: <Typography.Text code>{workspace}</Typography.Text>,
            span: 12,
        },
        {
            key: 'repository',
            label: "Specific repository for the pipeline",
            children: <Typography.Text code>{repository}</Typography.Text>,
            span: 12,
        },
        {
            key: 'pipeline',
            label: "Specific custom pipeline",
            children: <Typography.Text code>{pipeline}</Typography.Text>,
            span: 12,
        },
        {
            key: 'branch',
            label: "Specific branch for the pipeline",
            children: <Typography.Text code>{branch}</Typography.Text>,
            span: 12,
        },
    ]

    return (
        <div data-testid="av-post-processing-bitbucket-cloud">
            <Space direction="vertical">
                <Typography.Text code>bitbucket-cloud</Typography.Text>
                <Descriptions
                    items={items}
                    span={12}
                />
            </Space>
        </div>
    )
}
