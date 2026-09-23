import {useState} from "react";
import Link from "next/link";
import {gql} from "graphql-request";
import {Button, Empty, Skeleton, Space, Timeline, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {branchUri, buildUri, validationRunUri, validationStampUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import {FindingAcceptanceTag} from "@components/extension/findings/finding/FindingAcceptance";
import TimestampText from "@components/common/TimestampText";

const PAGE_SIZE = 20

const severityColors = {
    CRITICAL: 'red',
    HIGH: 'orange',
    MEDIUM: 'gold',
    LOW: 'blue',
    UNKNOWN: 'gray',
}

const gqlFindingObservations = gql`
    query FindingObservations($id: Int!, $size: Int!) {
        finding(id: $id) {
            observations(offset: 0, size: $size) {
                pageInfo {
                    totalSize
                }
                pageItems {
                    time
                    severity
                    rawSeverity
                    installedVersion
                    fixedVersion
                    acceptance {
                        effective
                        statement
                        expiresAt
                        source
                    }
                    validationRun {
                        id
                        runOrder
                        build {
                            id
                            name
                            branch {
                                id
                                name
                            }
                        }
                        validationStamp {
                            id
                            name
                        }
                    }
                }
            }
        }
    }
`

function Observation({observation}) {
    const {severity, rawSeverity, installedVersion, fixedVersion, acceptance, validationRun} = observation
    const {build, validationStamp} = validationRun
    return (
        <Space orientation="vertical" size={4} data-testid="finding-observation">
            <Space size={4} wrap>
                <Typography.Text strong><TimestampText value={observation.time}/></Typography.Text>
                <FindingSeverityTag severity={severity}/>
                {
                    rawSeverity && rawSeverity !== severity &&
                    <Typography.Text type="secondary">reported as {rawSeverity}</Typography.Text>
                }
            </Space>
            <Typography.Text>
                Build <Link href={buildUri(build)}>{build.name}</Link>
                {' '}on <Link href={branchUri(build.branch)}>{build.branch.name}</Link>,
                {' '}<Link href={validationRunUri(validationRun)}>run #{validationRun.runOrder}</Link>
                {' '}of <Link href={validationStampUri(validationStamp)}>{validationStamp.name}</Link>
            </Typography.Text>
            {
                installedVersion &&
                <Typography.Text type="secondary">
                    Installed <Typography.Text code>{installedVersion}</Typography.Text>
                    {
                        fixedVersion ?
                            <>, fixed in <Typography.Text code>{fixedVersion}</Typography.Text></> :
                            ', no fix known'
                    }
                </Typography.Text>
            }
            {
                acceptance &&
                <Space size={4} wrap>
                    <FindingAcceptanceTag acceptance={acceptance}/>
                    {
                        acceptance.statement &&
                        <Typography.Text italic>{acceptance.statement}</Typography.Text>
                    }
                </Space>
            }
        </Space>
    )
}

/**
 * The timeline of the observations of a finding, the most recent first: each scan which
 * reported it, with the severity it asserted, the versions, and the acceptance at the time.
 * One page at first, more on demand.
 */
export default function FindingObservationsTimeline({id}) {

    const [size, setSize] = useState(PAGE_SIZE)

    const {data, loading, finished, error} = useQuery(
        gqlFindingObservations,
        {
            variables: {id, size},
            deps: [id, size],
            condition: !!id,
            dataFn: data => data.finding?.observations,
        }
    )

    const observations = data?.pageItems ?? []
    const totalSize = data?.pageInfo?.totalSize ?? 0

    if (error) {
        return <Typography.Text type="danger">{error}</Typography.Text>
    }

    return (
        <Skeleton active loading={!data && (loading || !finished)}>
            {
                observations.length === 0 &&
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="No observation of this finding is kept"
                />
            }
            {
                observations.length > 0 &&
                <Space orientation="vertical" style={{width: '100%'}}>
                    <Timeline
                        data-testid="finding-observations"
                        items={observations.map(observation => ({
                            key: `${observation.validationRun.id}`,
                            color: severityColors[observation.severity] ?? 'gray',
                            content: <Observation observation={observation}/>,
                        }))}
                    />
                    {
                        observations.length < totalSize &&
                        <Button loading={loading} onClick={() => setSize(size + PAGE_SIZE)}>
                            More observations
                        </Button>
                    }
                    <Typography.Text type="secondary">
                        {observations.length} of {totalSize} observation{totalSize === 1 ? '' : 's'}
                    </Typography.Text>
                </Space>
            }
        </Skeleton>
    )
}
