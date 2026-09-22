import {Descriptions, Space, Typography} from "antd";

export default function Display({
                                    dockerImage,
                                    dockerCommand,
                                    commitMessage,
                                    config,
                                    job,
                                    parameters = [],
                                    credentials,
                                }) {

    const parametersItems = parameters.map(({name, value}) => ({
        key: name,
        label: name,
        children: <Typography.Text code>{value}</Typography.Text>,
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
            key: 'config',
            label: "Specific Jenkins config",
            children: <Typography.Text code>{config}</Typography.Text>,
            span: 12,
        },
        {
            key: 'job',
            label: "Specific Jenkins job",
            children: <Typography.Text code>{job}</Typography.Text>,
            span: 12,
        },
        {
            key: 'parameters',
            label: "Specific parameters",
            children: <Descriptions items={parametersItems}/>,
            span: 12,
        },
    ]

    return (
        <>
            <Space orientation="vertical">
                <Typography.Text code>jenkins</Typography.Text>
                <Descriptions
                    items={items}
                    span={12}
                />
            </Space>
        </>
    )
}