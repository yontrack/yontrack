import {Tag} from "antd";
import {stateName} from "@components/extension/findings/findingsModel";

const colors = {
    OPEN: 'error',
    ACCEPTED: 'warning',
    RESOLVED: 'success',
}

/**
 * State of a finding, in its project or on a branch.
 */
export default function FindingStateTag({state}) {
    if (!state) return null
    return (
        <Tag color={colors[state] ?? 'default'}>
            {stateName(state)}
        </Tag>
    )
}
