import {expect} from "@playwright/test";
import {
    waitForPipelineToBeRunnable,
    waitForPipelineWorkflowToBeFinished,
    withSlotWorkflow
} from "./slotWorkflowsFixtures";
import {createPipeline} from "../pipelineFixtures";
import {graphQLCall} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {login} from "../../../core/login";
import {PipelinePage} from "../PipelinePage";
import {createSlot} from "../slotFixtures";
import {addSlotWorkflow} from "@ontrack/extensions/environments/workflows";
import {gqlPipelineData} from "@ontrack/extensions/environments/environments";
import {waitUntilCondition} from "../../../support/timing";
import {test} from "../../../fixtures/connection";

test('API - workflows on creation participate into the pipeline check list', async ({page, ontrack}) => {
    const {slot, project} = await withSlotWorkflow(ontrack, {trigger: 'CANDIDATE'})
    const {pipeline} = await createPipeline({project, slot})

    // Pipeline was just created, workflow is running into the background
    // The pipeline must be marked as non-deployable
    let data = await graphQLCall(
        ontrack.connection,
        gql`
            query PipelineDeploymentStatus($pipelineId: String!) {
                slotPipelineById(id: $pipelineId) {
                    runAction {
                        ok
                    }
                    slot {
                        workflows {
                            slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
                                workflowInstance {
                                    status
                                }
                            }
                        }
                    }
                }
            }
        `,
        {
            pipelineId: pipeline.id,
        }
    )
    await expect(data.slotPipelineById.runAction.ok).toBe(false)
    let slotWorkflows = data.slotPipelineById.slot.workflows
    await expect(slotWorkflows.length).toBe(1)
    let slotWorkflowInstance = slotWorkflows[0].slotWorkflowInstanceForPipeline
    await expect(slotWorkflowInstance.workflowInstance.status).toBe('RUNNING')

    // Waiting for the deployment to be runnable
    await waitForPipelineToBeRunnable(page, ontrack, pipeline.id)

    // Checking that the pipeline is now deployable
    data = await graphQLCall(
        ontrack.connection,
        gql`
            query PipelineDeploymentStatus($pipelineId: String!) {
                slotPipelineById(id: $pipelineId) {
                    runAction {
                        ok
                    }
                    slot {
                        workflows {
                            slotWorkflowInstanceForPipeline(pipelineId: $pipelineId) {
                                workflowInstance {
                                    status
                                }
                            }
                        }
                    }
                }
            }
        `,
        {
            pipelineId: pipeline.id,
        }
    )
    await expect(data.slotPipelineById.runAction.ok).toBe(true)
    slotWorkflows = data.slotPipelineById.slot.workflows
    await expect(slotWorkflows.length).toBe(1)
    slotWorkflowInstance = slotWorkflows[0].slotWorkflowInstanceForPipeline
    await expect(slotWorkflowInstance.workflowInstance.status).toBe('SUCCESS')
})

test('workflows on creation participate into the pipeline check list', async ({page, ontrack}) => {
    const {slot, project, slotWorkflow} = await withSlotWorkflow(ontrack, {trigger: 'CANDIDATE'})
    const {pipeline} = await createPipeline({project, slot})

    await waitForPipelineToBeRunnable(page, ontrack, pipeline.id)

    await login(page, ontrack)

    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await pipelinePage.expectChecksSummary({passed: 1, total: 1})
    await pipelinePage.checkRunAction({})
    await pipelinePage.expectNoPipelineErrorMessage()

    // Check workflow state in the UI. It passed, so it is behind "1 check passed" - the redesign
    // shows what is blocking first and folds what is not.
    const pipelineWorkflow = await pipelinePage.getWorkflow(slotWorkflow.id, {passed: true})
    await pipelineWorkflow.checkState({name: 'Test', ok: true})
})

test('workflows on creation participate into the pipeline progress', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    // Adding two workflows, one successful, one with error
    const slotWorkflowSuccess = await addSlotWorkflow({
        slot,
        trigger: 'CANDIDATE',
        workflowYaml: `
            name: Success
            nodes:
              - id: test
                executorId: mock
                data:
                    text: Success
                    error: false
        `
    })
    const slotWorkflowError = await addSlotWorkflow({
        slot,
        trigger: 'CANDIDATE',
        workflowYaml: `
            name: Error
            nodes:
              - id: test
                executorId: mock
                data:
                    text: Error
                    error: true
        `
    })
    const {pipeline} = await createPipeline({project, slot})

    await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflowSuccess)
    await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflowError)

    await login(page, ontrack)

    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await pipelinePage.expectChecksSummary({passed: 1, total: 2})
    await pipelinePage.expectPipelineErrorMessage("Workflow is in error")
})

