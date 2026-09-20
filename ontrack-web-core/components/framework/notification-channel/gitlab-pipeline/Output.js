import {Descriptions, Typography} from "antd";
import Link from "next/link";

export default function GitLabPipelineNotificationChannelOutput({
                                                                    project,
                                                                    ref,
                                                                    iid,
                                                                    url,
                                                                    status,
                                                                    variables = [],
                                                                }) {
    return (
        <Descriptions
            column={12}
            items={[
                {
                    key: 'url',
                    label: 'Pipeline',
                    children: url ?
                        <Link href={url}>#{iid}</Link> :
                        <Typography.Text type="secondary">Not triggered</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'status',
                    label: 'Status',
                    children: status ?
                        <Typography.Text code>{status}</Typography.Text> :
                        <Typography.Text type="secondary">Not known</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'target',
                    label: 'Target',
                    children: <Typography.Text code>{project}@{ref}</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'variables',
                    label: 'Variables',
                    children: variables.length === 0 ?
                        <Typography.Text type="secondary">None</Typography.Text> :
                        <Descriptions
                            items={variables.map(({name, value}) => ({
                                key: name,
                                label: name,
                                children: <code>{value}</code>,
                                span: 12,
                            }))}
                        />,
                    span: 12,
                },
            ]}
        />
    )
}
