import Head from "next/head";
import {gql} from "graphql-request";
import {Alert, Col, Empty, Row, Skeleton, Space, Typography} from "antd";
import MainPage from "@components/layouts/MainPage";
import PageSection from "@components/common/PageSection";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import {projectTitle} from "@components/common/Titles";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {isAuthorized} from "@components/common/authorizations";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {scorecardRows, setTitle} from "@components/extension/scorecard/scorecardModel";
import SetExplanation from "@components/extension/scorecard/SetExplanation";
import {ScorecardRecomputeButton, useScorecardRecompute} from "@components/extension/scorecard/ScorecardRecompute";
import ReadingCard from "@components/extension/scorecard/project/ReadingCard";

const HISTORY_DAYS = 90

// `labelFragment`, used by the labels of the estates, comes with `ProjectContent`
export const gqlProjectScorecard = gql`
    query ProjectScorecard($id: Int!, $days: Int!) {
        project(id: $id) {
            ...ProjectContent
            authorizations {
                name
                action
                authorized
            }
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
    ${gqlProjectContentFragment}
`

/**
 * The scorecard page of a project: per set, each reading with the trend of its daily snapshots,
 * the window, the branches which fed it, the marker it was read up to and its details. It answers
 * "why is this number what it is".
 */
export default function ProjectScorecardView({id}) {

    const [refreshCount, refresh] = useRefresh()

    const {data: project, finished, error} = useQuery(
        gqlProjectScorecard,
        {
            variables: {id, days: HISTORY_DAYS},
            deps: [id, refreshCount],
            condition: !!id,
            dataFn: data => data.project,
        }
    )

    const scorecard = project?.scorecard
    const {recompute, polling, error: recomputeError} = useScorecardRecompute({
        projectId: id,
        scorecard,
        refresh,
    })

    const sets = scorecard?.sets ?? []
    const computed = scorecardRows(scorecard).length > 0

    const commands = []
    if (project && isAuthorized(project, 'project', 'config')) {
        commands.push(
            <ScorecardRecomputeButton key="recompute" recompute={recompute} polling={polling}/>
        )
    }
    commands.push(
        <CloseCommand key="close" href={projectUri({id})}/>
    )

    return (
        <>
            <Head>
                {project && projectTitle(project, "Scorecard")}
            </Head>
            <MainPage
                title="Scorecard"
                breadcrumbs={project ? downToProjectBreadcrumbs({project}) : []}
                commands={commands}
            >
                <Space orientation="vertical" size={16} className="ot-line">
                    {
                        !finished &&
                        <Skeleton active/>
                    }
                    {
                        error &&
                        <Alert type="error" showIcon title={error}/>
                    }
                    {
                        recomputeError &&
                        <Alert type="error" showIcon title={recomputeError}/>
                    }
                    {
                        finished && !error && !computed &&
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="The readings of this project have not been computed yet"
                        />
                    }
                    {
                        computed && sets.map(set =>
                            <PageSection
                                key={set.name}
                                id={`scorecard-set-${set.name}`}
                                title={setTitle(set)}
                                padding={true}
                            >
                                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                                    <SetExplanation set={set} testId={`scorecard-set-explanation-${set.name}`}/>
                                    {
                                        set.readings.length === 0 ?
                                            <Typography.Text type="secondary">
                                                The readings of this set have not been computed yet.
                                            </Typography.Text> :
                                            <Row gutter={[16, 16]}>
                                                {
                                                    set.readings.map(reading =>
                                                        <Col key={reading.key} xs={24} lg={12} xxl={8}>
                                                            <ReadingCard
                                                                reading={reading}
                                                                testId={`reading-${set.name}-${reading.key}`}
                                                            />
                                                        </Col>
                                                    )
                                                }
                                            </Row>
                                    }
                                </Space>
                            </PageSection>
                        )
                    }
                </Space>
            </MainPage>
        </>
    )
}
