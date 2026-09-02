import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import LoadingInline from "@components/common/LoadingInline";
import {gql} from "graphql-request";
import {Badge} from "antd";
import NotificationBadgeCluster from "@components/primitives/NotificationBadgeCluster";
import {bucketNotificationTypes} from "@components/extension/notifications/notificationBuckets";

/**
 * Fetches an entity's notification records and hands the counts to the badges.
 *
 * This component owns the QUERY only. How the counts look is
 * `NotificationBadgeCluster`'s job, and how one count looks is `StatePill`'s, so
 * the badges read identically wherever they are used.
 *
 * Two presentations, picked by whether `children` is given:
 *   - with children, a single antd `Badge` corner count decorating them - this
 *     is the promotion medal in the builds table, where three pills would not fit;
 *   - without, the full cluster - one pill per bucket.
 *
 * They COUNT NOTIFICATION RECORDS, not workflows; see
 * `docs/adr/0002-promotion-run-badges-count-notifications.md`.
 */
export default function EntityNotificationsBadge({entityType, entityId, href, showText = false, children}) {

    const client = useGraphQLClient()
    const [loading, setLoading] = useState(true)
    const [counts, setCounts] = useState({success: 0, running: 0, error: 0})

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query EntityNotificationsStatuses(
                        $entityType: ProjectEntityType!,
                        $entityId: Int!,
                    ) {
                        notificationRecords(
                            eventEntityType: $entityType,
                            eventEntityId: $entityId,
                            sourceId: "entity-subscription",
                        ) {
                            pageItems {
                                result {
                                    type
                                }
                            }
                        }
                    }
                `,
                {entityType, entityId: Number(entityId)}
            ).then(data => {
                const types = data.notificationRecords?.pageItems?.map(record => record.result.type) ?? []
                setCounts(bucketNotificationTypes(types))
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, entityType, entityId])

    // The corner count shows the most severe non-empty bucket: on a 22px medal
    // there is only room for one number, and "something failed" is the one worth
    // the space.
    const worstBucket =
        counts.error ? {count: counts.error, colour: "red"} :
            counts.running ? {count: counts.running, colour: "blue"} :
                counts.success ? {count: counts.success, colour: "green"} :
                    {count: 0, colour: ""}

    return (
        <>
            {
                children &&
                <Badge
                    overflowCount={10}
                    showZero={false}
                    count={worstBucket.count}
                    title=""
                    color={worstBucket.colour}
                    size="small"
                >
                    {children}
                </Badge>
            }
            {
                !children &&
                <LoadingInline
                    loading={loading}
                    text=""
                >
                    <NotificationBadgeCluster
                        counts={counts}
                        href={href}
                        showText={showText}
                    />
                </LoadingInline>
            }
        </>
    )
}
