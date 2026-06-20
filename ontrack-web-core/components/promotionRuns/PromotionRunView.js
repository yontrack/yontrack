import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import Head from "next/head";
import {buildKnownName, pageTitle, promotionLevelTitleName} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToBuildBreadcrumbs} from "@components/common/Breadcrumbs";
import {Card, Skeleton, Space, Typography} from "antd";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import {CloseCommand} from "@components/common/Commands";
import {buildUri} from "@components/common/Links";
import BuildLink from "@components/builds/BuildLink";
import TimestampText from "@components/common/TimestampText";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import PageSection from "@components/common/PageSection";
import {isAuthorized} from "@components/common/authorizations";
import NotificationRecordingsTable from "@components/extension/notifications/NotificationRecordingsTable";
import {useQuery} from "@components/services/GraphQL";
import PromotionRunDeleteCommand from "@components/promotionRuns/PromotionRunDeleteCommand";
import AutoVersioningPromotionRunTrail from "@components/extension/auto-versioning/AutoVersioningPromotionRunTrail";
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";

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

    const [commands, setCommands] = useState([])

    useEffect(() => {
        if (run) {
            const commands = []
            if (isAuthorized(run, 'promotion_run', 'delete')) {
                commands.push(<PromotionRunDeleteCommand key="delete" run={run}/>)
            }
            commands.push(
                <CloseCommand key="close" href={buildUri(run.build)}/>
            )
            setCommands(commands)
        }
    }, [run])

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
                            <Card>
                                <Space direction="vertical">
                                    <Typography.Paragraph>
                                        Build <BuildLink build={run.build}/> has been promoted to <PromotionLevelLink
                                        promotionLevel={run.promotionLevel}/>.
                                    </Typography.Paragraph>
                                    {
                                        run.description &&
                                        <Typography.Paragraph>
                                            <AnnotatedDescription entity={run}/>
                                        </Typography.Paragraph>
                                    }
                                    {
                                        run.creation &&
                                        <Typography.Paragraph>
                                            <Space>
                                                <Typography.Text type="secondary">Timestamp:</Typography.Text>
                                                <TimestampText value={run.creation.time}/>
                                                <Typography.Text disabled>
                                                    ({run.creation.user})
                                                </Typography.Text>
                                            </Space>
                                        </Typography.Paragraph>
                                    }
                                    {
                                        run.fieldValues?.length > 0 &&
                                        <PromotionRunFieldValues
                                            fields={run.promotionLevel.fields}
                                            fieldValues={run.fieldValues}
                                        />
                                    }
                                </Space>
                            </Card>
                            {
                                run &&
                                <AutoVersioningPromotionRunTrail promotionRunId={run.id}/>
                            }
                            <PageSection
                                id="promotion-run-notifications"
                                title="Notifications"
                            >
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
                            </PageSection>
                        </Space>
                    }
                </Skeleton>
            </MainPage>
        </>
    )
}
