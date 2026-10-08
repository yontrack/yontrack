import {gql} from "graphql-request";
import {gqlSignatureActorFields} from "@components/common/actors/actors";

/**
 * The cross-project views of the agent activity (#2035): the *Agent activity* dashboard widget, and
 * the *Latest agent actions* page its tiles link to - for any user, restricted to the projects they
 * can see, under the Agent governance licence (`extension.agents`).
 */

/**
 * ID of the licensed feature gating the views.
 */
export const AGENT_ACTIONS_FEATURE = "extension.agents"

/**
 * The windows offered, in days.
 */
export const AGENT_ACTIONS_WINDOWS = [7, 30, 90]

/**
 * The window of the widget, by default, in days.
 */
export const AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW = 30

/**
 * The window of the page, by default, in days.
 */
export const AGENT_ACTIONS_DEFAULT_WINDOW = 7

/**
 * Size of a page of the actions.
 */
export const AGENT_ACTIONS_PAGE_SIZE = 20

/**
 * Path of the *Latest agent actions* page.
 */
export const AGENT_ACTIONS_PATH = "/extension/agents/actions"

/**
 * The event types counted by each tile of the widget, and filtering the page it links to.
 */
export const AGENT_ACTIVITY_EVENT_TYPES = {
    builds: ["new_build"],
    promotions: ["new_promotion_run"],
    deployments: ["slot-pipeline-creation", "slot-pipeline-deploying", "slot-pipeline-deployed"],
}

/**
 * A window, as one of the windows offered - the default one when it is not.
 *
 * @param value Number of days, as a number or a string (from the URL)
 * @param defaultWindow Window when the value is not one of the windows offered
 */
export const agentActionsWindow = (value, defaultWindow = AGENT_ACTIONS_DEFAULT_WINDOW) => {
    const days = Number(value)
    return AGENT_ACTIONS_WINDOWS.includes(days) ? days : defaultWindow
}

/**
 * The start of a window of days, as the `from` of the query.
 *
 * @param days Number of days of the window
 * @param now Current time, a `Date` - for the tests
 * @return ISO date-time, in UTC
 */
export const agentActionsFrom = (days, now = new Date()) =>
    new Date(now.getTime() - days * 24 * 60 * 60 * 1000).toISOString()

/**
 * Link to the *Latest agent actions* page, filtered.
 *
 * @param window Number of days of the window
 * @param eventTypes IDs of the event types, if any
 * @param project Name of the project, if any
 * @param agent Identifier of the agent, `<slug>[agent]`, if any
 */
export const agentActionsUri = ({window, eventTypes, project, agent} = {}) => {
    const query = new URLSearchParams()
    if (window) query.set("window", `${window}`)
    if (eventTypes?.length > 0) query.set("eventTypes", eventTypes.join(","))
    if (project) query.set("project", project)
    if (agent) query.set("agent", agent)
    const search = query.toString()
    return search ? `${AGENT_ACTIONS_PATH}?${search}` : AGENT_ACTIONS_PATH
}

/**
 * The filter of the page, read from the query of its URL - what `agentActionsUri` writes.
 *
 * @param query Query of the router, whose values are strings (or arrays of strings)
 * @return Values of the filter form: `days`, `eventTypes`, `project` and `agent`
 */
export const agentActionsFilterFromQuery = (query = {}) => {
    const single = (value) => Array.isArray(value) ? value[0] : value
    const filter = {
        days: agentActionsWindow(single(query.window)),
    }
    const eventTypes = single(query.eventTypes)?.split(",").map(it => it.trim()).filter(it => it.length > 0) ?? []
    if (eventTypes.length > 0) filter.eventTypes = eventTypes
    const project = single(query.project)
    if (project) filter.project = project
    const agent = single(query.agent)
    if (agent) filter.agent = agent
    return filter
}

/**
 * Value of the agent filter for all the agents - no filter.
 */
export const AGENT_ALL = "all"

