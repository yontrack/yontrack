import {Form} from "antd";
import SelectHttpAuthenticator from "@components/extension/notifications/http/SelectHttpAuthenticator";
import Well from "@components/common/Well";
import {Dynamic} from "@components/common/Dynamic";

/**
 * Authentication of an HTTP configuration: its type, and the form of its configuration,
 * shared with the webhooks (`framework/http-authenticator/{type}/Form.js`).
 *
 * The secrets are never required: on an update, a blank one keeps the current one.
 */
export default function HttpAuthenticationFormItems() {
    const form = Form.useFormInstance()
    const type = Form.useWatch(['authentication', 'type'])

    // The configuration of the previous type does not apply to the new one
    const onTypeChange = () => {
        form.setFieldValue(['authentication', 'config'], {})
    }

    return (
        <>
            <Form.Item
                name={['authentication', 'type']}
                label="Authentication type"
                extra="How the calls made under this configuration are authenticated"
                initialValue="none"
                rules={[{required: true, message: 'Authentication type is required.'}]}
            >
                <SelectHttpAuthenticator onTypeChange={onTypeChange}/>
            </Form.Item>
            {
                type &&
                <Form.Item label="Authentication configuration">
                    <Well>
                        <Dynamic
                            path={`framework/http-authenticator/${type}/Form.js`}
                            props={{prefix: ['authentication', 'config'], creation: false}}
                        />
                    </Well>
                </Form.Item>
            }
        </>
    )
}
