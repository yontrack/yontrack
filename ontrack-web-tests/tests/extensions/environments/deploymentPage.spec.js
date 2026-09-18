import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {createSlot} from "./slotFixtures";
import {createPipeline} from "./pipelineFixtures";
import {PipelinePage} from "./PipelinePage";

/**
 * The deployment page, redesigned by #1792 - phase 3 of the environments UI.
 *
 * What is worth a browser here is what the component tests cannot reach: that the one query the
 * page sends is accepted by the real schema, that the steps bar follows a deployment as the server
 * actually moves it, that the primary action unblocks when the blocking check is answered, and that
 * an override's justification comes back from the server into the audit timeline. The states of the
 * header, the phases and the timeline are covered far more cheaply by their unit tests.
 *
 * The "header commands hidden without the right" case of the issue is **not** here: this suite has
 * no way to be somebody other than the admin it logs in as, and inventing one for a single
 * assertion would be a bigger change than the page. The hiding itself is a `DeploymentHeader` unit
 * test (`hides every action from a user who may not act`) and, for the commands, the unchanged
 * `isAuthorized` guards inside `ForceDeploymentCommand` and `DeleteDeploymentCommand`.
 */

test('the steps bar follows the deployment from candidate to deployed', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    const {pipeline} = await createPipeline({project, slot})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // Three steps, and nothing is blocking this slot, so the way forward is offered.
    await pipelinePage.expectStep('CANDIDATE')
    await pipelinePage.expectStep('RUNNING')
    await pipelinePage.expectStep('DONE')
    await pipelinePage.expectStep('CANCELLED', {present: false})

    await pipelinePage.running()
    await pipelinePage.finish()

    // Finished: no action at all, not disabled ones.
    await pipelinePage.checkCancelAction({visible: false})
})

test('a cancelled deployment shows Cancelled in place of the step it never reached', async ({page, ontrack}) => {
    const {slot, project} = await createSlot(ontrack)
    const {pipeline} = await createPipeline({project, slot})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    await page.getByTestId('deployment-cancel').click()

    await pipelinePage.expectStep('CANCELLED')
    // It was cancelled as a candidate, so "Deployed" is not a step it skipped - it is a step it
    // will never take, and drawing it would promise one.
    await pipelinePage.expectStep('DONE', {present: false})
    await pipelinePage.checkCancelAction({visible: false})
    await pipelinePage.expectTimelineEntry('Cancelled')
})

test('the primary action unblocks when the blocking check is answered', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const ruleConfigId = await ontrack.environments.addManualApproval({slot})

    const branch = await project.createBranch()
    const build = await branch.createBuild()
    const pipeline = await slot.createPipeline({build})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // Blocked: one check, not passing, and the fix is on the row itself.
    await pipelinePage.expectChecksSummary({passed: 0, total: 1})
    await pipelinePage.checkRunAction({disabled: true})

    const rule = await pipelinePage.getAdmissionRule(ruleConfigId)
    await rule.expectToBeUnchecked()
    await rule.manualInput({
        actions: async (dialog) => {
            await dialog.getByLabel('Approval', {exact: true}).click()
            await dialog.getByLabel('Approval message').fill("OK for me")
        }
    })

    // ...and the whole point of the page: answering it there moves the deployment on.
    await pipelinePage.expectChecksSummary({passed: 1, total: 1})
    await pipelinePage.checkRunAction({disabled: false})

    // The answer is an audit fact too, and the timeline is where the page keeps those.
    await pipelinePage.expectTimelineEntry('Rule data changed')
})

test('the candidate phase becomes read-only history once the deployment is running', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const ruleConfigId = await ontrack.environments.addPromotionRule({slot, promotion: "GOLD"})

    const branch = await project.createBranch()
    const gold = await branch.createPromotionLevel("GOLD")
    const build = await branch.createBuild()
    await build.promote(gold)
    const pipeline = await slot.createPipeline({build})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // As a candidate, the promotion rule is the current phase and there is no history below it.
    await pipelinePage.expectChecksSummary({passed: 1, total: 1})
    await expect(page.getByTestId('deployment-phase-label-CANDIDATE')).toHaveCount(0)

    await pipelinePage.running()

    // Once running, it moves below - collapsed, and with no Override on it.
    const candidatePhase = await pipelinePage.openPhase('CANDIDATE')
    const rule = await candidatePhase.getAdmissionRule(ruleConfigId, {passed: true})
    await rule.expectToBeChecked()
    await rule.checkOverrideRuleButton({visible: false})
})
