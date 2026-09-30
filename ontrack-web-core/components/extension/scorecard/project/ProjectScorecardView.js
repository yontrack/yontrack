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
import {
    orderedSets,
    resolveSet,
    SCORECARD_HISTORY_DAYS,
    scorecardRows,
    setParam,
    setTitle,
    sortedReadings,
} from "@components/extension/scorecard/scorecardModel";
import SetExplanation from "@components/extension/scorecard/SetExplanation";
import {ScorecardRecomputeButton, useScorecardRecompute} from "@components/extension/scorecard/ScorecardRecompute";
import SetCard from "@components/extension/scorecard/SetCard";
import ReadingTile from "@components/extension/scorecard/ReadingTile";

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
 * The scorecard page of a project: the cards of its sets, as on the Scorecard section of the project
 * page, and the readings of the selected set, each with the trend of its daily snapshots, the window,
 * the branches which fed it, the marker it was read up to and its details. It answers "why is this
 * number what it is".
 *
 * @param id ID of the project
 * @param set Set to show, as in the `set` parameter of the URL: `project` or the name of an estate;
 *   the default set when not given or not one of the project
 * @param onSetChange Called with the parameter of the set a card selects
 */
export default function ProjectScorecardView({id, set: setParameter, onSetChange}) {

    const [refreshCount, refresh] = useRefresh()

    const {data: project, finished, error} = useQuery(
        gqlProjectScorecard,
        {
            variables: {id, days: SCORECARD_HISTORY_DAYS},
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

    const sets = orderedSets(scorecard)
    const computed = scorecardRows(scorecard).length > 0
    const {set: shown} = resolveSet(sets, setParameter)
    const readings = sortedReadings(shown)

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
                        computed &&
                        <Row gutter={[16, 16]} data-testid="scorecard-sets">
                            {
                                sets.map(set =>
                                    <Col key={set.name} xs={24} md={12} lg={8}>
                                        <SetCard
                                            set={set}
                                            selected={set === shown}
                                            onSelect={() => onSetChange && onSetChange(setParam(set))}
                                            testId={`scorecard-set-card-${set.name}`}
                                        />
                                    </Col>
                                )
                            }
                        </Row>
                    }
                    {
                        computed && shown &&
                        <PageSection
                            id={`scorecard-set-${shown.name}`}
                            title={setTitle(shown)}
                            padding={true}
                        >
                            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                                <SetExplanation set={shown} testId={`scorecard-set-explanation-${shown.name}`}/>
                                {
                                    readings.length === 0 ?
                                        <Typography.Text>
                                            The readings of this set have not been computed yet.
                                        </Typography.Text> :
                                        <Row gutter={[20, 20]}>
                                            {
                                                readings.map(reading =>
                                                    <Col key={reading.key} xs={24} lg={12}>
                                                        <ReadingTile
                                                            size="large"
                                                            reading={reading}
                                                            testId={`reading-${shown.name}-${reading.key}`}
                                                        />
                                                    </Col>
                                                )
                                            }
                                        </Row>
                                }
                            </Space>
                        </PageSection>
                    }
                </Space>
            </MainPage>
        </>
    )
}
