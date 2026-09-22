import {gql} from "graphql-request";
import {Space} from "antd";
import LoadingInline from "@components/common/LoadingInline";
import {useQuery} from "@components/services/GraphQL";
import {promotionRunUri} from "@components/common/Links";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import EntityNotificationsBadge from "@components/extension/notifications/EntityNotificationsBadge";
import React from "react";
import PromotionRunLink from "@components/promotionRuns/PromotionRunLink";

export default function OntrackValidationNotificationChannelOutput({runId}) {

    const {data: run, loading} = useQuery(
        gql`
            query PromotionTun($runId: Int!) {
                promotionRuns(id: $runId) {
                    id
                    promotionLevel {
                        id
                        name
                        image
                    }
                }
            }
        `,
        {
            variables: {runId},
            dataFn: data => data.promotionRuns[0],
        }
    )

    return (
        <>
            <Space orientation="vertical">
                Promotion created.
                <LoadingInline loading={loading}>
                    {
                        run &&
                        <EntityNotificationsBadge
                            entityType="PROMOTION_RUN"
                            entityId={run.id}
                            href={promotionRunUri(run)}
                        >
                            <PromotionRunLink
                                promotionRun={run}
                                text={
                                    <Space>
                                        <PromotionLevelImage promotionLevel={run.promotionLevel}/>
                                        {run.promotionLevel.name}
                                    </Space>
                                }
                            />
                        </EntityNotificationsBadge>
                    }
                </LoadingInline>
            </Space>
        </>
    )
}
