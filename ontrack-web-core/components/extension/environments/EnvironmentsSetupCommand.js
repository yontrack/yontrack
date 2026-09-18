import {useContext} from "react"
import {FaCog} from "react-icons/fa"
import {Command} from "@components/common/Commands"
import {UserContext} from "@components/providers/UserProvider"
import {environmentsSetupUri} from "@components/extension/environments/EnvironmentsLinksUtils"

/**
 * **Setup** - the way from the Environments home to the page where configuring happens.
 *
 * The redesign takes "New environment" and "New slot" off the matrix's header: creating things is
 * not what somebody came to the Environments home to do, and in practice it is done as code. #1791
 * put them behind this command as a dropdown of two dialogs, as a stepping stone; since #1793 the
 * Setup page exists and this is a plain link to it, which is what the redesign always said it would
 * become.
 *
 * Hidden without any configuration right at all, like every other unauthorised action in Yontrack:
 * a Setup page whose every control is missing is worse than no Setup button.
 */
export default function EnvironmentsSetupCommand() {

    const user = useContext(UserContext)

    const canCreateEnvironment = !!user.authorizations?.environment?.create
    const canEditEnvironment = !!user.authorizations?.environment?.edit
    const canDeleteEnvironment = !!user.authorizations?.environment?.delete
    // The global half of the `slot` context: "can create a slot on at least one project". See
    // `EnvironmentsAuthorizationContributor`.
    const canCreateSlot = !!user.authorizations?.slot?.create

    if (!canCreateEnvironment && !canEditEnvironment && !canDeleteEnvironment && !canCreateSlot) return null

    return (
        <Command
            icon={<FaCog/>}
            href={environmentsSetupUri}
            text="Setup"
            title="Set up environments and slots"
            testId="environments-setup"
        />
    )
}
