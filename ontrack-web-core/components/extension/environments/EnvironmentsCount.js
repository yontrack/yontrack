import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import LoadingInline from "@components/common/LoadingInline";

export default function EnvironmentsCount() {
    const {data: count, loading, finished} = useQuery(
        gql`
            query EnvironmentCount {
                environmentsCount
            }
        `,
        {
            initialData: 0,
            dataFn: data => data.environmentsCount,
        }
    )
    return <LoadingInline loading={loading || !finished}>{count}</LoadingInline>
}