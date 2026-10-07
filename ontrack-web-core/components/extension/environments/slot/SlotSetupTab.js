import {Alert, Button, Space, Typography} from "antd"
import {FaEdit} from "react-icons/fa"
import PageSection from "@components/common/PageSection"
import {isAuthorized} from "@components/common/authorizations"
import SlotAdmissionRulesTable from "@components/extension/environments/SlotAdmissionRulesTable"
import SlotWorkflowsTable from "@components/extension/environments/SlotWorkflowsTable"
import DeleteSlotCommand from "@components/extension/environments/DeleteSlotCommand"
import SlotSettingsDialog, {useSlotSettingsDialog} from "@components/extension/environments/slot/SlotSettingsDialog"

/**
 * **Setup** - the slot's admission rules and workflows, and the way to delete it.
 *
 * These used to be two full-width sections under the operational half of the slot page, which put
 * configuration in the middle of a screen somebody opened to move a build forward. They are behind
 * a tab now, and the whole tab is hidden without `slot.edit`: a reader who cannot change any of it
 * is not being kept from anything they came for.
 *
 * **Delete slot** moves here from the page header for the same reason. It was the one header
 * command of an operational page that destroyed configuration, sitting next to Close.
 *
 * **Settings** come first: the description, and whether the slot admits agents - the agent policy
 * refuses any pipeline action by an agent in a slot which does not.
 *
 * @param {Object} slot The slot, with its authorizations.
 * @param {number} reloadCount Bumped by the page after any change.
 * @param {function} onChange Called after a change.
 */
export default function SlotSetupTab({slot, reloadCount = 0, onChange}) {

    const settingsDialog = useSlotSettingsDialog({onSuccess: onChange})

    if (!isAuthorized(slot, 'slot', 'edit')) {
        return <Alert
            type="info"
            showIcon
            title="You are not allowed to configure this slot."
            data-testid="slot-setup-unauthorized"
        />
    }

    return (
        <Space orientation="vertical" size={16} className="ot-line" data-testid="slot-setup">
            <PageSection
                title="Settings"
                padding={true}
                extra={
                    <Button
                        icon={<FaEdit/>}
                        onClick={() => settingsDialog.start({slot})}
                        data-testid="slot-settings-edit"
                    >
                        Edit
                    </Button>
                }
            >
                <Space orientation="vertical" data-testid="slot-settings">
                    <Typography.Text>
                        {slot.description || <Typography.Text type="secondary">No description</Typography.Text>}
                    </Typography.Text>
                    {
                        slot.agentsAdmitted ?
                            <Typography.Text data-testid="slot-agents-admitted">
                                Agents admitted: an agent may deploy in this slot, provided its owner may.
                            </Typography.Text> :
                            <Typography.Text type="secondary" data-testid="slot-agents-not-admitted">
                                Agents not admitted: an agent may not deploy in this slot.
                            </Typography.Text>
                    }
                </Space>
            </PageSection>
            <PageSection title="Admission rules" padding={false}>
                <SlotAdmissionRulesTable slot={slot} reloadCount={reloadCount} onChange={onChange}/>
            </PageSection>
            <PageSection title="Workflows" padding={false}>
                <SlotWorkflowsTable slot={slot} reloadCount={reloadCount} onChange={onChange}/>
            </PageSection>
            <PageSection title="Danger zone" padding={true}>
                <DeleteSlotCommand slot={slot}/>
            </PageSection>
            <SlotSettingsDialog dialog={settingsDialog}/>
        </Space>
    )
}
