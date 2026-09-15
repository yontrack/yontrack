import {Descriptions, Typography} from "antd";
import Link from "next/link";

export default function BitbucketPipelinesNotificationChannelOutput({
                                                                        workspace,
                                                                        repository,
                                                                        branch,
                                                                        pipeline,
                                                                        buildNumber,
                                                                        url,
                                                                        state,
                                                                        variables = [],
                                                                    }) {
    return (
        <Descriptions
            column={12}
            items={[
                {
                    key: 'url',
                    label: 'Pipeline run',
                    children: url ?
                        <Link href={url}>#{buildNumber}</Link> :
                        <Typography.Text type="secondary">Not triggered</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'state',
                    label: 'State',
                    children: state ?
                        <Typography.Text code>{state}</Typography.Text> :
                        <Typography.Text type="secondary">Not followed</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'target',
                    label: 'Target',
                    children: <Typography.Text code>{workspace}/{repository}@{branch}</Typography.Text>,
                    span: 12,
                },
                {
                    key: 'pipeline',
                    label: 'Pipeline',
                    children: pipeline ?
                        <Typography.Text code>{pipeline}</Typography.Text> :
                        <Typography.Text type="secondary">Default</Typography.Text>,
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
