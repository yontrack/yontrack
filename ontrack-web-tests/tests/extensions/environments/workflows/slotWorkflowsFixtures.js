import {createSlot} from "../slotFixtures";
import {addSlotWorkflow} from "@ontrack/extensions/environments/workflows";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {waitUntilCondition} from "../../../support/timing";

export const triggerMapping = {
    CANDIDATE: "On candidate",
    RUNNING: "On running",
    DONE: "On deployment done",
}

export const withSlotWorkflow = async (ontrack, {trigger}) => {
    const {slot, project} = await createSlot(ontrack)
    const slotWorkflow = await addSlotWorkflow({
        slot,
        trigger,
        workflowYaml: `
            name: Test
            nodes:
              - id: start
                executorId: mock
                data:
                    text: Start
                    waitMs: 2000
              - id: end
                parents:
                  - id: start
                executorId: mock
                data:
                    text: End
        `
    })
    return {slot, project, slotWorkflow}
}

export const waitForPipelineWorkflowToBeFinished = async (page, ontrack, pipelineId, slotWorkflow) => {
    await waitUntilCondition({
        page,
        condition: async () => {
            const data = await graphQLCall(
                ontrack.connection,
                gql`
                    query PipelineDeploymentStatus($pipelineId: String!) {
                        slotPipelineById(id: $pipelineId) {
                            slotWorkflowInstances {
                                slotWorkflow {
                                    id
                                }
                                workflowInstance {
                                    finished
                                }
                            }
                        }
                    }
                `,
                {
                    pipelineId: pipelineId,
                }
            )
            // Looking for the instance for the slot workflow
            const slotWorkflowInstance = data.slotPipelineById?.slotWorkflowInstances?.find(it =>
                it.slotWorkflow.id === slotWorkflow.id
            )
            // Condition is that the workflow instance is finished to run
            return slotWorkflowInstance?.workflowInstance?.finished
        },
        message: `Pipeline workflow for ${slotWorkflow.workflow.name} not finished in 5 seconds`
    })
}

export const waitForPipelineToBeRunnable = async (page, ontrack, pipelineId) => {
    await waitUntilCondition({
        page,
        condition: async () => {
            const data = await graphQLCall(
                ontrack.connection,
                gql`
                    query PipelineDeploymentStatus($pipelineId: String!) {
                        slotPipelineById(id: $pipelineId) {
                            runAction {
                                ok
                            }
                        }
                    }
                `,
                {
                    pipelineId: pipelineId,
                }
            )
            return (data.slotPipelineById.runAction.ok === true)
        },
        message: 'Pipeline not ready for deployment within 5 seconds'
    })
}

/**
 * Overrides the result of a slot workflow for one pipeline, through the API.
 *
 * The desktop UI has a button for this and `slotWorkflows.spec.js` drives it; a spec whose subject
 * is something else - the mobile deployment screen showing that a workflow *was* overridden - wants
 * the state and not the journey.
 */
export const overridePipelineWorkflow = async (ontrack, {pipelineId, slotWorkflowId, message}) => {
    await graphQLCallMutation(
        ontrack.connection,
        'overridePipelineWorkflow',
        gql`
            mutation OverridePipelineWorkflow(
                $pipelineId: String!,
                $slotWorkflowId: String!,
                $message: String!,
            ) {
                overridePipelineWorkflow(input: {
                    pipelineId: $pipelineId,
                    slotWorkflowId: $slotWorkflowId,
                    message: $message,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {pipelineId, slotWorkflowId, message},
    )
}
