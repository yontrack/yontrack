import Link from "next/link";
import {Empty, Space, Table, Typography} from "antd";
import {branchUri, validationStampUri} from "@components/common/Links";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {exposureRows} from "@components/extension/findings/findingsModel";

const resolutionReasons = {
    ABSENT: 'no longer reported by the latest scan',
}

/**
 * The exposure of a finding on the branches of its project, one row per branch and stamp, with
 * the start of the exposure, and its end when it is resolved.
 */
export default function FindingExposureTable({exposures}) {

    const rows = exposureRows(exposures)

    const columns = [
        {
            key: 'branch',
            title: 'Branch',
            onCell: (row) => ({rowSpan: row.branchRowSpan}),
            render: (_, {branch}) => <Link href={branchUri(branch)}>{branch.name}</Link>,
        },
        {
            key: 'validationStamp',
            title: 'Validation stamp',
            render: (_, {validationStamp}) =>
                <Link href={validationStampUri(validationStamp)}>{validationStamp.name}</Link>,
        },
        {
            key: 'state',
            title: 'State',
            render: (_, {branch, validationStamp, state, acceptanceExpiresAt}) =>
                <Space size={4} data-testid={`finding-exposure-${branch.name}-${validationStamp.name}`}>
                    <FindingStateTag state={state}/>
                    {
                        state === 'ACCEPTED' &&
                        <Typography.Text type="secondary">
                            {acceptanceExpiresAt ? `until ${acceptanceExpiresAt}` : 'without expiry'}
                        </Typography.Text>
                    }
                </Space>,
        },
        {
            key: 'since',
            title: 'Exposed since',
            render: (_, {since}) => <TimestampText value={since}/>,
        },
        {
            key: 'resolvedAt',
            title: 'Resolved',
            render: (_, {resolvedAt, resolutionReason}) =>
                resolvedAt ?
                    <Space orientation="vertical" size={0}>
                        <TimestampText value={resolvedAt}/>
                        {
                            resolutionReason &&
                            <Typography.Text type="secondary">
                                {resolutionReasons[resolutionReason] ?? resolutionReason}
                            </Typography.Text>
                        }
                    </Space> :
                    <Typography.Text type="secondary">Not resolved</Typography.Text>,
        },
    ]

    return (
        <Table
            data-testid="finding-exposure"
            size="small"
            rowKey="key"
            columns={columns}
            dataSource={rows}
            pagination={false}
            locale={{
                emptyText: <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="This finding is not exposed on any branch"
                />,
            }}
        />
    )
}
