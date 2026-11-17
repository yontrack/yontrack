import {Form, Input} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function BasicHttpAuthenticatorForm({prefix, creation}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'username')}
                label="Username"
                extra="Username used to authenticate"
                rules={[{required: true, message: 'Username is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'password')}
                label="Password"
                extra={creation ? "Password used to authenticate" : "Password used to authenticate. When editing, leave it blank to keep the current one."}
                rules={[{required: creation, message: 'Password is required.'}]}
            >
                <Input.Password/>
            </Form.Item>
        </>
    )
}
