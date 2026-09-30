import {Tag} from "antd";
import {findingSeverityColor, severityName} from "@components/extension/findings/findingsModel";

/**
 * Severity of a finding. The colour only doubles the name, it never replaces it.
 */
export default function FindingSeverityTag({severity}) {
    return (
        <Tag color={findingSeverityColor(severity).preset} data-testid={`finding-severity-${severity}`}>
            {severityName(severity)}
        </Tag>
    )
}
