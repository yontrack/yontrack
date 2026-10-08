import {Alert} from "antd";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";

/**
 * Says that *Evidence from non-agents only* does nothing without the "Agent governance" licence - and
 * says nothing while the licence is not known, or when it allows the feature.
 */
export default function LicenceNotice() {
    const {enabled} = useLicensedFeature("extension.agents")
    return enabled === false ?
        <Alert
            type="warning"
            showIcon
            title="Requires the Agent governance licence"
            description="Without it, this restriction is kept but not applied: agents record evidence on this validation stamp like anybody else."
            data-testid="non-agent-evidence-licence"
        /> :
        null
}
