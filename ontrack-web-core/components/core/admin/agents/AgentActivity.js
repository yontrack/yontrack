import {useState} from "react";
import {Alert, Form, Segmented, Skeleton, Space, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";
import Table from "@components/common/table/Table";
import TimestampText from "@components/common/TimestampText";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import LoadMoreButton from "@components/common/LoadMoreButton";
import ProjectLink from "@components/projects/ProjectLink";
import SelectProject from "@components/projects/SelectProject";
import SelectMultipleEvents from "@components/core/model/SelectMultipleEvents";
import {
    AGENT_ACTIVITY_FEATURE,
    AGENT_ACTIVITY_PAGE_SIZE,
    AGENT_ACTIVITY_WINDOWS,
    agentActivityFilter,
    agentActivityVariables,
    gqlAgentActivity,
} from "@components/core/admin/agents/agentActivityModel";

const FIRST_PAGE = {offset: 0, size: AGENT_ACTIVITY_PAGE_SIZE}

/**
 * The *Activity* tab of an agent's page (#2034): what the agent did over the last 7, 30 or 90 days,
 * filtered by event type and project - for its owner and the administrators.
 *
 * Under the Agent governance licence: without it, the tab says so, and never shows an empty list.
 *
 * Desktop only, as the rest of the agent administration.
 *
 * @param agent Agent of the page (`id`)
 */
export default function AgentActivity({agent}) {
    const {enabled} = useLicensedFeature(AGENT_ACTIVITY_FEATURE)
    if (enabled === undefined) {
        return <Skeleton active/>
    } else if (!enabled) {
        return <Alert
            type="warning"
            showIcon
            title="Requires the Agent governance licence"
            description="The activity of the agents is part of the Agent governance. What the agents do is still recorded, and signed with their name."
            data-testid="agent-activity-licence"
        />
    } else {
        return <AgentActivityFeed agent={agent}/>
    }
}

/**
 * The filters and the actions of the agent, newest first, with *Load more*.
 */
function AgentActivityFeed({agent}) {

    // The window starts when it is chosen: a filter is computed once, never on each render
    const [filter, setFilter] = useState(() => agentActivityFilter())
    const [pagination, setPagination] = useState(FIRST_PAGE)

    const changeFilter = (changes) => {
        setFilter(current => ({...current, ...changes}))
        setPagination(FIRST_PAGE)
    }

    const {data: page, loading} = useQuery(
        gqlAgentActivity,
        {
            variables: agentActivityVariables({
                id: agent.id,
                filter,
                offset: pagination.offset,
                size: pagination.size,
            }),
            deps: [agent.id, filter, pagination],
            dataFn: data => ({
                activity: data.agents?.[0]?.agentActivity,
                offset: pagination.offset,
            }),
        }
    )

    // A page beyond the first one is appended to the actions already loaded. This is state kept
    // from one page to the next, adjusted while rendering.
    const [loaded, setLoaded] = useState({page: null, actions: []})
    if (page && page !== loaded.page) {
        const newItems = page.activity?.pageItems ?? []
        setLoaded({
            page,
            actions: page.offset > 0 ? [...loaded.actions, ...newItems] : newItems,
        })
    }
    const pageInfo = loaded.page?.activity?.pageInfo

    const onLoadMore = () => {
        if (pageInfo?.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    return (
        <Space orientation="vertical" className="ot-line" data-testid="agent-activity">
            <Form layout="inline">
                <Form.Item label="Window" htmlFor="agent-activity-window">
                    <Segmented
                        id="agent-activity-window"
                        data-testid="agent-activity-window"
                        value={filter.days}
                        options={AGENT_ACTIVITY_WINDOWS.map(days => ({label: `${days} days`, value: days}))}
                        onChange={days => changeFilter(agentActivityFilter(days))}
                    />
                </Form.Item>
                <Form.Item label="Event types" htmlFor="agent-activity-event-types">
                    <SelectMultipleEvents
                        id="agent-activity-event-types"
                        style={{minWidth: "20em"}}
                        value={filter.eventTypes}
                        onChange={eventTypes => changeFilter({eventTypes: eventTypes ?? []})}
                    />
                </Form.Item>
                <Form.Item label="Project" htmlFor="agent-activity-project">
                    <SelectProject
                        id="agent-activity-project"
                        value={filter.project}
                        onChange={project => changeFilter({project: project ?? null})}
                    />
                </Form.Item>
            </Form>
            <Table
                dataSource={loaded.actions}
                rowKey="id"
                size="small"
                pagination={false}
                loading={loading}
                locale={{emptyText: `No action by this agent in the last ${filter.days} days.`}}
                onRow={(event) => ({'data-testid': `agent-activity-${event.id}`})}
                columns={[
                    {
                        key: 'time',
                        title: 'Time',
                        dataIndex: 'time',
                        width: 180,
                        render: (value) => <TimestampText value={value} format="YYYY MMM DD, HH:mm:ss"/>,
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
                                data-testid={`agent-activity-session-${event.id}`}
                            >
                                Agent session
                            </a> :
                            <Typography.Text type="secondary">None</Typography.Text>,
                    },
                ]}
                footer={
                    pageInfo?.nextPage ?
                        () => <LoadMoreButton
                            pageInfo={pageInfo}
                            moreText="There are more actions of this agent to be loaded"
                            noMoreText="There are no more actions of this agent to be loaded"
                            onLoadMore={onLoadMore}
                        /> :
                        undefined
                }
            />
        </Space>
    )
}
