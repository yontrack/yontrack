import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, Input, InputNumber} from "antd";

export default function DeliveryScorecardForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="windowDays"
                    label="Window (days)"
                    extra="Number of days a reading is taken over, back from its computation. An estate may override it per reading."
                    rules={[{required: true}]}
                >
                    <InputNumber min={1} max={3650}/>
                </Form.Item>
                <Form.Item
                    name="retentionDays"
                    label="Retention (days)"
                    extra="Number of days the daily snapshots of the readings are kept."
                    rules={[{required: true}]}
                >
                    <InputNumber min={1} max={36500}/>
                </Form.Item>
                <Form.Item
                    name="cron"
                    label="Schedule"
                    extra="Cron schedule of the daily computation of the readings (seconds, minutes, hours, day of month, month, day of week), in the time zone of the server. For example, 0 0 2 * * * for every day at 02:00."
                    rules={[{required: true}]}
                >
                    <Input/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
