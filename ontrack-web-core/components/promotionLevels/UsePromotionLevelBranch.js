import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

/**
 * Gets the branch a promotion level belongs to.
 *
 * Note the `Number` coercion: `PromotionLevel.id` is exposed as a GraphQL `ID!`, and is therefore
 * carried around the UI as a string, while the `promotionLevel(id:)` query expects an `Int!`.
 */
export const usePromotionLevelBranch = ({promotionLevelId}) => {
    const {data: branch, loading, error, finished} = useQuery(
        gql`
            query PromotionLevelBranch(
                $promotionLevelId: Int!
            ) {
                promotionLevel(id: $promotionLevelId) {
                    branch {
                        id
                        name
                        project {
                            id
                            name
                        }
                    }
                }
            }
        `,
        {
            variables: {
                promotionLevelId: Number(promotionLevelId),
            },
            deps: [promotionLevelId],
            condition: !!promotionLevelId,
            dataFn: data => data.promotionLevel?.branch,
        }
    )
    // `useQuery` reports `loading` as false until its effect runs: without `finished`, the caller
    // would briefly render as if the branch were known to be absent.
    return {branch, loading: loading || !finished, error}
}
