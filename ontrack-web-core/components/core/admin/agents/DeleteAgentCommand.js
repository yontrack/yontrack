import {Button, Popconfirm} from "antd";
import {FaTrashAlt} from "react-icons/fa";
import {useDeleteAgent} from "@components/core/admin/agents/AgentsService";

/**
 * Deletes an agent and its tokens, after confirmation.
 */
export default function DeleteAgentCommand({agent, onSuccess}) {
    const {deleteAgent, loading} = useDeleteAgent({onSuccess})
    const label = `Delete the agent ${agent.fullName}`
    return (
        <Popconfirm
            title="Agent deletion"
            description={`Delete ${agent.fullName} (${agent.email}) and its tokens? Its name stays on everything it signed.`}
            okText="Delete"
            onConfirm={() => deleteAgent(agent)}
        >
            <Button
                type="text"
                danger={true}
                icon={<FaTrashAlt/>}
                title={label}
                aria-label={label}
                loading={loading}
            />
        </Popconfirm>
    )
}
