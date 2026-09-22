import {useState} from "react"
import {Space} from "antd"
import SlotBuildEligibilitySwitch from "@components/extension/environments/SlotBuildEligibilitySwitch"
import SlotEligibleBuildsTable from "@components/extension/environments/SlotEligibleBuildsTable"

/**
 * **Eligible builds** - what could go into this slot next, each with Deploy.
 *
 * Unchanged in substance from the panel it replaces, including its "Show all eligible builds"
 * switch: *deployable* builds are the ones the slot's rules already accept, and the switch widens
 * the list to every eligible one so that "why can I not deploy this?" has somewhere to be asked.
 * What changed is where it sits - a tab of its own rather than a third of a split page, next to a
 * deployments table it had nothing to do with.
 *
 * @param {Object} slot The slot.
 * @param {function} onChange Called after a deployment is started.
 * @param {function} onDeploy The page's deploy dialog: one dialog per page, not one per tab.
 */
export default function SlotEligibleBuildsTab({slot, onChange, onDeploy}) {

    const [showEligibleBuilds, setShowEligibleBuilds] = useState(false)

    return (
        <Space orientation="vertical" size={16} className="ot-line" data-testid="slotBuilds">
            <SlotBuildEligibilitySwitch
                value={showEligibleBuilds}
                onChange={setShowEligibleBuilds}
            />
            <SlotEligibleBuildsTable
                slot={slot}
                onChange={onChange}
                onDeploy={onDeploy}
                showEligibleBuilds={showEligibleBuilds}
            />
        </Space>
    )
}
