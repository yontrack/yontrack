import {callGraphQL} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useDeleteSubscription = () => {

    const deleteSubscription = async ({entity, name}) => {
        await callGraphQL({
            query: gql`
                mutation DeleteSubscription(
                    $id: String!,
                    $projectEntity: ProjectEntityIDInput,
                ) {
                    deleteSubscription(input: {
                        id: $id,
                        projectEntity: $projectEntity,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {
                id: name,
                projectEntity: entity,
            },
        })
    }

    return {
        deleteSubscription,
    }
}