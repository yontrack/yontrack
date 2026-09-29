import {Command} from "@components/common/Commands";
import {FaBell, FaBellSlash} from "react-icons/fa";
import {callGraphQL} from "@components/services/GraphQL";
import {useContext, useState} from "react";
import {EventsContext} from "@components/common/EventsContext";
import {gql} from "graphql-request";

export default function DisableProjectCommand({project}) {

    const eventsContext = useContext(EventsContext)
    const [loading, setLoading] = useState(false)

    const disableProject = async () => {
        setLoading(true)
        try {
            await callGraphQL({
                query: gql`
                    mutation DisableProject($id: Int!) {
                        disableProject(input: {id: $id}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: Number(project.id)},
            })
            eventsContext.fireEvent("project.updated", {id: project.id})
        } finally {
            setLoading(false)
        }
    }

    const enableProject = async () => {
        setLoading(true)
        try {
            await callGraphQL({
                query: gql`
                    mutation EnableProject($id: Int!) {
                        enableProject(input: {id: $id}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: Number(project.id)},
            })
            eventsContext.fireEvent("project.updated", {id: project.id})
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            {
                !project.disabled &&
                <Command
                    disabled={loading}
                    icon={<FaBellSlash/>}
                    text="Disable project"
                    action={disableProject}
                />
            }
            {
                project.disabled &&
                <Command
                    disabled={loading}
                    icon={<FaBell/>}
                    text="Enable project"
                    action={enableProject}
                />
            }
        </>
    )
}