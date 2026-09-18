import {useContext} from "react"
import {Button, Dropdown, Space, Typography} from "antd"
import {FaCog} from "react-icons/fa"
import {UserContext} from "@components/providers/UserProvider"
import NewEnvironmentDialog, {useNewEnvironmentDialog} from "@components/extension/environments/NewEnvironmentDialog"
import NewSlotDialog, {useNewSlotDialog} from "@components/extension/environments/NewSlotDialog"

/**
 * **Setup** - where configuring environments and slots moves to, out of the operational screens.
 *
 * The redesign takes "New environment" and "New slot" off the matrix's header: creating things is
 * not what somebody came to the Environments home to do, and in practice it is done as code. The
 * Setup *page* that will hold them arrives with phase 4 of the redesign; until then this command is
 * the doorway, and behind it are the same two dialogs the header used to carry. A reader gets one
 * header button instead of two, and the day the page exists this command points at it instead -
 * which is a change of one `href` rather than a change to the header again.
 *
 * Hidden without any configuration right at all, like every other unauthorised action in Yontrack:
 * a Setup menu whose every entry is missing is worse than no Setup menu.
 */
export default function EnvironmentsSetupCommand() {

    const user = useContext(UserContext)

    const newEnvironmentDialog = useNewEnvironmentDialog()
    const newSlotDialog = useNewSlotDialog()

    const canCreateEnvironment = !!user.authorizations?.environment?.create
    // The slot dialog is gated on `environment.view` today, which is a known defect of its own
    // (noted in the redesign's list) - this command repeats the existing gate rather than silently
    // tightening or loosening it.
    const canCreateSlot = !!user.authorizations?.environment?.view

    if (!canCreateEnvironment && !canCreateSlot) return null

    const items = []
    if (canCreateEnvironment) {
        items.push({key: 'new-environment', label: "New environment"})
    }
    if (canCreateSlot) {
        items.push({key: 'new-slot', label: "New slot"})
    }

    const onClick = ({key}) => {
        if (key === 'new-environment') newEnvironmentDialog.start({})
        if (key === 'new-slot') newSlotDialog.start({})
    }

    return (
        <>
            <Dropdown menu={{items, onClick}} trigger={['click']}>
                <Button type="text" data-testid="environments-setup" title="Set up environments and slots">
                    <Space size={8}>
                        <FaCog/>
                        <Typography.Text>Setup</Typography.Text>
                    </Space>
                </Button>
            </Dropdown>
            <NewEnvironmentDialog newEnvironmentDialog={newEnvironmentDialog}/>
            <NewSlotDialog newSlotDialog={newSlotDialog}/>
        </>
    )
}
