import {Alert, Space} from "antd"
import PageSection from "@components/common/PageSection"
import {isAuthorized} from "@components/common/authorizations"
import SlotAdmissionRulesTable from "@components/extension/environments/SlotAdmissionRulesTable"
import SlotWorkflowsTable from "@components/extension/environments/SlotWorkflowsTable"
import DeleteSlotCommand from "@components/extension/environments/DeleteSlotCommand"

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
 * @param {Object} slot The slot, with its authorizations.
 * @param {number} reloadCount Bumped by the page after any change.
 * @param {function} onChange Called after a change.
 */
export default function SlotSetupTab({slot, reloadCount = 0, onChange}) {

    if (!isAuthorized(slot, 'slot', 'edit')) {
        return <Alert
            type="info"
            showIcon
            message="You are not allowed to configure this slot."
            data-testid="slot-setup-unauthorized"
        />
    }

    return (
        <Space direction="vertical" size={16} className="ot-line" data-testid="slot-setup">
            <PageSection title="Admission rules" padding={false}>
                <SlotAdmissionRulesTable slot={slot} reloadCount={reloadCount} onChange={onChange}/>
            </PageSection>
            <PageSection title="Workflows" padding={false}>
                <SlotWorkflowsTable slot={slot} reloadCount={reloadCount} onChange={onChange}/>
            </PageSection>
            <PageSection title="Danger zone" padding={true}>
                <DeleteSlotCommand slot={slot}/>
            </PageSection>
        </Space>
    )
}
