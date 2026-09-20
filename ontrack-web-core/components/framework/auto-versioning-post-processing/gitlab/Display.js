import {Descriptions, Space, Typography} from "antd";

export default function Display({
                                    dockerImage,
                                    dockerCommand,
                                    commitMessage,
                                    config,
                                    project,
                                    ref,
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
            label: "Specific GitLab config",
            children: <Typography.Text code>{config}</Typography.Text>,
            span: 12,
        },
        {
            key: 'project',
            label: "Specific project for the pipeline",
            children: <Typography.Text code>{project}</Typography.Text>,
            span: 12,
        },
        {
            key: 'ref',
            label: "Specific ref for the pipeline",
            children: <Typography.Text code>{ref}</Typography.Text>,
            span: 12,
        },
    ]

    return (
        <div data-testid="av-post-processing-gitlab">
            <Space direction="vertical">
                <Typography.Text code>gitlab</Typography.Text>
                <Descriptions
                    items={items}
                    span={12}
                />
            </Space>
        </div>
    )
}
