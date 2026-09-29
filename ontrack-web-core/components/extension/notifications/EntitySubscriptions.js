import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Tag, Typography} from "antd";
import ItemList from "@components/common/ItemList";
import SubscriptionLink from "@components/extension/notifications/SubscriptionLink";
import SubscriptionsLink from "@components/extension/notifications/SubscriptionsLink";

const noSubscriptions = []
const noPageInfo = {}

/**
 * This component displays a list of the subscriptions attached to a given entity.
 *
 * Each subscription can be navigated to and a general link allows to go the list
 * of all subscriptions for this entity.
 *
 * @param type Project entity type
 * @param id Project entity ID
 */
export default function EntitySubscriptions({type, id}) {

    const {data} = useQuery(
        gql`
            query GetEntitySubscriptions($entity: ProjectEntityIDInput!) {
                eventSubscriptions(size: 10, filter: {
                    entity: $entity,
                }) {
                    pageInfo {
                        totalSize
                        nextPage {
                            offset
                        }
                    }
                    pageItems {
                        name
                        channel
                    }
                }
            }
        `,
        {
            variables: {entity: {type, id: Number(id)}},
            deps: [type, id],
            dataFn: data => data.eventSubscriptions,
        }
    )
    const subscriptions = data?.pageItems ?? noSubscriptions
    const pageInfo = data?.pageInfo ?? noPageInfo

    return (
        <>
            <Typography.Title type="secondary" level={5}>Subscriptions (<SubscriptionsLink entity={{type, id}}
                                                                                           text={pageInfo.totalSize}
            />)</Typography.Title>
            <ItemList size="small">
                {
                    subscriptions.map(subscription =>
                        <ItemList.Item
                            key={subscription.name}
                            title={<SubscriptionLink entity={{type, id}} subscription={subscription}/>}
                            avatar={<Tag>{subscription.channel}</Tag>}
                        />
                    )
                }
            </ItemList>
            {
                pageInfo.nextPage &&
                <div style={{paddingLeft: 16}}>
                    <SubscriptionsLink entity={{type, id}}
                                       text="More..."
                    />
                </div>
            }
        </>
    )
}