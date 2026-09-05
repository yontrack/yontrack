import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";

export const usePromotionLevel = (project, branch, promotionLevel) => {
    const client = useGraphQLClient()

    const [promotionLevelObject, setPromotionLevelObject] = useState({})

    useEffect(() => {
        if (client && project && branch && promotionLevel) {
            client.request(
                gql`
                    query GetPromotionLevelByName(
                        $project: String!,
                        $branch: String!,
                        $promotionLevel: String!,
                    ) {
                        promotionLevelByName(project: $project, branch: $branch, name: $promotionLevel) {
                            id
                            name
                            description
                            image
                            branch {
                                id
                                name
                                displayName
                                project {
                                    id
                                    name
                                }
                            }
                        }
                    }
                `,
                {
                    project, branch, promotionLevel
                }
            ).then(data => {
                setPromotionLevelObject(data.promotionLevelByName)
            })
        }
    }, [client, project, branch, promotionLevel]);

    return promotionLevelObject
}