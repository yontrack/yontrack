import {useContext, useEffect, useState} from "react";
import {EventsContext} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import Favourite from "@components/common/Favourite";
import {callGraphQL} from "@components/services/GraphQL";

export default function BranchFavourite({branch}) {

    const eventsContext = useContext(EventsContext)
    const [favourite, setFavourite] = useState(branch.favourite)

    useEffect(() => {
        setFavourite(branch.favourite)
    }, [branch])

    const toggleFavourite = async () => {
        if (favourite) {
            return callGraphQL({
                query: gql`
                    mutation UnsetFavourite(
                        $branchId: Int!,
                    ) {
                        unfavouriteBranch(input: {
                            id: $branchId,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {branchId: Number(branch.id)},
            }).then(() => {
                setFavourite(false)
                eventsContext.fireEvent("branch.favourite", {id: branch.id, value: false})
            })
        } else {
            return callGraphQL({
                query: gql`
                    mutation SetFavourite(
                        $branchId: Int!,
                    ) {
                        favouriteBranch(input: {
                            id: $branchId,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {branchId: Number(branch.id)},
            }).then(() => {
                setFavourite(true)
                eventsContext.fireEvent("branch.favourite", {id: branch.id, value: true})
            })
        }
    }

    return (
        <>
            <Favourite
                value={favourite}
                onToggle={toggleFavourite}
            />
        </>
    )
}