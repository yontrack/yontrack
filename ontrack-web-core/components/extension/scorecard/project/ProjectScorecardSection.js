import {useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Col, Empty, Row, Space, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {projectScorecardUri} from "@components/common/Links";
import {isAuthorized} from "@components/common/authorizations";
import {
    latestComputedAt,
    orderedSets,
    resolveSet,
    SCORECARD_HISTORY_DAYS,
    scorecardRows,
    setParam,
    sortedReadings,
} from "@components/extension/scorecard/scorecardModel";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import SetCard from "@components/extension/scorecard/SetCard";
import ReadingTile from "@components/extension/scorecard/ReadingTile";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import ScorecardLegend from "@components/extension/scorecard/ScorecardLegend";
import {ScorecardRecomputeButton, useScorecardRecompute} from "@components/extension/scorecard/ScorecardRecompute";

export const gqlProjectScorecardSummary = gql`
    query ProjectScorecardSummary($id: Int!, $days: Int!) {
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
                        history(days: $days) {
                            day
                            value
                        }
                    }
                }
            }
        }
    }
    ${gqlLabelFragment}
`

/**
 * The Scorecard section of the project page: one card per set - "Project", with no estate, then
 * one per estate the project is in, by name - each estate with the ring of its targets met, and
 * under them the readings of the selected set as tiles, with the trend of their last 90 days.
 *
 * The first estate is selected by default, the Project set for a project in no estate. The selection
 * is not kept: the "Details" link opens the scorecard page on it. The "Recompute" command needs the
 * right to configure the project.
 */
export default function ProjectScorecardSection({project}) {

    const [refreshCount, refresh] = useRefresh()
    const [selected, setSelected] = useState(null)

    const {data: scorecard, finished, error} = useQuery(
        gqlProjectScorecardSummary,
        {
            variables: {id: Number(project.id), days: SCORECARD_HISTORY_DAYS},
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

    const sets = orderedSets(scorecard)
    const computed = scorecardRows(scorecard).length > 0
    const latest = latestComputedAt(scorecard)
    const {set: current} = resolveSet(sets, selected)
    const readings = sortedReadings(current)

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
                    <Link href={projectScorecardUri(project, current ? setParam(current) : undefined)}>Details</Link>
                </Space>
            }
            padding={true}
        >
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                {
                    error &&
                    <Typography.Text type="danger">{error}</Typography.Text>
                }
                {
                    recomputeError &&
                    <Typography.Text type="danger">{recomputeError}</Typography.Text>
                }
                {
                    !error && !computed &&
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="The readings of this project have not been computed yet"
                    />
                }
                {
                    computed &&
                    <>
                        <Row gutter={[16, 16]} data-testid="scorecard-sets">
                            {
                                sets.map(set =>
                                    <Col key={set.name} xs={24} md={12} lg={8}>
                                        <SetCard
                                            set={set}
                                            selected={set === current}
                                            onSelect={it => setSelected(setParam(it))}
                                            testId={`scorecard-set-card-${set.name}`}
                                        />
                                    </Col>
                                )
                            }
                        </Row>
                        <Row gutter={[16, 16]} data-testid="scorecard-tiles">
                            {
                                readings.map(reading =>
                                    <Col key={reading.key} xs={24} md={12} lg={8}>
                                        <ReadingTile
                                            reading={reading}
                                            testId={`scorecard-${current.name}-${reading.key}`}
                                        />
                                    </Col>
                                )
                            }
                        </Row>
                        {
                            readings.length === 0 &&
                            <SecondaryText>
                                The readings of this set have not been computed yet.
                            </SecondaryText>
                        }
                        <ScorecardLegend latest={latest}/>
                    </>
                }
            </Space>
        </PageSection>
    )
}
