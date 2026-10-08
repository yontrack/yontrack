import {Space, Typography} from "antd";
import Link from "next/link";
import {FaKey} from "react-icons/fa";
import Table from "@components/common/table/Table";
import TimestampText from "@components/common/TimestampText";
import {agentUri} from "@components/common/Links";
import {agentLastUsed} from "@components/core/admin/agents/agentsModel";
import EditAgentCommand from "@components/core/admin/agents/EditAgentCommand";
import TransferAgentCommand from "@components/core/admin/agents/TransferAgentCommand";
import DeleteAgentCommand from "@components/core/admin/agents/DeleteAgentCommand";
import AccountKindTag from "@components/common/actors/AccountKindTag";

/**
 * List of agents with their tool, owner, tokens and actions.
 *
 * @param agents Agents to list
 * @param loading Loading indicator
 * @param refresh Reloads the list after an action
 * @param showOwner Showing the owner of each agent
 * @param canTransfer Offering the transfer of each agent (administrators)
 */
export default function AgentsTable({agents, loading, refresh, showOwner = true, canTransfer = false}) {
    return (
        <Table
            data-testid="agents"
            dataSource={agents}
            loading={loading}
            rowKey="id"
            pagination={false}
            locale={{emptyText: "No agent registered."}}
        >
            <Table.Column
                key="agent"
                title="Agent"
                render={(_, agent) =>
                    <Space orientation="vertical" size={0}>
                        <Space size={4}>
                            <Link href={agentUri(agent)}>{agent.fullName}</Link>
                            <AccountKindTag kind={agent.kind} testId={`agent-kind-${agent.id}`}/>
                        </Space>
                        <Typography.Text type="secondary">{agent.email}</Typography.Text>
                    </Space>
                }
            />
            <Table.Column
                key="tool"
                title="Tool"
                dataIndex="agentTool"
            />
            {
                showOwner &&
                <Table.Column
                    key="owner"
                    title="Owner"
                    render={(_, agent) =>
                        <Typography.Text title={agent.owner?.email}>{agent.owner?.fullName}</Typography.Text>
                    }
                />
            }
            <Table.Column
                key="tokens"
                title="Tokens"
                render={(_, agent) => agent.tokens?.length ?? 0}
            />
            <Table.Column
                key="lastUsed"
                title="Last used"
                render={(_, agent) => {
                    const lastUsed = agentLastUsed(agent)
                    return lastUsed ?
                        <TimestampText value={lastUsed} relative={true}/> :
                        <Typography.Text type="secondary">Never</Typography.Text>
                }}
            />
            <Table.Column
                key="actions"
                title="Actions"
                render={(_, agent) =>
                    <Space size={0}>
                        <EditAgentCommand agent={agent} refresh={refresh}/>
                        {
                            canTransfer &&
                            <TransferAgentCommand agent={agent} refresh={refresh}/>
                        }
                        <Link
                            href={agentUri(agent)}
                            title={`Tokens of the agent ${agent.fullName}`}
                            aria-label={`Tokens of the agent ${agent.fullName}`}
                            style={{padding: '4px 15px'}}
                        >
                            <FaKey aria-hidden={true}/>
                        </Link>
                        <DeleteAgentCommand agent={agent} onSuccess={refresh}/>
                    </Space>
                }
            />
        </Table>
    )
}
