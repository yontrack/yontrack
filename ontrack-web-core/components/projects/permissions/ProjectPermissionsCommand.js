import {FaUserLock} from "react-icons/fa";
import {Command} from "@components/common/Commands";
import {projectPermissionsUri} from "@components/common/Links";

/**
 * Link from the project page to its permissions page, for the holders of the right to manage them.
 */
export default function ProjectPermissionsCommand({project}) {
    return (
        <Command
            icon={<FaUserLock/>}
            text="Permissions"
            title="Grants and revokes the roles of accounts and groups on this project"
            href={projectPermissionsUri(project)}
            testId="project-permissions-command"
        />
    )
}
