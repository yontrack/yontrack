import {Space, Tag, Typography} from "antd";
import Table from "@components/common/table/Table";
import PromotionRunLink from "@components/promotionRuns/PromotionRunLink";

const statusColors = {
    PROMOTED: 'success',
    SKIPPED: 'default',
    FAILED: 'error',
}

const columns = [
    {title: 'Project', dataIndex: 'project', key: 'project'},
    {title: 'Branch', dataIndex: 'branch', key: 'branch'},
    {title: 'Build', dataIndex: 'build', key: 'build'},
    {
        title: 'Qualifiers',
        dataIndex: 'qualifiers',
        key: 'qualifiers',
        render: (qualifiers) => (
            <Space wrap>
                {(qualifiers ?? []).map(qualifier =>
                    qualifier ?
                        <Typography.Text key={qualifier} code>{qualifier}</Typography.Text> :
                        <Typography.Text key="" type="secondary">Unqualified</Typography.Text>
                )}
            </Space>
        ),
    },
    {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (status, {runId}) => (
            <Space>
                <Tag color={statusColors[status]}>{status}</Tag>
                {runId && <PromotionRunLink promotionRun={{id: runId}} text="Promotion run"/>}
            </Space>
        ),
    },
    {title: 'Message', dataIndex: 'message', key: 'message'},
]

export default function YontrackDependenciesPromotionNotificationChannelOutput({targets = []}) {
    return (
        // Not sticky: the output of a notification is displayed in the expanded row of the notifications table
        <Table
            data-testid="yontrack-dependencies-promotion-targets"
            dataSource={targets}
            columns={columns}
            rowKey={({project, branch, build}) => `${project}/${branch}/${build}`}
            pagination={false}
            size="small"
            sticky={false}
            locale={{emptyText: 'No linked build was selected.'}}
        />
    )
}
