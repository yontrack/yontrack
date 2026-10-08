import {Alert, Form, Segmented, Select, Skeleton, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";
import StandardTable from "@components/common/table/StandardTable";
import TimestampText from "@components/common/TimestampText";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import ActorBadge from "@components/common/actors/ActorBadge";
import ProjectLink from "@components/projects/ProjectLink";
import SelectProject from "@components/projects/SelectProject";
import SelectMultipleEvents from "@components/core/model/SelectMultipleEvents";
import {gqlEventsAgents} from "@components/core/admin/events/eventsQueries";
import {
    AGENT_ACTIONS_FEATURE,
    AGENT_ACTIONS_PAGE_SIZE,
    AGENT_ACTIONS_WINDOWS,
    AGENT_ALL,
    agentActionsAgentOptions,
    agentActionsVariables,
    gqlAgentActions,
} from "@components/extension/agents/agentActionsModel";

/**
 * *Latest agent actions* (#2035): what the agents did, newest first, on the projects the user can see,
 * filtered on a window, an agent, event types and a project.
 *
 * Under the Agent governance licence: without it, the page says so, and never shows an empty list.
 *
 * @param initialFilter Filter to start from, read from the URL - `days`, `eventTypes`, `project` and
 * `agent`
 */
export default function AgentActionsView({initialFilter}) {
    const {enabled} = useLicensedFeature(AGENT_ACTIONS_FEATURE)
    if (enabled === undefined) {
        return <Skeleton active/>
    } else if (!enabled) {
        return <Alert
            type="warning"
            showIcon
            title="Requires the Agent governance licence"
            description="The activity of the agents is part of the Agent governance. What the agents do is still recorded, and signed with their name."
            data-testid="agent-actions-licence"
        />
    } else {
        return <AgentActionsTable initialFilter={initialFilter}/>
    }
}

/**
 * The agents the user can list: every agent for an administrator, their own agents for anybody else.
 */
function SelectAgent({id, value, onChange}) {
    const {data: agents, loading} = useQuery(gqlEventsAgents, {
        initialData: [],
        dataFn: data => data.agents,
    })
    return (
        <Select
            id={id}
            data-testid={id}
            aria-label="Agent"
            value={value ?? AGENT_ALL}
            onChange={onChange}
            options={agentActionsAgentOptions(agents ?? [])}
            loading={loading}
            showSearch={{optionFilterProp: "label"}}
            popupMatchSelectWidth={false}
            style={{minWidth: "14em"}}
        />
    )
}

function AgentActionsTable({initialFilter}) {
    return (
        <StandardTable
            id="agent-actions"
            query={gqlAgentActions}
            queryNode="agentActions"
            size={AGENT_ACTIONS_PAGE_SIZE}
            rowKey={event => event.id}
            initialFilter={initialFilter}
            filterFormVariables={agentActionsVariables}
            filterForm={[
                <Form.Item
                    key="days"
                    name="days"
                    label="Window"
                    htmlFor="agent-actions-window"
                >
                    <Segmented
                        id="agent-actions-window"
                        data-testid="agent-actions-window"
                        options={AGENT_ACTIONS_WINDOWS.map(days => ({label: `${days} days`, value: days}))}
                    />
                </Form.Item>,
                <Form.Item
                    key="agent"
                    name="agent"
                    label="Agent"
                >
                    <SelectAgent id="agent-actions-agent"/>
                </Form.Item>,
                <Form.Item
                    key="eventTypes"
                    name="eventTypes"
                    label="Event types"
                >
                    <SelectMultipleEvents
                        id="agent-actions-event-types"
                        style={{minWidth: "20em"}}
                    />
                </Form.Item>,
                <Form.Item
                    key="project"
                    name="project"
                    label="Project"
                >
                    <SelectProject id="agent-actions-project"/>
                </Form.Item>,
            ]}
            columns={[
                {
                    key: 'time',
                    title: 'Time',
                    dataIndex: 'time',
                    width: 180,
                    render: (value) => <TimestampText value={value} format="YYYY MMM DD, HH:mm:ss"/>,
                },
                {
                    key: 'actor',
                    title: 'Agent',
                    render: (_, event) => <ActorBadge
                        signature={event}
                        link={false}
                        testId={`agent-actions-actor-${event.id}`}
                    />,
                },
                {
                    key: 'message',
                    title: 'Action',
                    dataIndex: 'message',
                    render: (value) => <SafeHTMLComponent htmlContent={value}/>,
                },
                {
                    key: 'project',
                    title: 'Project',
                    render: (_, event) => event.project ?
                        <ProjectLink project={event.project}/> :
                        <Typography.Text type="secondary">None</Typography.Text>,
                },
                {
                    key: 'session',
                    title: 'Session',
                    render: (_, event) => event.actor?.sessionLink ?
                        <a
                            href={event.actor.sessionLink}
                            target="_blank"
                            rel="noopener noreferrer"
                            data-testid={`agent-actions-session-${event.id}`}
                        >
                            Agent session
                        </a> :
                        <Typography.Text type="secondary">None</Typography.Text>,
                },
            ]}
        />
    )
}
