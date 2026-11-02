import {Form, Radio, Tag} from "antd";

/**
 * Choice of the access of a token being generated. It never changes afterwards.
 */
export function TokenAccessFormItem() {
    return (
        <Form.Item
            name="readOnly"
            label="Access"
            initialValue={false}
        >
            <Radio.Group
                data-testid="token-access"
                options={[
                    {value: false, label: "Full"},
                    {value: true, label: "Read-only"},
                ]}
            />
        </Form.Item>
    )
}

/**
 * Marker of a read-only token in a list of tokens.
 */
export function TokenReadOnlyMarker({token}) {
    return token.readOnly ? <Tag data-testid={`token-read-only-${token.name}`}>Read-only</Tag> : null
}
