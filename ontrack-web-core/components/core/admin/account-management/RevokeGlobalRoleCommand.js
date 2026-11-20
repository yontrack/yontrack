import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {FaTimes} from "react-icons/fa";
import {
    useMutationRevokeGlobalRoleFromAccount,
    useMutationRevokeGlobalRoleFromAccountGroup
} from "@components/core/admin/account-management/GlobalPermissionsService";

/**
 * Revokes one global role from an account or a group, keeping its other global roles.
 */
export default function RevokeGlobalRoleCommand({refresh, target, role}) {

    const {
        revokeGlobalRoleFromAccount,
        loading: loadingAccount
    } = useMutationRevokeGlobalRoleFromAccount({onSuccess: refresh})
    const {
        revokeGlobalRoleFromAccountGroup,
        loading: loadingAccountGroup
    } = useMutationRevokeGlobalRoleFromAccountGroup({onSuccess: refresh})

    const onConfirm = async () => {
        if (target.type === 'ACCOUNT') {
            await revokeGlobalRoleFromAccount({accountId: target.id, role: role.id})
        } else {
            await revokeGlobalRoleFromAccountGroup({accountGroupId: target.id, role: role.id})
        }
    }

    return (
        <InlineConfirmCommand
            title={`Remove the ${role.name} role from ${target.name}`}
            confirm={`Do you want to remove the ${role.name} global role from ${target.name}? Its other global roles are kept.`}
            icon={<FaTimes/>}
            onConfirm={onConfirm}
            loading={loadingAccount || loadingAccountGroup}
        />
    )
}
