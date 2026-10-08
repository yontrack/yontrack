import {gql} from "graphql-request";
import {gqlSignatureActorFields} from "@components/common/actors/actors";

/**
 * The *Activity* of an agent (#2034): what the agent did over a window of days, read from the event
 * log through `Account.agentActivity` - for its owner and the administrators, under the Agent
 * governance licence (`extension.agents`).
 */

/**
 * The windows offered, in days.
 */
export const AGENT_ACTIVITY_WINDOWS = [7, 30, 90]

/**
 * The window shown first, in days.
 */
export const AGENT_ACTIVITY_DEFAULT_WINDOW = 7

/**
 * Size of a page of the activity.
 */
export const AGENT_ACTIVITY_PAGE_SIZE = 20

/**
 * ID of the licensed feature gating the activity.
 */
export const AGENT_ACTIVITY_FEATURE = "extension.agents"

/**
 * The start of a window of days, as the `from` of the query.
 *
 * @param days Number of days of the window
 * @param now Current time, a `Date` - for the tests
 * @return ISO date-time, in UTC
 */
export const agentActivityFrom = (days, now = new Date()) =>
    new Date(now.getTime() - days * 24 * 60 * 60 * 1000).toISOString()

/**
 * A filter of the activity: a window starting when it is chosen, with no event type nor project.
 *
 * @param days Number of days of the window
 * @param now Current time, a `Date` - for the tests
 */
export const agentActivityFilter = (days = AGENT_ACTIVITY_DEFAULT_WINDOW, now = new Date()) => ({
    days,
    from: agentActivityFrom(days, now),
})

/**
 * The variables of `gqlAgentActivity`. An empty list of event types or no project gives no variable.
 *
 * @param id ID of the agent
 * @param filter Filter of the activity: `days` and `from` (`agentActivityFilter`), `eventTypes` and
 * `project`
 * @param offset Offset of the page
 * @param size Size of the page
 */
export const agentActivityVariables = ({id, filter, offset = 0, size = AGENT_ACTIVITY_PAGE_SIZE}) => {
    const variables = {
        id: Number(id),
        from: filter.from,
        offset,
        size,
    }
    if (filter.eventTypes?.length > 0) variables.eventTypes = filter.eventTypes
    if (filter.project) variables.project = filter.project
    return variables
}

/**
 * A page of the activity of an agent, newest first: for each event, its time, its message, its
 * project and the agent session behind it.
 */
export const gqlAgentActivity = gql`
    query AgentActivity(
        $id: Int!,
        $from: LocalDateTime,
        $eventTypes: [String!],
        $project: String,
        $offset: Int!,
        $size: Int!,
    ) {
        agents(id: $id) {
            id
            agentActivity(
                from: $from,
                eventTypes: $eventTypes,
                project: $project,
                offset: $offset,
                size: $size,
            ) {
                pageInfo {
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    id
                    time
                    message
                    eventType {
                        id
                        description
                    }
                    project {
                        id
                        name
                    }
                    ${gqlSignatureActorFields}
                }
            }
        }
    }
`
