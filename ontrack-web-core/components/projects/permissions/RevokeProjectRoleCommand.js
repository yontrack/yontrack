import {useState} from "react";
import {FaTimes} from "react-icons/fa";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {revokeProjectRole} from "@components/projects/permissions/ProjectPermissionsService";

/**
 * Revokes one project role from an account or a group, keeping its other roles on the project.
 */
export default function RevokeProjectRoleCommand({projectId, target, role, onSuccess, onError}) {

    const [loading, setLoading] = useState(false)

    const onConfirm = async () => {
        setLoading(true)
        try {
            await revokeProjectRole({projectId, target, projectRole: role.id})
            if (onSuccess) onSuccess()
        } catch (ex) {
            if (onError) onError(ex.message)
        } finally {
            setLoading(false)
        }
    }

    return (
        <InlineConfirmCommand
            title={`Remove the ${role.name} role from ${target.name}`}
            confirm={`Do you want to remove the ${role.name} role on this project from ${target.name}? Its other roles are kept.`}
            icon={<FaTimes/>}
            onConfirm={onConfirm}
            loading={loading}
        />
    )
}
