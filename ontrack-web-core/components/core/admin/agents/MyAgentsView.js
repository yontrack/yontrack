import StandardPage from "@components/layouts/StandardPage";
import {useRefresh} from "@components/common/RefreshUtils";
import {useAgents} from "@components/core/admin/agents/AgentsService";
import AgentsTable from "@components/core/admin/agents/AgentsTable";
import RegisterAgentCommand from "@components/core/admin/agents/RegisterAgentCommand";
import {Typography} from "antd";
import {CloseToHomeCommand} from "@components/common/Commands";

/**
 * The agents of the current user.
 */
export default function MyAgentsView() {
    const [refreshState, refresh] = useRefresh()
    const {agents, loading} = useAgents({refreshState, mine: true})
    return (
        <StandardPage
            pageTitle="My agents"
            commands={[
                <RegisterAgentCommand key="register"/>,
                <CloseToHomeCommand key="home"/>,
            ]}
        >
            <Typography.Paragraph type="secondary">
                Your agents act on Yontrack with their own tokens, and you are accountable for them.
                An agent has your rights, narrowed by the agent policy: it reads, records evidence, and
                promotes or deploys only where agents are admitted. It never approves.
            </Typography.Paragraph>
            <AgentsTable
                agents={agents}
                loading={loading}
                refresh={refresh}
                showOwner={false}
                canTransfer={false}
            />
        </StandardPage>
    )
}
