import {Typography} from "antd";

/**
 * The counts of a security scan go with its findings, which only the `validateBuildWithFindings`
 * mutation posts. A run created by hand carries no data: its status must be chosen.
 */
export default function FindingsValidationDataType() {
    return (
        <Typography.Text type="secondary">
            Security findings are posted by the scans, through the <Typography.Text
            code>validateBuildWithFindings</Typography.Text> mutation. Choose the status of this run below.
        </Typography.Text>
    )
}
