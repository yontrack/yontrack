import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber} from "antd";

export default function EventsForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="retentionDays"
                    label="Retention (days)"
                    extra="Number of days the events are kept. A daily job deletes the older ones, and this cannot be undone. 0 keeps the events forever."
                    rules={[{required: true}]}
                >
                    <InputNumber min={0}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
