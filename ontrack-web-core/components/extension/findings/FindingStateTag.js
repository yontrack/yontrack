import {Tag} from "antd";
import {stateName} from "@components/extension/findings/findingsModel";

const colors = {
    OPEN: 'error',
    EXPOSED: 'error',
    ACCEPTED: 'warning',
    RESOLVED: 'success',
}

/**
 * State of a finding, in its project or on a branch, or of its exposure on a branch for a stamp.
 */
export default function FindingStateTag({state}) {
    if (!state) return null
    return (
        <Tag color={colors[state] ?? 'default'}>
            {stateName(state)}
        </Tag>
    )
}
