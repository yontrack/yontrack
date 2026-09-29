import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useTriggers = () => {

    const {data, loading, finished} = useQuery(
        gql`
            query TriggerList {
                triggerList {
                    id
                    displayName
                }
            }
        `,
        {
            dataFn: data => data.triggerList,
        }
    )

    return {
        data,
        loading: loading || !finished,
    }
}