import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";

export const useProject = ({id}) => {
    const {data, loading, finished} = useQuery(
        gql`
            query Project($id: Int!) {
                project(id: $id) {
                    ...ProjectContent
                }
            }
            ${gqlProjectContentFragment}
        `,
        {
            variables: {id},
            dataFn: data => data.project,
        }
    )
    return {
        project: data,
        loading: loading || !finished,
    }
}
