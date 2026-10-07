import {graphQLCall} from "@ontrack/graphql";
import {gql} from "graphql-request";

/**
 * Events of the instance, newest first, through the admin-only `events` query.
 *
 * @param filter `EventFilterInput` - from, to, user, eventTypes, project
 * @param size Maximum number of events to return
 */
export const findEvents = async (ontrack, filter = {}, size = 20) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query FindEvents($filter: EventFilterInput, $size: Int!) {
                events(filter: $filter, size: $size) {
                    pageItems {
                        id
                        eventType {
                            id
                        }
                        time
                        user
                        values {
                            name
                            value
                        }
                    }
                }
            }
        `,
        {filter, size}
    )
    return data.events.pageItems
}
