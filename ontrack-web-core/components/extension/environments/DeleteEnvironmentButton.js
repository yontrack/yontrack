import {UserContext} from "@components/providers/UserProvider";
import {useContext, useState} from "react";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {EventsContext} from "@components/common/EventsContext";

export default function DeleteEnvironmentButton({environment}) {

    const eventsContext = useContext(EventsContext)
    const user = useContext(UserContext)
    const client = useGraphQLClient()

    const [loading, setLoading] = useState(false)

    const deleteEnvironment = () => {
        setLoading(true)
        client.request(
            gql`
                mutation DeleteEnvironment(
                    $id: String!,
                ) {
                    deleteEnvironment(input: {
                        id: $id
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {
                id: environment.id,
            }
        ).then(() => {
            eventsContext.fireEvent("environment.deleted", {id: environment.id})
        }).finally(() => {
            setLoading(false)
        })
    }

    return (
        <>
            {
                /*
                 * `environment.delete`, not `environment.create` (#1793). The two are different
                 * global functions - `EnvironmentDelete` and `EnvironmentSave` - so gating a
                 * deletion on the right to create was offering it to somebody the backend would
                 * then refuse, and hiding it from somebody allowed to do it.
                 */
                user.authorizations.environment?.delete &&
                <>
                    <InlineConfirmCommand
                        title="Environment deletion"
                        confirm={`Do you really want to delete the ${environment.name}? This will remove all data associated with it.`}
                        onConfirm={deleteEnvironment}
                        loading={loading}
                    />
                </>
            }
        </>
    )
}