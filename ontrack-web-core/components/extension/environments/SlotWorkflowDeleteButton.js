import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {callGraphQL} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useState} from "react";

export default function SlotWorkflowDeleteButton({slot, slotWorkflow, onChange}) {

    const [loading, setLoading] = useState(false)
    const deleteWorkflow = async () => {
        setLoading(true)
        try {
            await callGraphQL({
                query: gql`
                    mutation DeleteSlotWorkflow($slotWorkflowId: String!) {
                        deleteSlotWorkflow(input: {id: $slotWorkflowId}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {slotWorkflowId: slotWorkflow.id},
            })
            if (onChange) onChange()
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <InlineConfirmCommand
                title="Delete this workflow"
                confirm="Do you really want to delete this workflow?"
                onConfirm={deleteWorkflow}
                loading={loading}
            />
        </>
    )
}