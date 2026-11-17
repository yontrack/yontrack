import {Form, Input} from "antd";
import {prefixedFormName} from "@components/form/formUtils";

export default function HeaderHttpAuthenticatorForm({prefix, creation}) {
    return (
        <>
            <Form.Item
                name={prefixedFormName(prefix, 'name')}
                label="Name"
                extra="Name of the header to send"
                rules={[{required: true, message: 'Name is required.'}]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name={prefixedFormName(prefix, 'value')}
                label="Value"
                extra={creation ? "Value of the header to send" : "Value of the header to send. When editing, leave it blank to keep the current one."}
                rules={[{required: creation, message: 'Value is required.'}]}
            >
                <Input.Password/>
            </Form.Item>
        </>
    )
}
