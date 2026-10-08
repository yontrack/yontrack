import {useRouter} from "next/router";
import StandardPage from "@components/layouts/StandardPage";
import AgentActionsView from "@components/extension/agents/AgentActionsView";
import {agentActionsFilterFromQuery} from "@components/extension/agents/agentActionsModel";

/**
 * *Latest agent actions* (#2035), reached from the information group of the user menu and from the
 * tiles of the *Agent activity* widget, which filter it through the URL. Licensed.
 */
export default function AgentActionsPage() {
    const router = useRouter()
    return (
        <StandardPage pageTitle="Latest agent actions" loading={!router.isReady}>
            {
                router.isReady &&
                <AgentActionsView initialFilter={agentActionsFilterFromQuery(router.query)}/>
            }
        </StandardPage>
    )
}
