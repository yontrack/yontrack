import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber, Switch} from "antd";
import DurationPicker from "@components/common/DurationPicker";

export default function AutoVersioningForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="enabled"
                    label="Enabled"
                    extra="Check to enable auto-versioning in Ontrack."
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    name="auditRetentionDuration"
                    label="Audit retention duration"
                    extra="Maximum duration to keep audit entries for active auto-versioning requests"
                >
                    <DurationPicker/>
                </Form.Item>
                <Form.Item
                    name="auditCleanupDuration"
                    label="Audit cleanup duration"
                    extra="Maximum duration to keep audit entries for all kinds of auto-versioning requests (counted after the audit retention)"
                >
                    <DurationPicker/>
                </Form.Item>
                <Form.Item
                    name="buildLinks"
                    label="Build links"
                    extra="Check to enable the creation of build links on auto-versioning."
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    name="retryMaxCount"
                    label="Automatic retries"
                    extra="Maximum number of times an order failing on a transient error (like GitHub being momentarily unavailable) is rescheduled automatically. 0 disables the automatic retries."
                >
                    <InputNumber min={0} max={10}/>
                </Form.Item>
                <Form.Item
                    name="retryDelayMinutes"
                    label="Automatic retry delay"
                    extra="Delay (in minutes) before an automatic retry is scheduled. The retry actually starts on the next run of the auto-versioning scheduler."
                >
                    <InputNumber min={1} max={1440}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}