import {Alert} from "antd";
import {useLicensedFeature} from "@components/extension/license/useLicensedFeature";

/**
 * Says that *Assisted builds require* does nothing without the "Agent governance" licence - and says
 * nothing while the licence is not known, or when it allows the feature.
 */
export default function LicenceNotice() {
    const {enabled} = useLicensedFeature("extension.agents")
    return enabled === false ?
        <Alert
            type="warning"
            showIcon
            title="Requires the Agent governance licence"
            description="Without it, this condition is kept but not applied: assisted builds are promoted without these validations."
            data-testid="assisted-builds-require-licence"
        /> :
        null
}
