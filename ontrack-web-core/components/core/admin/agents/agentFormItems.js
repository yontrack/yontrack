import {Form, Input} from "antd";
import AgentToolInput from "@components/core/admin/agents/AgentToolInput";

/**
 * Form items common to the registration and to the edition of an agent.
 */
export function AgentDetailsFormItems() {
    return (
        <>
            <Form.Item
                name="displayName"
                label="Display name"
                rules={[
                    {required: true, message: "The display name is required."},
                    {max: 100, message: "The display name has at most 100 characters."},
                ]}
            >
                <Input/>
            </Form.Item>
            <Form.Item
                name="tool"
                label="Tool"
                extra="The tool behind the agent. Pick one, or type another name."
                rules={[
                    {required: true, message: "The tool is required."},
                    {max: 40, message: "The tool has at most 40 characters."},
                ]}
            >
                <AgentToolInput/>
            </Form.Item>
            <Form.Item
                name="description"
                label="Description"
                rules={[
                    {max: 500, message: "The description has at most 500 characters."},
                ]}
            >
                <Input.TextArea rows={3}/>
            </Form.Item>
        </>
    )
}