/**
 * Turns the values of the filter form of the page into the variables of `gqlAgentActions`. An empty
 * value gives no variable; no window is the window by default.
 *
 * @param days Number of days of the window
 * @param eventTypes IDs of the event types
 * @param project Name of a project
 * @param agent Identifier of an agent, or `AGENT_ALL`
 * @param now Current time, a `Date` - for the tests
 */
export const agentActionsVariables = ({days, eventTypes, project, agent} = {}, now = new Date()) => {
    const variables = {
        from: agentActionsFrom(agentActionsWindow(days), now),
    }
    if (eventTypes?.length > 0) variables.eventTypes = eventTypes
    if (project) variables.project = project
    if (agent && agent !== AGENT_ALL) variables.agent = agent
    return variables
}

/**
 * Options of the agent filter: all the agents, then each agent the user can list by its identifier -
 * every agent for an administrator, their own agents for anybody else.
 *
 * @param agents Agents, with their `email` (their identifier) and `fullName`
 */
export const agentActionsAgentOptions = (agents = []) => [
    {value: AGENT_ALL, label: "All agents"},
    ...agents.map(agent => ({
        value: agent.email,
        label: `${agent.fullName} (${agent.email})`,
    })),
]

/**
 * The assisted share, as a percentage - `null` when no build is known.
 *
 * @param share Share between 0 and 1, or `null`
 * @return "42%", or `null`
 */
export const assistedSharePercent = (share) =>
    share === null || share === undefined ? null : `${Math.round(share * 100)}%`

/**
 * The tiles of the widget, from its counts, each linking to the page filtered on what it counts.
 *
 * @param stats `AgentActivityStats`
 * @param projects Projects the widget is narrowed to: the page is filtered on the project when there is
 * only one
 */
export const agentActivityTiles = (stats, projects = []) => {
    const project = projects?.length === 1 ? projects[0] : undefined
    const link = (eventTypes) => agentActionsUri({window: stats.window, eventTypes, project})
    return [
        {
            key: "builds",
            label: "Builds by agents",
            value: `${stats.builds}`,
            href: link(AGENT_ACTIVITY_EVENT_TYPES.builds),
        },
        {
            key: "promotions",
            label: "Promotions by agents",
            value: `${stats.promotions}`,
            href: link(AGENT_ACTIVITY_EVENT_TYPES.promotions),
        },
        {
            key: "deployments",
            label: "Deployments by agents",
            value: `${stats.deployments}`,
            href: link(AGENT_ACTIVITY_EVENT_TYPES.deployments),
        },
        {
            key: "assisted",
            label: "Assisted share",
            value: assistedSharePercent(stats.assistedShare) ?? "-",
            // A dash is not read out
            ariaValue: assistedSharePercent(stats.assistedShare) ?? "no known build",
            detail: `${stats.assistedBuilds} of ${stats.knownBuilds} builds`,
            extra: `${stats.unknownBuilds} unknown`,
            // The builds created by agents are the closest list of actions
            href: link(AGENT_ACTIVITY_EVENT_TYPES.builds),
        },
    ]
}

/**
 * Counts of the widget.
 */
export const gqlAgentActivityStats = gql`
    query AgentActivityStats(
        $window: Int,
        $projects: [String!],
        $labels: [String!],
    ) {
        agentActivityStats(
            window: $window,
            projects: $projects,
            labels: $labels,
        ) {
            window
            from
            builds
            promotions
            deployments
            assistedBuilds
            knownBuilds
            unknownBuilds
            assistedShare
        }
    }
`

/**
 * A page of the latest agent actions, newest first: for each event, its time, its actor, its message,
 * its project and its session.
 */
export const gqlAgentActions = gql`
    query AgentActions(
        $offset: Int!,
        $size: Int!,
        $agent: String,
        $from: LocalDateTime,
        $eventTypes: [String!],
        $project: String,
    ) {
        agentActions(
            offset: $offset,
            size: $size,
            agent: $agent,
            from: $from,
            eventTypes: $eventTypes,
            project: $project,
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
                user
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
`
