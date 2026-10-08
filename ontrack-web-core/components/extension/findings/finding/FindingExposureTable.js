import Link from "next/link";
import {Empty, Space, Tooltip, Typography} from "antd";
import Table from "@components/common/table/Table";
import {branchUri, validationStampUri} from "@components/common/Links";
import PeriodBuild from "@components/extension/findings/finding/PeriodBuild";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {
    earlierPeriodsSummary,
    exposureRows,
    formatExposureDuration,
} from "@components/extension/findings/findingsModel";

const resolutionReasons = {
    ABSENT: 'no longer reported',
}

/**
 * The exposure of a finding on the branches of its project, one row per branch and stamp: the
 * build it was discovered in and the one it was fixed in, for its current period, and how long it
 * has been exposed — an accepted exposure counting as exposed.
 */
export default function FindingExposureTable({exposures}) {

    const rows = exposureRows(exposures).map(row => ({
        ...row,
        current: row.periods?.length ? row.periods[row.periods.length - 1] : null,
    }))

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
            render: (_, {branch, validationStamp, state, acceptanceExpiresAt, periods, current}) =>
                <Space orientation="vertical" size={0}
                       data-testid={`finding-exposure-${branch.name}-${validationStamp.name}`}>
                    <FindingStateTag state={state}/>
                    {
                        state === 'ACCEPTED' &&
                        <Typography.Text type="secondary">
                            {acceptanceExpiresAt ? `until ${acceptanceExpiresAt}` : 'without expiry'}
                        </Typography.Text>
                    }
                    {
                        periods?.length > 1 && current?.ongoing &&
                        <Typography.Text type="secondary">reopened</Typography.Text>
                    }
                </Space>,
        },
        {
            key: 'discoveredIn',
            title: 'Discovered in',
            render: (_, {since, current}) =>
                <Space orientation="vertical" size={0}>
                    {current && <PeriodBuild run={current.startedBy} name={current.startedInBuild}/>}
                    <Typography.Text type="secondary">
                        <TimestampText value={current?.startedAt ?? since}/>
                    </Typography.Text>
                </Space>,
        },
        {
            key: 'fixedIn',
            title: 'Fixed in',
            render: (_, {resolvedAt, resolutionReason, current}) =>
                resolvedAt ?
                    <Space orientation="vertical" size={0}>
                        {current && <PeriodBuild run={current.endedBy} name={current.endedInBuild}/>}
                        <Typography.Text type="secondary">
                            <TimestampText value={current?.endedAt ?? resolvedAt}/>
                            {
                                resolutionReason &&
                                <> · {resolutionReasons[resolutionReason] ?? resolutionReason}</>
                            }
                        </Typography.Text>
                    </Space> :
                    <Typography.Text type="secondary">Not resolved</Typography.Text>,
        },
        {
            key: 'exposedFor',
            title: 'Exposed for',
            render: (_, {state, periods, current}) => {
                if (!current) return null
                const earlier = earlierPeriodsSummary(periods)
                return (
                    <Space orientation="vertical" size={0} data-testid="finding-exposure-duration">
                        <Tooltip title={
                            <>
                                <TimestampText value={current.startedAt}/>
                                {' → '}
                                {current.endedAt ? <TimestampText value={current.endedAt}/> : 'now'}
                            </>
                        }>
                            <Typography.Text>{formatExposureDuration(current.durationSeconds, current.ongoing)}</Typography.Text>
                        </Tooltip>
                        {
                            state === 'ACCEPTED' &&
                            <Typography.Text type="secondary">accepted</Typography.Text>
                        }
                        {
                            earlier &&
                            <Typography.Text type="secondary">{earlier}</Typography.Text>
                        }
                    </Space>
                )
            },
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
