import {gql} from "graphql-request";
import {Space, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";

/**
 * Label of a collapsed panel, with the number of items it holds.
 *
 * The count is what makes a collapsed panel worth expanding, so it is fetched even while the panel
 * itself stays closed. A `null` count means "not known" — either the first fetch has not resolved
 * yet, or the user is not allowed to see the underlying list — and renders as no count at all
 * rather than as a misleading zero.
 */
export function PromotionRunPanelLabel({label, count}) {
    return (
        <Space size="small">
            <Typography.Text>{label}</Typography.Text>
            {
                count !== null && count !== undefined &&
                <Typography.Text type="secondary" data-testid={`panel-count-${label}`}>
                    {`(${count})`}
                </Typography.Text>
            }
        </Space>
    )
}

export function AutoVersioningTrailPanelLabel({promotionRunId}) {
    const {data: count} = useQuery(
        gql`
            query PromotionRunTrailCount($id: Int!) {
                promotionRuns(id: $id) {
                    autoVersioningTrailPaginated(filter: {onlyEligible: true}, size: 1) {
                        pageInfo {
                            totalSize
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(promotionRunId)},
            deps: [promotionRunId],
            initialData: null,
            dataFn: data => data.promotionRuns[0]?.autoVersioningTrailPaginated?.pageInfo?.totalSize ?? null,
        }
    )
    return <PromotionRunPanelLabel label="Auto-versioning trail" count={count}/>
}

export function NotificationsPanelLabel({promotionRunId}) {
    // Reading notification records needs the NotificationRecordingAccess global function, so this
    // query is kept on its own: for a user without it, the error stops at this label instead of
    // blanking the page.
    const {data: count} = useQuery(
        gql`
            query PromotionRunNotificationsCount($id: Int!) {
                notificationRecords(
                    eventEntityType: PROMOTION_RUN,
                    eventEntityId: $id,
                    sourceId: "entity-subscription",
                    size: 1,
                ) {
                    pageInfo {
                        totalSize
                    }
                }
            }
        `,
        {
            variables: {id: Number(promotionRunId)},
            deps: [promotionRunId],
            initialData: null,
            dataFn: data => data.notificationRecords?.pageInfo?.totalSize ?? null,
        }
    )
    return <PromotionRunPanelLabel label="Notifications" count={count}/>
}
