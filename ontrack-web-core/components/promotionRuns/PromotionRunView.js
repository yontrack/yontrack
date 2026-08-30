import {gql} from "graphql-request";
import Head from "next/head";
import {buildKnownName, pageTitle, promotionLevelTitleName} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToBuildBreadcrumbs} from "@components/common/Breadcrumbs";
import {Collapse, Skeleton, Space, Typography} from "antd";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import {CloseCommand} from "@components/common/Commands";
import {buildUri} from "@components/common/Links";
import {isAuthorized} from "@components/common/authorizations";
import NotificationRecordingsTable from "@components/extension/notifications/NotificationRecordingsTable";
import {useQuery} from "@components/services/GraphQL";
import PromotionRunDeleteCommand from "@components/promotionRuns/PromotionRunDeleteCommand";
import AutoVersioningPromotionRunTrail from "@components/extension/auto-versioning/AutoVersioningPromotionRunTrail";
import PromotionRunSummary from "@components/promotionRuns/PromotionRunSummary";
import PromotionRunWorkflows from "@components/promotionRuns/PromotionRunWorkflows";
import {
    AutoVersioningTrailPanelLabel,
    NotificationsPanelLabel
} from "@components/promotionRuns/PromotionRunPanelLabels";

export default function PromotionRunView({id}) {

    const {data: run, loading} = useQuery(
        gql`
            query GetPromotionRun($id: Int!) {
                promotionRuns(id: $id) {
                    id
                    description
                    annotatedDescription
                    creation {
                        user
                        time
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    build {
                        id
                        name
                        releaseProperty {
                            value
                        }
                        branch {
                            id
                            name
                            displayName
                            project {
                                id
                                name
                            }
                        }
                    }
                    promotionLevel {
                        id
                        name
                        image
                        fields {
                            name
                            displayName
                            type
                        }
                        branch {
                            id
                            name
                            project {
                                id
                                name
                            }
                        }
                    }
                    fieldValues {
                        name
                        value
                    }
                }
            }
        `,
        {
            variables: {
                id: Number(id),
            },
            deps: [id],
            initialData: null,
            dataFn: data => data.promotionRuns[0],
        }
    )

    const commands = run ? [
        ...(isAuthorized(run, 'promotion_run', 'delete') ?
            [<PromotionRunDeleteCommand key="delete" run={run}/>] : []),
        <CloseCommand key="close" href={buildUri(run.build)}/>,
    ] : []

    // The two tables are secondary, on-demand content: they sit in panels collapsed by default,
    // below the workflows. antd's Collapse is lazy, so neither table is queried until expanded.
    const secondaryPanels = run ? [
        {
            key: 'auto-versioning-trail',
            label: <AutoVersioningTrailPanelLabel promotionRunId={run.id}/>,
            children: <AutoVersioningPromotionRunTrail promotionRunId={run.id}/>,
        },
        {
            key: 'notifications',
            label: <NotificationsPanelLabel promotionRunId={run.id}/>,
            children: <div id="promotion-run-notifications" data-testid="promotion-run-notifications">
                <Typography.Paragraph type="secondary" style={{padding: 8}}>
                    List of notifications sent for this promotion.
                </Typography.Paragraph>
                <NotificationRecordingsTable
                    entity={{
                        type: 'PROMOTION_RUN',
                        id: run.id,
                    }}
                    sourceId="entity-subscription"
                />
            </div>,
        },
    ] : []

    return (
        <>
            <Head>
                {
                    run?.promotionLevel &&
                    pageTitle(`${promotionLevelTitleName(run?.promotionLevel)} --> ${buildKnownName(run?.build)}`)
                }
            </Head>
            <MainPage
                title={
                    run?.promotionLevel && <>
                        <Space>
                            <Typography.Text>Promotion to</Typography.Text>
                            <PromotionLevelLink promotionLevel={run?.promotionLevel}/>
                        </Space>
                    </>
                }
                commands={commands}
                breadcrumbs={run ? downToBuildBreadcrumbs(run) : []}
            >
                <Skeleton loading={loading} active>
                    {
                        run &&
                        <Space direction="vertical" className="ot-line">
                            <PromotionRunSummary run={run}/>
                            <PromotionRunWorkflows promotionRunId={run.id}/>
                            <Collapse
                                className="ot-line"
                                data-testid="promotion-run-secondary"
                                items={secondaryPanels}
                            />
                        </Space>
                    }
                </Skeleton>
            </MainPage>
        </>
    )
}
