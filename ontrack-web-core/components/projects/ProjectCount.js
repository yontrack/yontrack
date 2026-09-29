import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import LoadingInline from "@components/common/LoadingInline";

export default function ProjectCount() {
    const {data: count, loading, finished} = useQuery(
        gql`
            query ProjectCount {
                entityCounts {
                    projects
                }
            }
        `,
        {
            initialData: 0,
            dataFn: data => data.entityCounts.projects,
        }
    )
    return <LoadingInline loading={loading || !finished}>{count}</LoadingInline>
}