import StandardPage from "@components/layouts/StandardPage";
import {useRefresh} from "@components/common/RefreshUtils";
import {useAgent} from "@components/core/admin/agents/AgentsService";
import {agentSlug} from "@components/core/admin/agents/agentsModel";
import {Descriptions, Empty, Space, Tabs, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import AgentTokens from "@components/core/admin/agents/AgentTokens";
import {useContext} from "react";
import {UserContext} from "@components/providers/UserProvider";
import {Command} from "@components/common/Commands";
import {FaList, FaTimes} from "react-icons/fa";
import {agentsUri, homeUri, myAgentsUri} from "@components/common/Links";
import EditAgentCommand from "@components/core/admin/agents/EditAgentCommand";
import TransferAgentCommand from "@components/core/admin/agents/TransferAgentCommand";
import DeleteAgentCommand from "@components/core/admin/agents/DeleteAgentCommand";
import {useRouter} from "next/router";
import Link from "next/link";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import AccountKindTag from "@components/common/actors/AccountKindTag";
import AgentActivity from "@components/core/admin/agents/AgentActivity";

/**
 * Page of an agent, for its owner and the administrators: its details and its tokens, and its
 * activity (#2034).
 */
export default function AgentView({id}) {
    const [refreshState, refresh] = useRefresh()
    const {agent, loading} = useAgent({id, refreshState})
    const user = useContext(UserContext)
    const admin = !!user?.authorizations?.accounts?.config

    const listUri = admin ? agentsUri() : myAgentsUri()

    return (
        <StandardPage
            pageTitle={agent ? agent.fullName : "Agent"}
            breadcrumbs={[
                ...homeBreadcrumbs(),
                <Link key="agents" href={listUri}>{admin ? "Agents" : "My agents"}</Link>,
            ]}
            commands={[
                <Command key="agents" icon={<FaList/>} href={listUri} text={admin ? "Agents" : "My agents"}/>,
                <Command key="home" icon={<FaTimes/>} href={homeUri()} text="Close"/>,
            ]}
        >
            {
                !loading && !agent &&
                <Empty description="This agent does not exist, or is not yours."/>
            }
            {
                agent &&
                <Tabs
                    data-testid="agent-tabs"
                    defaultActiveKey="details"
                    items={[
                        {
                            key: 'details',
                            label: 'Details',
                            children: <AgentDetails agent={agent} admin={admin} refresh={refresh} listUri={listUri}/>,
                        },
                        {
                            key: 'activity',
                            label: 'Activity',
                            children: <AgentActivity agent={agent}/>,
                        },
                    ]}
                />
            }
        </StandardPage>
    )
}

/**
 * The details of an agent and its tokens.
 */
function AgentDetails({agent, admin, refresh, listUri}) {
    const router = useRouter()
    return (
        <Space orientation="vertical" className="ot-line">
            <PageSection
                title="Agent"
                padding={true}
                extra={
                    <Space size={0}>
                        <EditAgentCommand agent={agent} refresh={refresh}/>
                        {admin && <TransferAgentCommand agent={agent} refresh={refresh}/>}
                        <DeleteAgentCommand agent={agent} onSuccess={() => router.push(listUri)}/>
                    </Space>
                }
            >
                <Descriptions
                    column={1}
                    items={[
                        {key: 'name', label: 'Display name', children: agent.fullName},
                        {
                            key: 'kind',
                            label: 'Kind',
                            children: <AccountKindTag kind={agent.kind} testId="agent-kind"/>,
                        },
                        {
                            key: 'identifier',
                            label: 'Identifier',
                            children: <Typography.Text code={true}>{agent.email}</Typography.Text>,
                        },
                        {key: 'slug', label: 'Slug', children: agentSlug(agent)},
                        {key: 'tool', label: 'Tool', children: agent.agentTool},
                        {
                            key: 'owner',
                            label: 'Owner',
                            children: `${agent.owner?.fullName} (${agent.owner?.email})`,
                        },
                        {
                            key: 'description',
                            label: 'Description',
                            children: agent.agentDescription ??
                                <Typography.Text type="secondary">None</Typography.Text>,
                        },
                    ]}
                />
            </PageSection>
            <PageSection title="Tokens" padding={true}>
                <AgentTokens agent={agent} refresh={refresh}/>
            </PageSection>
        </Space>
    )
}
