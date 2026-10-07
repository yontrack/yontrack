import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {gql} from "graphql-request";
import {AgentDetailsFormItems} from "@components/core/admin/agents/agentFormItems";

export const useEditAgentDialog = ({onSuccess}) => {
    return useFormDialog({
        init: (form, {agent}) => {
            form.setFieldsValue({
                displayName: agent.fullName,
                tool: agent.agentTool,
                description: agent.agentDescription,
            })
        },
        prepareValues: (values, {agent}) => ({
            ...values,
            id: Number(agent.id),
            description: values.description || null,
        }),
        query: gql`
            mutation UpdateAgent(
                $id: Int!,
                $displayName: String!,
                $tool: String!,
                $description: String,
            ) {
                updateAgent(input: {
                    id: $id,
                    displayName: $displayName,
                    tool: $tool,
                    description: $description,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'updateAgent',
        onSuccess,
    })
}

export default function EditAgentDialog({dialog}) {
    return (
        <FormDialog dialog={dialog} id="edit-agent">
            <AgentDetailsFormItems/>
        </FormDialog>
    )
}
