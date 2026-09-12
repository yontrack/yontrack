import {graphQLCall} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {waitUntilCondition} from "./timing";

/**
 * Subscribing a promotion level to a workflow, and waiting for the run it sets off.
 *
 * Three specs now need a promotion that fires a workflow - the promotion run page, the branch
 * delivery map, and the mobile UI - and each of the first two had grown its own private copy of
 * the subscription. They differ only in the shape of the workflow, which is a parameter rather
 * than a reason for a third copy.
 */

/**
 * Nodes fanning out from a single root, so that a run has two depth columns and a join.
 *
 * That shape is what makes the run worth drawing at all: concurrency, fan-out and ordering are
 * all readable from it, on the desktop's node strip and in the mobile UI's depth-ordered list
 * alike. `failing` makes the `publish` node fail, which is how a spec gets a node error to read.
 */
export const fanOutWorkflowNodes = ({failing = false} = {}) => ([
    {
        id: "build",
        executorId: "mock",
        data: {text: "Building"},
    },
    {
        id: "test-unit",
        parents: [{id: "build"}],
        executorId: "mock",
        data: {text: "Unit tests"},
    },
    {
        id: "publish",
        parents: [{id: "build"}],
        executorId: "mock",
        data: {text: "Publishing", error: failing},
    },
])

/**
 * Subscribes a promotion level to a workflow launched on every promotion.
 *
 * @param promotionLevel The promotion level to subscribe.
 * @param name The workflow's name, which is what a UI draws.
 * @param failing Whether the fan-out's `publish` node fails. Ignored when `nodes` is given.
 * @param nodes The workflow's nodes, for a spec whose subject is not the shape of the graph.
 */
export const subscribeToWorkflow = async (promotionLevel, {name, failing = false, nodes = undefined}) => {
    await promotionLevel.subscribe({
        name: `Subscription ${name}`,
        events: ['new_promotion_run'],
        channel: 'workflow',
        channelConfig: {
            workflow: {
                name,
                nodes: nodes ?? fanOutWorkflowNodes({failing}),
            },
        },
    })
}

/**
 * Waits for the workflow a promotion set off to have finished, and answers its instance id.
 *
 * The link between a promotion run and its workflows goes through the notification record the
 * run leaves behind, and both the notification and the workflow are asynchronous - so a browser
 * opened straight after the promotion can legitimately find nothing. The mobile screens ask the
 * server once and do not poll, which makes waiting here the difference between a flake and a
 * test.
 */
export const waitForPromotionRunWorkflow = async (page, ontrack, promotionRun) => {
    let instanceId = null
    await waitUntilCondition({
        page,
        condition: async () => {
            const data = await graphQLCall(
                ontrack.connection,
                gql`
                    query PromotionRunWorkflows($id: Int!) {
                        promotionRuns(id: $id) {
                            workflowInstances {
                                id
                                finished
                            }
                        }
                    }
                `,
                {id: Number(promotionRun.id)},
            )
            const instance = data.promotionRuns?.[0]?.workflowInstances?.[0]
            if (instance?.finished) {
                instanceId = instance.id
                return true
            }
            return false
        },
        message: `No finished workflow for promotion run ${promotionRun.id} within 5 seconds`,
    })
    return instanceId
}
