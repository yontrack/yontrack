import {Popconfirm, Space, Typography} from "antd";
import {Command} from "@components/common/Commands";
import {FaTrashAlt} from "react-icons/fa";
import {useMutationDeleteAccount} from "@components/core/admin/account-management/AccountManagementService";

export default function DeleteAccountCommand({account, refresh}) {

    const {deleteAccount, loading} = useMutationDeleteAccount({
        onSuccess: refresh,
    })

    // Deleting a person deletes the agents they own
    const agents = account.agents ?? []

    const onDeleteAccount = async () => {
        await deleteAccount({accountId: Number(account.id)})
    }

    return (
        <Popconfirm
            title="Account deletion"
            description={
                agents.length > 0 ?
                    <Space orientation="vertical" size={0}>
                        <Typography.Text>
                            Are you sure you want to delete this account? Its agents are deleted with it:
                        </Typography.Text>
                        <ul data-testid="account-deletion-agents">
                            {
                                agents.map(agent =>
                                    <li key={agent.id}>{agent.fullName} ({agent.email})</li>
                                )
                            }
                        </ul>
                    </Space> :
                    "Are you sure you want to delete this account?"
            }
            onConfirm={onDeleteAccount}
        >
            <div>
                <Command
                    icon={<FaTrashAlt/>}
                    title="Delete this account"
                    disabled={loading}
                />
            </div>
        </Popconfirm>
    )
}