import {useContext, useEffect} from "react";
import Link from "next/link";
import {Alert, Card, Col, Row, Skeleton, Space, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import LoadingContainer from "@components/common/LoadingContainer";
import PaddedContent from "@components/common/PaddedContent";
import {
    AGENT_ACTIONS_FEATURE,
    AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW,
    agentActionsWindow,
    agentActivityTiles,
    gqlAgentActivityStats,
} from "@components/extension/agents/agentActionsModel";

/**
 * *Agent activity* (#2035): how much of the delivery the agents drive over the last 7, 30 or 90 days,
 * across the projects the user can see - optionally narrowed to some projects, or to the projects
 * carrying some labels.
 *
 * Four tiles - builds, promotions and deployments by agents, and the assisted share of the builds -
 * each linking to the *Latest agent actions* page, filtered on what it counts.
 *
 * Under the Agent governance licence: without it, the widget says so, and reads nothing.
 *
 * The config field `window` is renamed `days` here, not to shadow the global `window`.
 */
export default function AgentActivityWidget({window: days = AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW, projects = [], labels = []}) {

    const actualDays = agentActionsWindow(days, AGENT_ACTIVITY_WIDGET_DEFAULT_WINDOW)

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(`Agent activity - last ${actualDays} days`)
    }, [actualDays])

    const {enabled} = useLicensedFeature(AGENT_ACTIONS_FEATURE)

    return (
        <PaddedContent>
            {
                enabled === undefined && <Skeleton active/>
            }
            {
                enabled === false &&
                <Alert
                    type="warning"
                    showIcon
                    title="Requires the Agent governance licence"
                    description="The activity of the agents is part of the Agent governance. What the agents do is still recorded, and signed with their name."
                    data-testid="agent-activity-widget-licence"
                />
            }
            {
                enabled && <AgentActivityTiles days={actualDays} projects={projects} labels={labels}/>
            }
        </PaddedContent>
    )
}

function AgentActivityTiles({days, projects, labels}) {

    const {data: stats, loading, finished, error} = useQuery(
        gqlAgentActivityStats,
        {
            variables: {
                window: days,
                projects: projects?.length > 0 ? projects : null,
                labels: labels?.length > 0 ? labels : null,
            },
            deps: [days, JSON.stringify(projects), JSON.stringify(labels)],
            dataFn: data => data.agentActivityStats,
        }
    )

    const tiles = stats ? agentActivityTiles(stats, projects) : []

    return (
        <LoadingContainer loading={loading || !finished} error={error}>
            <Row gutter={[12, 12]} data-testid="agent-activity-widget">
                {
                    tiles.map(tile =>
                        <Col key={tile.key} xs={12} xl={6}>
                            <AgentActivityTile tile={tile}/>
                        </Col>
                    )
                }
            </Row>
        </LoadingContainer>
    )
}

/**
 * A count with its label, the count linking to the actions it counts.
 */
function AgentActivityTile({tile}) {
    const testId = `agent-activity-${tile.key}`
    return (
        <Card size="small" data-testid={testId}>
            <Space orientation="vertical" size={0}>
                <Typography.Text type="secondary">{tile.label}</Typography.Text>
                <Link
                    href={tile.href}
                    aria-label={`${tile.label}: ${tile.ariaValue ?? tile.value}`}
                    data-testid={`${testId}-value`}
                    style={{fontSize: 28, fontWeight: 600, lineHeight: 1.3}}
                >
                    {tile.value}
                </Link>
                {
                    tile.detail &&
                    <Typography.Text type="secondary" data-testid={`${testId}-detail`}>{tile.detail}</Typography.Text>
                }
                {
                    tile.extra &&
                    <Typography.Text type="secondary" data-testid={`${testId}-extra`}>{tile.extra}</Typography.Text>
                }
            </Space>
        </Card>
    )
}