test('API - workflow on promotion leading to the deployment of a build', async ({page, ontrack}) => {
    // Creating a slot
    const {slot, project} = await createSlot(ontrack)

    // Creating a promotion
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel()

    // Creating a subscription on promotions to run a workflow
    await pl.subscribe({
        name: "Deployment on promotion",
        events: ['new_promotion_run'],
        channel: 'workflow',
        channelConfig: {
            workflow: {
                name: "Deployment",
                nodes: [
                    {
                        id: "start",
                        executorId: "slot-pipeline-creation",
                        data: {
                            environment: slot.environment.name,
                        }
                    },
                    {
                        id: "deploying",
                        parents: [
                            {id: "start"}
                        ],
                        executorId: "slot-pipeline-deploying",
                        data: {}
                    },
                    {
                        id: "deployed",
                        parents: [
                            {id: "deploying"}
                        ],
                        executorId: "slot-pipeline-deployed",
                        data: {}
                    },
                ]
            }
        }
    })

    // Creating a build and promoting it
    const build = await branch.createBuild()
    await build.promote(pl)

    let pipeline
    await waitUntilCondition({
        page,
        condition: async () => {
            // Getting the current pipeline of the slot and checking its status
            const data = await graphQLCall(
                ontrack.connection,
                gql`
                    query SlotPipeline($slotId: String!) {
                        slotById(id: $slotId) {
                            currentPipeline {
                                ...PipelineData
                            }
                        }
                    }
                    ${gqlPipelineData}
                `,
                {slotId: slot.id}
            )
            pipeline = data.slotById?.currentPipeline
            return pipeline && pipeline.status === 'DONE'
        },
        message: "Pipeline not created or not deployed"
    })

    await expect(pipeline.number).toStrictEqual(1)

})

test('failing workflows on deploying block the deployment completion', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    // Adding two workflows, one successful, one with error
    const slotWorkflowSuccess = await addSlotWorkflow({
        slot,
        trigger: 'RUNNING',
        workflowYaml: `
            name: Success
            nodes:
              - id: test
                executorId: mock
                data:
                    text: Success
                    error: false
        `
    })
    const slotWorkflowError = await addSlotWorkflow({
        slot,
        trigger: 'RUNNING',
        workflowYaml: `
            name: Error
            nodes:
              - id: test
                executorId: mock
                data:
                    text: Error
                    error: true
        `
    })

    // Creating a pipeline and starting its deployment
    const {pipeline} = await createPipeline({project, slot})
    await ontrack.environments.startPipeline({pipeline})

    await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflowSuccess)
    await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflowError)

    await login(page, ontrack)

    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await pipelinePage.checkFinishAction({disabled: true})
})

test('going to the workflow instance from a pipeline workflow', async ({page, ontrack}) => {
    const {slot, project, slotWorkflow} = await withSlotWorkflow(ontrack, {trigger: 'CANDIDATE'})
    const {pipeline} = await createPipeline({project, slot})

    await waitForPipelineToBeRunnable(page, ontrack, pipeline.id)

    await login(page, ontrack)

    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // It succeeded, so it is folded behind "1 check passed" - the link out to the run is still
    // there, one click away.
    const pipelineWorkflow = await pipelinePage.getWorkflow(slotWorkflow.id, {passed: true})
    const workflowInstancePage = await pipelineWorkflow.goToWorkflowInstance()

    await workflowInstancePage.checkStatus('Success')
})

test('a workflow status can be overridden for a pipeline', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    const slotWorkflowError = await addSlotWorkflow({
        slot,
        trigger: 'CANDIDATE',
        workflowYaml: `
            name: Workflow not working
            nodes:
              - id: test
                executorId: mock
                data:
                    text: Error
                    error: true
        `
    })
    const {pipeline} = await createPipeline({project, slot})
    await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflowError)

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await pipelinePage.expectChecksSummary({passed: 0, total: 1})
    await pipelinePage.checkRunAction({disabled: true})

    const pipelineWorkflow = await pipelinePage.getWorkflow(slotWorkflowError.id)
    await pipelineWorkflow.checkState({name: "Workflow not working", ok: false})

    await pipelineWorkflow.checkWorkflowOverridden({overridden: false})
    await pipelineWorkflow.checkOverrideWorkflowButton({visible: true})

    await pipelineWorkflow.overrideWorkflow({message: "Ignoring the workflow result"})

    // An overridden check passes and is shown anyway: it is the one row whose story matters more
    // than its verdict.
    await pipelineWorkflow.checkWorkflowOverridden({overridden: true})
    await pipelineWorkflow.checkOverrideWorkflowButton({visible: false})

    await pipelinePage.expectChecksSummary({passed: 1, total: 1})
    await pipelinePage.checkRunAction({})

    // ...and, since #1792, the override is in the audit timeline with the reason that was given.
    await pipelinePage.expectTimelineEntry("Ignoring the workflow result")
})