import Link from "next/link";
import {Empty, Space, Tooltip, Typography} from "antd";
import Table from "@components/common/table/Table";
import {branchUri, validationStampUri} from "@components/common/Links";
import PeriodBuild from "@components/extension/findings/finding/PeriodBuild";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {
    boundaryBuildName,
    boundaryDurationText,
    boundaryFilterShortName,
    boundaryMatchedText,
    earlierPeriodsSummary,
    exposureRows,
    formatExposureDuration,
    lastEndedPeriod,
} from "@components/extension/findings/findingsModel";

const resolutionReasons = {
    ABSENT: 'no longer reported',
}

/**
 * What a period which ended reads against a filter, as text: the build, or why there is none.
 */
function BoundaryCell({boundary, reopening, shortName}) {
    switch (boundary?.status) {
        case 'MATCHED':
            return (
                <Space orientation="vertical" size={0}>
                    <PeriodBuild run={{build: boundary.build}} name={boundaryBuildName(boundary)}/>
                    <Typography.Text type="secondary">{boundaryMatchedText(boundary, shortName)}</Typography.Text>
                </Space>
            )
        case 'NONE_YET':
            return <Typography.Text>None yet</Typography.Text>
        case 'NOT_REACHED':
            return (
                <Space orientation="vertical" size={0}>
                    <Typography.Text>Not reached</Typography.Text>
                    <Typography.Text type="secondary">
                        reopened in <PeriodBuild run={reopening?.startedBy} name={reopening?.startedInBuild}/> first
                    </Typography.Text>
                </Space>
            )
        case 'NOT_APPLICABLE':
            return (
                <Space orientation="vertical" size={0}>
                    <Typography.Text>Doesn&apos;t apply</Typography.Text>
                    <Typography.Text type="secondary">{boundary.reason}</Typography.Text>
                </Space>
            )
        default:
            return <Typography.Text type="secondary">Not resolved</Typography.Text>
    }
}

/**
 * The exposure of a finding on the branches of its project, one row per branch and stamp: the
 * build it was discovered in and the one it was fixed in, for its current period, and how long it
 * has been exposed — an accepted exposure counting as exposed.
 *
 * Read against a filter, it says as well whether a build matching it has been reached, and a reopened
 * exposure shows the last period which ended.
 *
 * @param exposures The exposures of the finding, with the boundaries of their periods under a filter
 * @param filter The filter they have been read against, if any
 */
export default function FindingExposureTable({exposures, filter}) {

    const rows = exposureRows(exposures).map(row => {
        const current = row.periods?.length ? row.periods[row.periods.length - 1] : null
        const ended = lastEndedPeriod(row.periods)
        return {
            ...row,
            current,
            ended,
            reopened: !!current?.ongoing && !!ended,
        }
    })
    const shortName = boundaryFilterShortName(filter)

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
            render: (_, {resolvedAt, resolutionReason, current, ended, reopened}) =>
                filter && reopened ?
                    <Space orientation="vertical" size={0}>
                        <span><PeriodBuild run={ended.endedBy} name={ended.endedInBuild}/>, reopened</span>
                        <Typography.Text type="secondary"><TimestampText value={ended.endedAt}/></Typography.Text>
                    </Space> :
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
        ...(filter ? [{
            key: 'matchingBuild',
            title: 'In a matching build',
            render: (_, {ended, current}) =>
                <div data-testid="finding-exposure-matching-build">
                    <BoundaryCell boundary={ended?.boundary} reopening={current} shortName={shortName}/>
                </div>,
        }] : []),
        {
            key: 'exposedFor',
            title: 'Exposed for',
            render: (_, {state, periods, current, ended}) => {
                if (!current) return null
                const earlier = earlierPeriodsSummary(periods)
                const filtered = filter ? boundaryDurationText(ended?.boundary, shortName) : ''
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
                            filtered &&
                            <Typography.Text type="secondary"
                                             data-testid="finding-exposure-duration-filtered">{filtered}</Typography.Text>
                        }
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
