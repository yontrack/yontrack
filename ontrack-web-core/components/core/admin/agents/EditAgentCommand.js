import {Button} from "antd";
import {FaPencilAlt} from "react-icons/fa";
import EditAgentDialog, {useEditAgentDialog} from "@components/core/admin/agents/EditAgentDialog";

export default function EditAgentCommand({agent, refresh}) {
    const dialog = useEditAgentDialog({onSuccess: refresh})
    const label = `Edit the agent ${agent.fullName}`
    return (
        <>
            <Button
                type="text"
                icon={<FaPencilAlt/>}
                title={label}
                aria-label={label}
                onClick={() => dialog.start({agent})}
            />
            <EditAgentDialog dialog={dialog}/>
        </>
    )
}
