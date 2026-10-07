import {Button} from "antd";
import {FaExchangeAlt} from "react-icons/fa";
import TransferAgentDialog, {useTransferAgentDialog} from "@components/core/admin/agents/TransferAgentDialog";

/**
 * Transfers an agent to another owner - administrators only.
 */
export default function TransferAgentCommand({agent, refresh}) {
    const dialog = useTransferAgentDialog({onSuccess: refresh})
    const label = `Transfer the agent ${agent.fullName} to another owner`
    return (
        <>
            <Button
                type="text"
                icon={<FaExchangeAlt/>}
                title={label}
                aria-label={label}
                onClick={() => dialog.start({agent})}
            />
            <TransferAgentDialog dialog={dialog}/>
        </>
    )
}
