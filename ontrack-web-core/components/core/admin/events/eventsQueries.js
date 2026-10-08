import {gql} from "graphql-request";
import {gqlSignatureActorFields} from "@components/common/actors/actors";

/**
 * Page of the events matching the filter of the events page, with the actor of each event - the
 * agent behind it, `null` for a person (#2031).
 */
export const gqlEvents = gql`
    query Events(
        $offset: Int!,
        $size: Int!,
        $from: LocalDateTime,
        $to: LocalDateTime,
        $user: String,
        $eventTypes: [String!],
        $project: String,
        $actor: String,
    ) {
        events(
            offset: $offset,
            size: $size,
            filter: {
                from: $from,
                to: $to,
                user: $user,
                eventTypes: $eventTypes,
                project: $project,
                actor: $actor,
            },
        ) {
            pageInfo {
                nextPage {
                    offset
                    size
                }
            }
            pageItems {
                id
                eventType {
                    id
                    description
                }
                time
                user
                ${gqlSignatureActorFields}
                message
                project {
                    id
                    name
                }
                entities {
                    type
                    id
                    displayName
                }
                extraEntities {
                    type
                    id
                    displayName
                }
                ref
                values {
                    name
                    value
                }
            }
        }
    }
`

/**
 * What an export of the events matching the filter of the events page would hold.
 */
export const gqlEventsExport = gql`
    query EventsExport(
        $from: LocalDateTime,
        $to: LocalDateTime,
        $user: String,
        $eventTypes: [String!],
        $project: String,
        $actor: String,
    ) {
        eventsExport(
            filter: {
                from: $from,
                to: $to,
                user: $user,
                eventTypes: $eventTypes,
                project: $project,
                actor: $actor,
            },
        ) {
            maxRows
            truncated
        }
    }
`

/**
 * The registered agents, offered by the actor filter of the events page.
 */
export const gqlEventsAgents = gql`
    query EventsAgents {
        agents {
            id
            email
            fullName
        }
    }
`
