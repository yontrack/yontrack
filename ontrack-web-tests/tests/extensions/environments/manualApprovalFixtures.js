import {login} from "../../core/login";
import {createSlot} from "./slotFixtures";
import {PipelinePage} from "./PipelinePage";

export const prepareManualApprovalInPipelinePage = async (page, ontrack) => {
    const {project, slot} = await createSlot(ontrack)
    const ruleConfigId = await ontrack.environments.addManualApproval({slot})

    const branch = await project.createBranch()
    const build = await branch.createBuild()

    const pipeline = await slot.createPipeline({build})

    await login(page, ontrack)

    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    return {
        project,
        slot,
        pipeline,
        pipelinePage,
        ruleConfigId,
    }
}

export const manualApprovalInPipelinePage = async (page, ontrack) => {
    const {project, slot, pipeline, pipelinePage, ruleConfigId} = await prepareManualApprovalInPipelinePage(page, ontrack)

    const admissionRule = await pipelinePage.getAdmissionRule(ruleConfigId)
    await admissionRule.expectManualInputButton()
    await admissionRule.checkOverrideRuleButton({visible: true})
    await pipelinePage.expectChecksSummary({passed: 0, total: 1})

    await admissionRule.manualInput({
        actions: async (dialog) => {
            await dialog.getByLabel('Approval', {exact: true}).click()
            await dialog.getByLabel('Approval message').fill("OK for me")
        }
    })

    await admissionRule.expectManualInputButton(false)
    await admissionRule.checkOverrideRuleButton({visible: false})
    await pipelinePage.expectChecksSummary({passed: 1, total: 1})

    return {
        project,
        slot,
        pipeline,
        pipelinePage,
    }
}