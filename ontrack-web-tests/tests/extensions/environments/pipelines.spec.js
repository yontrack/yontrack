import {expect} from "@playwright/test";
import {manualApprovalInPipelinePage} from "./manualApprovalFixtures";
import {createSlot} from "./slotFixtures";
import {createPipeline} from "./pipelineFixtures";
import {PipelinePage} from "./PipelinePage";
import {login} from "../../core/login";
import {SlotPage} from "./SlotPage";
import {addSlotWorkflow} from "@ontrack/extensions/environments/workflows";
import {test} from "../../fixtures/connection";

test('pipeline status refreshed when inputs completed', async ({page, ontrack}) => {
    await manualApprovalInPipelinePage(page, ontrack)
})

test('pipeline marked as running after manual approval', async ({page, ontrack}) => {
    const {pipelinePage} = await manualApprovalInPipelinePage(page, ontrack)
    // Pipeline is now ready to be set in "deploying" mode
    await pipelinePage.checkRunAction({disabled: false})
    await pipelinePage.running()
    await pipelinePage.checkRunAction({visible: false})
    // Pipeline can now be deployed
    await pipelinePage.checkFinishAction({disabled: false})
    await pipelinePage.finish()
    await pipelinePage.checkFinishAction({visible: false})
})

test('pipeline lifecycle', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    const {pipeline} = await createPipeline({project, slot})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await pipelinePage.checkRunAction({})
    await pipelinePage.running()
    await pipelinePage.checkRunAction({visible: false})

    await pipelinePage.checkFinishAction({})
    await pipelinePage.finish()
    await pipelinePage.checkFinishAction({visible: false})
})

test('pipeline lifecycle from the slot page', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    const {pipeline} = await createPipeline({project, slot})

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    const slotPipelineTable = await slotPage.getSlotPipelineTable()
    const slotPipelineRow = await slotPipelineTable.getSlotPipelineRow(pipeline.id)

    await slotPipelineRow.checkRunAction({})
    await slotPipelineRow.running()
    await slotPipelineRow.checkRunAction({visible: false})

    await slotPipelineRow.checkFinishAction({})
    await slotPipelineRow.finish()
    await slotPipelineRow.checkFinishAction({visible: false})

})

test('starting a pipeline in forced DONE', async ({page, ontrack}) => {
    const {
        slot,
        project,
        promotionRuleId,
        candidateWorkflow,
        doneWorkflow,
        runningWorkflow
    } = await preparePipelineForForcedDeployment(ontrack)

    // Creating a pipeline in DONE state
    const {pipeline} = await createPipeline({
        project,
        slot,
        forceDone: true,
        forceDoneMessage: "Created pipeline as done",
        branchSetup: async (branch) => {
            await branch.createPromotionLevel("BRONZE")
        }
    })

    await expect(pipeline).not.toBeNull()

    // Going to the pipeline page
    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // Checks the pipeline is marked as DONE
    await checkForcedPipeline({
        pipelinePage,
        promotionRuleId,
        candidateWorkflow,
        runningWorkflow,
        doneWorkflow,
        expectedMessage: "Created pipeline as done",
    })
})

test('forcing a pipeline in forced DONE', async ({page, ontrack}) => {
    const {
        slot,
        project,
        promotionRuleId,
        doneWorkflow,
        runningWorkflow
    } = await preparePipelineForForcedDeployment(ontrack)

    // Creating a pipeline in normal state
    const {pipeline} = await createPipeline({
        project,
        slot,
        branchSetup: async (branch) => {
            await branch.createPromotionLevel("BRONZE")
        }
    })

    await expect(pipeline).not.toBeNull()

    // Going to the pipeline page
    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // Forcing the pipeline as DONE
    await pipelinePage.forceDone({
        message: "Deployment was done by other means"
    })

    // Checks the pipeline is marked as DONE
    await checkForcedPipeline({
        pipelinePage,
        promotionRuleId,
        candidateWorkflow: null, // The candidate workflow may have been run or not depending on the timing
        runningWorkflow,
        doneWorkflow,
        expectedMessage: "Deployment was done by other means",
    })
})

const preparePipelineForForcedDeployment = async (ontrack) => {
    const {slot, project} = await createSlot(ontrack)

    const promotionRuleId = await ontrack.environments.addPromotionRule({slot, promotion: "BRONZE"})

    const candidateWorkflow = await addSlotWorkflow({
        slot,
        trigger: 'CANDIDATE',
        workflowYaml: `
             name: On candidate
             nodes:
               - id: start
                 executorId: mock
                 data:
                   text: Candidate
        `
    })

    const runningWorkflow = await addSlotWorkflow({
        slot,
        trigger: 'RUNNING',
        workflowYaml: `
             name: On running
             nodes:
               - id: start
                 executorId: mock
                 data:
                   text: Running
        `
    })

    const doneWorkflow = await addSlotWorkflow({
        slot,
        trigger: 'DONE',
        workflowYaml: `
             name: On done
             nodes:
               - id: start
                 executorId: mock
                 data:
                   text: Done
        `
    })

    return {
        slot,
        project,
        promotionRuleId,
        candidateWorkflow,
        runningWorkflow,
        doneWorkflow,
    }
}

const checkForcedPipeline = async ({
                                       pipelinePage,
                                       promotionRuleId,
                                       candidateWorkflow,
                                       runningWorkflow,
                                       doneWorkflow,
                                       expectedMessage,
                                   }) => {

    // Pipeline is done: no action at all, not merely disabled ones.
    await pipelinePage.checkRunAction({visible: false})
    await pipelinePage.checkFinishAction({visible: false})
    await pipelinePage.checkCancelAction({visible: false})
    await pipelinePage.expectStep('DONE')

    // A finished deployment shows every phase, expanded and read-only, rather than the one it is
    // "in" - it is in none. Each phase is addressed through its own section.

    const candidatePhase = await pipelinePage.openPhase('CANDIDATE')
    const promotionRule = await candidatePhase.getAdmissionRule(promotionRuleId)
    await promotionRule.expectToBeUnchecked()

    if (candidateWorkflow) {
        const candidateWorkflowInstance = await candidatePhase.getWorkflow(candidateWorkflow.id)
        await candidateWorkflowInstance.checkState({name: "On candidate", ok: false})
    }

    // The RUNNING phase is shown although a forced deployment was never running: its workflows did
    // not run, and that is part of what forcing left behind.
    const runningPhase = await pipelinePage.openPhase('RUNNING')
    const runningWorkflowInstance = await runningPhase.getWorkflow(runningWorkflow.id)
    await runningWorkflowInstance.checkState({name: "On running", ok: false})

    const donePhase = await pipelinePage.openPhase('DONE')
    const doneWorkflowInstance = await donePhase.getWorkflow(doneWorkflow.id, {passed: true})
    await doneWorkflowInstance.checkState({name: "On done", ok: true})

    // The forcing message is in the audit timeline, which is where every override's justification
    // now lives (#1792).

    await pipelinePage.expectTimelineEntry(expectedMessage)

}
