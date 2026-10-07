import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Input} from "antd";
import {gql} from "graphql-request";
import {useContext} from "react";
import {UserContext} from "@components/providers/UserProvider";
import {AGENT_SLUG_PATTERN} from "@components/core/admin/agents/agentsModel";
import {AgentDetailsFormItems} from "@components/core/admin/agents/agentFormItems";
import SelectHumanAccount from "@components/core/admin/agents/SelectHumanAccount";

export const useRegisterAgentDialog = ({onSuccess}) => {
    return useFormDialog({
        init: (form) => {
            form.resetFields()
        },
        prepareValues: (values) => ({
            ...values,
            owner: values.owner || null,
            description: values.description || null,
        }),
        query: gql`
            mutation RegisterAgent(
                $slug: String!,
                $displayName: String!,
                $tool: String!,
                $description: String,
                $owner: String,
            ) {
                registerAgent(input: {
                    slug: $slug,
                    displayName: $displayName,
                    tool: $tool,
                    description: $description,
                    owner: $owner,
                }) {
                    errors {
                        message
                    }
                    agent {
                        id
                    }
                }
            }
        `,
        userNode: 'registerAgent',
        onSuccess,
    })
}

export default function RegisterAgentDialog({dialog}) {
    const user = useContext(UserContext)
    const admin = user?.authorizations?.accounts?.config
    return (
        <FormDialog dialog={dialog} id="register-agent" okText="Register">
            <Form.Item
                name="slug"
                label="Slug"
                extra="Identifies the agent as <slug>[agent], and never changes: 1 to 32 lowercase letters, digits or dashes."
                rules={[
                    {required: true, message: "The slug is required."},
                    {
                        pattern: AGENT_SLUG_PATTERN,
                        message: "Use 1 to 32 lowercase letters, digits or dashes.",
                    },
                ]}
            >
                <Input placeholder="claude-code-ci"/>
            </Form.Item>
            <AgentDetailsFormItems/>
            {
                admin &&
                <Form.Item
                    name="owner"
                    label="Owner"
                    extra="The person accountable for the agent. Yourself when not set."
                >
                    <SelectHumanAccount allowClear={true} placeholder="Yourself"/>
                </Form.Item>
            }
        </FormDialog>
    )
}
