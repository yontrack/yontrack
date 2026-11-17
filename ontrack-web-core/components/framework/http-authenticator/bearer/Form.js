import {Form, Input} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function BearerHttpAuthenticatorForm({prefix, creation}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'token')}
                label="Token"
                extra={creation ? "Token used to authenticate" : "Token used to authenticate. When editing, leave it blank to keep the current one."}
                rules={[{required: creation, message: 'Token is required.'}]}
            >
                <Input.Password/>
            </Form.Item>
        </>
    )
}
