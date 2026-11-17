import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber} from "antd";

const positive = [{required: true, type: 'integer', min: 1, message: 'A strictly positive integer is required.'}]

export default function HttpNotificationsForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="maxTimeoutSeconds"
                    label="Maximum timeout (s)"
                    extra="Maximum timeout (in seconds) a subscription may set on the wait of an HTTP call for its conditions: a longer one is capped to it"
                    rules={positive}
                >
                    <InputNumber min={1} precision={0}/>
                </Form.Item>
                <Form.Item
                    name="maxResponseBytes"
                    label="Maximum response size (bytes)"
                    extra="Maximum size (in bytes) of a response body: a longer body is truncated"
                    rules={positive}
                >
                    <InputNumber min={1} precision={0}/>
                </Form.Item>
                <Form.Item
                    name="requestTimeoutSeconds"
                    label="Request timeout (s)"
                    extra="Timeout (in seconds) of a single request, for both the connection and the reading of the response"
                    rules={positive}
                >
                    <InputNumber min={1} precision={0}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
