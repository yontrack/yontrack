import {Typography} from "antd";
import Table from "@components/common/table/Table";
import TimestampText from "@components/common/TimestampText";
import ActorBadge from "@components/common/actors/ActorBadge";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import LoadMoreButton from "@components/common/LoadMoreButton";

/**
 * *Actions by agents*, from the event log (#2033): the events of the build whose actor is an agent,
 * newest first - each one with its time, its message, the agent and its session.
 *
 * The agent's badge does not link to the session itself: the session has its own column, with a
 * link a keyboard reaches and a screen reader reads as such.
 *
 * @param actions Events loaded so far (`Build.agentActions.pageItems`)
 * @param pageInfo Page info of the last page loaded
 * @param loading Whether a page is being loaded
 * @param onLoadMore Loads the next page
 */
export default function BuildAgentsActions({actions, pageInfo, loading, onLoadMore}) {
    return (
        <section aria-labelledby="build-agents-actions-title" data-testid="build-agents-actions">
            <Typography.Title level={5} id="build-agents-actions-title">Actions by agents</Typography.Title>
            <Table
                dataSource={actions}
                rowKey="id"
                size="small"
                pagination={false}
                loading={loading}
                locale={{emptyText: "No action by an agent on this build."}}
                onRow={(event) => ({'data-testid': `build-agent-action-${event.id}`})}
                columns={[
                    {
                        key: 'time',
                        title: 'Time',
                        dataIndex: 'time',
                        width: 180,
                        render: (value) => <TimestampText value={value} format="YYYY MMM DD, HH:mm:ss"/>,
                    },
                    {
                        key: 'agent',
                        title: 'Agent',
                        render: (_, event) => <ActorBadge
                            signature={event}
                            link={false}
                            testId={`build-agent-action-actor-${event.id}`}
                        />,
                    },
                    {
                        key: 'message',
                        title: 'Action',
                        dataIndex: 'message',
                        render: (value) => <SafeHTMLComponent htmlContent={value}/>,
                    },
                    {
                        key: 'session',
                        title: 'Session',
                        render: (_, event) => event.actor?.sessionLink ?
                            <a
                                href={event.actor.sessionLink}
                                target="_blank"
                                rel="noopener noreferrer"
                                data-testid={`build-agent-action-session-${event.id}`}
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
                            moreText="There are more actions by agents to be loaded"
                            noMoreText="There are no more actions by agents to be loaded"
                            onLoadMore={onLoadMore}
                        /> :
                        undefined
                }
            />
        </section>
    )
}
