import {callGraphQL} from "@components/services/GraphQL";
import {useContext, useState} from "react";
import {EventsContext} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import {Command} from "@components/common/Commands";
import {FaBell, FaBellSlash} from "react-icons/fa";

export default function DisableBranchCommand({branch}) {

    const eventsContext = useContext(EventsContext)
    const [loading, setLoading] = useState(false)

    const disableBranch = async () => {
        setLoading(true)
        try {
            await callGraphQL({
                query: gql`
                    mutation DisableBranch($id: Int!) {
                        disableBranch(input: {id: $id}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: Number(branch.id)},
            })
            eventsContext.fireEvent("branch.updated", {id: Number(branch.id)})
        } finally {
            setLoading(false)
        }
    }

    const enableBranch = async () => {
        setLoading(true)
        try {
            await callGraphQL({
                query: gql`
                    mutation EnableBranch($id: Int!) {
                        enableBranch(input: {id: $id}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: Number(branch.id)},
            })
            eventsContext.fireEvent("branch.updated", {id: Number(branch.id)})
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            {
                !branch.disabled &&
                <Command
                    disabled={loading}
                    icon={<FaBellSlash/>}
                    text="Disable branch"
                    action={disableBranch}
                />
            }
            {
                branch.disabled &&
                <Command
                    disabled={loading}
                    icon={<FaBell/>}
                    text="Enable branch"
                    action={enableBranch}
                />
            }
        </>
    )
}