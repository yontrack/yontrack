import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useWorkflowInstanceStatus = () => {

    const {data, loading, finished} = useQuery(
        gql`
            query WorkflowInstanceStatusQuery {
                workflowInstanceStatusList
            }
        `,
        {
            initialData: [],
            dataFn: data => data.workflowInstanceStatusList,
        }
    )

    return {
        data,
        loading: loading || !finished,
    }

}