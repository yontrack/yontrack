import {Command} from "@components/common/Commands";
import {FaPlus} from "react-icons/fa";
import {useRouter} from "next/router";
import RegisterAgentDialog, {useRegisterAgentDialog} from "@components/core/admin/agents/RegisterAgentDialog";
import {agentUri} from "@components/common/Links";

/**
 * Registers an agent, and goes to its page to generate its first token.
 */
export default function RegisterAgentCommand() {
    const router = useRouter()

    const dialog = useRegisterAgentDialog({
        onSuccess: (result) => {
            const id = result?.agent?.id
            if (id) {
                router.push(agentUri({id}))
            }
        },
    })

    return (
        <>
            <Command
                icon={<FaPlus/>}
                text="Register an agent"
                title="Registers a new agent, owned by you"
                action={() => dialog.start({})}
            />
            <RegisterAgentDialog dialog={dialog}/>
        </>
    )
}
