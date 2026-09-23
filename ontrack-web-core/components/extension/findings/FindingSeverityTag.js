import {Tag} from "antd";
import {severityName} from "@components/extension/findings/findingsModel";

const colors = {
    CRITICAL: 'red',
    HIGH: 'volcano',
    MEDIUM: 'gold',
    LOW: 'blue',
    UNKNOWN: 'default',
}

/**
 * Severity of a finding. The colour only doubles the name, it never replaces it.
 */
export default function FindingSeverityTag({severity}) {
    return (
        <Tag color={colors[severity] ?? 'default'} data-testid={`finding-severity-${severity}`}>
            {severityName(severity)}
        </Tag>
    )
}
