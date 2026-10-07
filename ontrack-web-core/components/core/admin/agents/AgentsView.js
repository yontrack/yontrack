import StandardPage from "@components/layouts/StandardPage";
import {useRefresh} from "@components/common/RefreshUtils";
import {useAgents} from "@components/core/admin/agents/AgentsService";
import AgentsTable from "@components/core/admin/agents/AgentsTable";
import RegisterAgentCommand from "@components/core/admin/agents/RegisterAgentCommand";
import {Typography} from "antd";
import {CloseToHomeCommand} from "@components/common/Commands";
import {useContext} from "react";
import {UserContext} from "@components/providers/UserProvider";

/**
 * Every agent of the instance, for the administrators.
 */
export default function AgentsView() {
    const [refreshState, refresh] = useRefresh()
    const {agents, loading} = useAgents({refreshState})
    const user = useContext(UserContext)
    const admin = !!user?.authorizations?.accounts?.config
    return (
        <StandardPage
            pageTitle="Agents"
            commands={[
                <RegisterAgentCommand key="register"/>,
                <CloseToHomeCommand key="home"/>,
            ]}
        >
            <Typography.Paragraph type="secondary">
                Agents act on Yontrack with their own tokens, on behalf of the person who owns them.
                An agent has the rights of its owner, narrowed by the agent policy: it reads, records
                evidence, and promotes or deploys only where agents are admitted. It never approves.
            </Typography.Paragraph>
            <AgentsTable
                agents={agents}
                loading={loading}
                refresh={refresh}
                showOwner={true}
                canTransfer={admin}
            />
        </StandardPage>
    )
}
