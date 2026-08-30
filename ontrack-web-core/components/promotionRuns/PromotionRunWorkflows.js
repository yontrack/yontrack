import {useState} from "react";
import {gql} from "graphql-request";
import {Alert, Empty, Space} from "antd";
import PageSection from "@components/common/PageSection";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";
import {useQuery} from "@components/services/GraphQL";
import WorkflowInstanceCard from "@components/extension/workflows/WorkflowInstanceCard";

/**
 * The primary content of the promotion run page: the workflows which the promotion has triggered.
 *
 * The link between the promotion run and its workflows is resolved server-side by the
 * `workflowInstances` field — there is no direct model link, it goes through the notification
 * records of the `workflow` channel.
 */
export default function PromotionRunWorkflows({promotionRunId}) {

    const [refreshCount, setRefreshCount] = useState(0)

    const {data, error, finished} = useQuery(
        gql`
            query PromotionRunWorkflows($id: Int!) {
                promotionRuns(id: $id) {
                    workflowInstances {
                        id
                        status
                        durationMs
                        workflow {
                            name
                            nodes {
                                id
                                executorId
                                parents {
                                    id
                                }
                            }
                        }
                        nodesExecutions {
                            id
                            status
                            error
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(promotionRunId)},
            deps: [promotionRunId, refreshCount],
            initialData: [],
            dataFn: data => data.promotionRuns[0]?.workflowInstances ?? [],
        }
    )

    // `useQuery` nulls its data when the query fails, so the list is defaulted here rather than
    // relying on `initialData` alone.
    const instances = data ?? []

    // `finished` only ever goes from false to true, so the section renders a skeleton until the
    // first fetch resolves — which keeps the empty state from flashing — and then updates in place
    // on every auto refresh instead of flashing a skeleton again.
    const loading = !finished

    return (
        <AutoRefreshContextProvider onRefresh={() => setRefreshCount(count => count + 1)}>
            <PageSection
                id="promotion-run-workflows"
                title={loading || error ? "Workflows" : `Workflows (${instances.length})`}
                loading={loading}
                padding={true}
                extra={<AutoRefreshButton size="small"/>}
            >
                {
                    /*
                     * A failed query must never be shown as an empty state: "no workflow ran" and
                     * "we could not find out" are different answers, and on a promotion which did
                     * trigger a workflow the empty state would be plainly wrong.
                     */
                    error ?
                        <Alert
                            type="error"
                            showIcon
                            data-testid="promotion-run-workflows-error"
                            message="The workflows of this promotion could not be loaded."
                            description={error}
                        /> :
                        instances.length > 0 ?
                            <Space direction="vertical" className="ot-line">
                                {
                                    instances.map(instance =>
                                        <WorkflowInstanceCard key={instance.id} instance={instance}/>
                                    )
                                }
                            </Space> :
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="No workflow was triggered by this promotion."
                            />
                }
            </PageSection>
        </AutoRefreshContextProvider>
    )
}
