import {useQuery} from "@components/services/GraphQL";
import LoadingInline from "@components/common/LoadingInline";
import {gql} from "graphql-request";
import WorkflowInstanceStatus from "@components/extension/workflows/WorkflowInstanceStatus";

export default function ShowWorkflowInstanceStatus({instanceId}) {

    const {data: status, loading, finished} = useQuery(
        gql`
            query GetWorkflowInstanceStatus($instanceId: String!) {
                workflowInstance(id: $instanceId) {
                    status
                }
            }
        `,
        {
            variables: {instanceId},
            deps: [instanceId],
            initialData: '',
            dataFn: data => data.workflowInstance?.status,
        }
    )

    return (
        <>
            <LoadingInline loading={loading || !finished} text="">
                <WorkflowInstanceStatus status={status ?? ''}/>
            </LoadingInline>
        </>
    )
}
