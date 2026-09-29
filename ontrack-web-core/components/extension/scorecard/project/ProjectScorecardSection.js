import {gql} from "graphql-request";
import Link from "next/link";
import {Empty, Space, Table, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import TimestampText from "@components/common/TimestampText";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {projectScorecardUri} from "@components/common/Links";
import {isAuthorized} from "@components/common/authorizations";
import {
    latestComputedAt,
    readingDescriptions,
    readingMarkerKinds,
    readingName,
    scorecardRows,
    setTitle,
} from "@components/extension/scorecard/scorecardModel";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import ScorecardInfo from "@components/extension/scorecard/ScorecardInfo";
import SetExplanation from "@components/extension/scorecard/SetExplanation";
import ReadingValue from "@components/extension/scorecard/ReadingValue";
import {ScorecardRecomputeButton, useScorecardRecompute} from "@components/extension/scorecard/ScorecardRecompute";

export const gqlProjectScorecardSummary = gql`
    query ProjectScorecardSummary($id: Int!) {
        project(id: $id) {
            scorecard {
                sets {
                    name
                    estate {
                        name
                        description
                        labels {
                            ...labelFragment
                        }
                        marker {
                            kind
                            levelName
                            environment
                            qualifier
                        }
                    }
                    readings {
                        key
                        day
                        computedAt
                        windowStart
                        windowEnd
                        value
                        basis
                        unknownReason
                        details
                        direction
                        target
                        targetMet
                    }
                }
            }
        }
    }
    ${gqlLabelFragment}
`

/**
 * The Scorecard section of the project page: a compact table of the readings of the project, one
 * column per set — "Project", with no estate, then "Estate: <name>" per estate the project is in. Each
 * cell gives the value against the target of the estate, an unknown reading distinctly with its
 * reason, and the number of samples. The "Recompute" command needs the right to configure the project.
 *
 * An ⓘ on each column says what the set is, and one on each reading what it measures — up to each
 * kind of marker the sets read it up to. A legend says what the estate columns are.
 *
 * The scorecard page of the project says why each number is what it is.
 */
export default function ProjectScorecardSection({project}) {

    const [refreshCount, refresh] = useRefresh()

    const {data: scorecard, finished, error} = useQuery(
        gqlProjectScorecardSummary,
        {
            variables: {id: Number(project.id)},
            deps: [project.id, refreshCount],
            condition: !!project.id,
            dataFn: data => data.project?.scorecard,
        }
    )

    const canRecompute = isAuthorized(project, 'project', 'config')
    const {recompute, polling, error: recomputeError} = useScorecardRecompute({
        projectId: project.id,
        scorecard,
        refresh,
    })

    const sets = scorecard?.sets ?? []
    const rows = scorecardRows(scorecard)
    const latest = latestComputedAt(scorecard)

    const hasEstates = sets.some(set => set.estate)

    const columns = [
        {
            key: 'reading',
            title: 'Reading',
            render: (_, {key, readings}) => {
                const name = readingName(key)
                const descriptions = readingDescriptions(key, readingMarkerKinds(Object.values(readings)))
                return (
                    <Space size={0}>
                        <Typography.Text>{name}</Typography.Text>
                        {
                            descriptions.length > 0 &&
                            <ScorecardInfo
                                label={`About ${name}`}
                                title={name}
                                testId={`scorecard-reading-info-${key}`}
                                content={
                                    <Space orientation="vertical" size={4}>
                                        {
                                            descriptions.map(({label, text}) =>
                                                <Typography.Text key={label ?? 'default'}>
                                                    {label && <Typography.Text strong>{label}: </Typography.Text>}
                                                    {text}
                                                </Typography.Text>
                                            )
                                        }
                                    </Space>
                                }
                            />
                        }
                    </Space>
                )
            },
        },
        ...sets.map(set => ({
            key: set.name,
            title: <Space size={0}>
                {setTitle(set)}
                <ScorecardInfo
                    label={`About the ${setTitle(set)} column`}
                    title={setTitle(set)}
                    testId={`scorecard-set-info-${set.name}`}
                    content={<SetExplanation set={set}/>}
                />
            </Space>,
            render: (_, {readings}) => {
                const reading = readings[set.name]
                return reading ?
                    <ReadingValue reading={reading} testId={`scorecard-${set.name}-${reading.key}`}/> :
                    <Typography.Text type="secondary">-</Typography.Text>
            },
        })),
    ]

    return (
        <PageSection
            id="project-scorecard"
            loading={!finished}
            title="Scorecard"
            extra={
                <Space size={8}>
                    {
                        canRecompute &&
                        <ScorecardRecomputeButton recompute={recompute} polling={polling} size="small"/>
                    }
                    <Link href={projectScorecardUri(project)}>Details</Link>
                </Space>
            }
            padding={true}
        >
            <Space orientation="vertical" size={8} style={{width: '100%'}}>
                {
                    error &&
                    <Typography.Text type="danger">{error}</Typography.Text>
                }
                {
                    recomputeError &&
                    <Typography.Text type="danger">{recomputeError}</Typography.Text>
                }
                {
                    !error && rows.length === 0 &&
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="The readings of this project have not been computed yet"
                    />
                }
                {
                    rows.length > 0 &&
                    <>
                        <Table
                            data-testid="scorecard-table"
                            size="small"
                            rowKey="key"
                            columns={columns}
                            dataSource={rows}
                            pagination={false}
                        />
                        {
                            hasEstates &&
                            <Typography.Text type="secondary" style={{fontSize: '85%'}} data-testid="scorecard-legend">
                                One column per estate the project belongs to through its labels.
                                Met / Missed compares against that estate&apos;s target.
                            </Typography.Text>
                        }
                        {
                            latest &&
                            <Typography.Text type="secondary" style={{fontSize: '85%'}}>
                                Computed <TimestampText value={latest} relative={true}/>
                            </Typography.Text>
                        }
                    </>
                }
            </Space>
        </PageSection>
    )
}
