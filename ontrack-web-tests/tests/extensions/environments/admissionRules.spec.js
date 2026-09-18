import {expect} from "@playwright/test";
import {login} from "../../core/login";
import {generate} from "@ontrack/utils";
import {createSlot} from "./slotFixtures";
import {SlotPage} from "./SlotPage";
import {PipelinePage} from "./PipelinePage";
import {test} from "../../fixtures/connection";

test('adding a branch pattern admission rule with default name', async ({page, ontrack}) => {
    // Provisioning
    const {slot} = await createSlot(ontrack)

    // Login
    await login(page, ontrack)
    // Going to the slot page
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    // Adding an admission rule, which lives in the Setup tab since #1793
    const description = generate("rule-")
    await slotPage.addAdmissionRule({
        rule: "Branch pattern",
        name: "branchPattern",
        description: description,
        config: async () => {
            const includes = page.getByLabel('Includes regular expressions');
            await includes.click()
            await includes.fill('release-.*')
            await includes.press('Enter')
        }
    })
})

test('adding a manual approval admission rule with default name', async ({page, ontrack}) => {
    // Provisioning
    const {slot} = await createSlot(ontrack)

    // Login
    await login(page, ontrack)
    // Going to the slot page
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    // Adding an admission rule
    const description = generate("rule-")
    await slotPage.addAdmissionRule({
        rule: "Manual approval",
        name: "manual",
        description: description,
        config: async () => {
            await page.getByLabel('Message').fill("Approval message")
        }
    })
})

/**
 * Editing a rule in place, which did not exist before #1793.
 *
 * It matters beyond convenience: deleting a rule and adding it back gives it a *new id*, and a
 * deployment's stored answers to a rule are keyed on that id - so the old "delete and re-add" way
 * quietly detached every approval already given to it.
 */
test('editing an existing admission rule', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const ruleConfigId = await ontrack.environments.addPromotionRule({slot, promotion: "BRONZE"})

    const branch = await project.createBranch()
    await branch.createPromotionLevel("BRONZE")
    const silver = await branch.createPromotionLevel("SILVER")
    const build = await branch.createBuild()
    await build.promote(silver)

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    await slotPage.expectAdmissionRuleEditable(ruleConfigId)

    const description = generate("edited-")
    await slotPage.editAdmissionRule(ruleConfigId, {
        description,
        config: async () => {
            // The promotion rule's field is labelled "Promotion name", not "Promotion".
            const promotion = page.getByLabel('Promotion name', {exact: true})
            await promotion.fill('SILVER')
        },
    })

    // The rule kept its id - it was edited, not replaced - and carries the new configuration.
    await slotPage.goToSetup()
    await expect(page.getByTestId(`slot-rule-${ruleConfigId}`)).toBeVisible()
    await expect(page.getByTestId(`slot-rule-${ruleConfigId}`)).toContainText(description)
    await expect(page.getByTestId(`slot-rule-${ruleConfigId}`)).toContainText('SILVER')
})

/**
 * The manual approval's `users` and `groups`, which the backend has always accepted and the form
 * never exposed (#1793).
 *
 * Checked by a round trip through the *form* rather than through the API: the point of the change is
 * that the restriction can now be set and read back where an administrator actually works.
 */
test('a manual approval can be restricted to a group', async ({page, ontrack}) => {
    const {slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    const description = generate("rule-")
    const group = generate("group-")
    await slotPage.addAdmissionRule({
        rule: "Manual approval",
        name: "manual",
        description: description,
        config: async () => {
            await page.getByLabel('Message').fill("Approval message")
            const groups = page.getByTestId('manual-groups')
            await groups.click()
            await page.keyboard.type(group)
            await page.keyboard.press('Enter')
        }
    })

    // Reopening the rule shows the restriction it was saved with.
    const ruleConfigId = await ontrack.environments.findAdmissionRuleId({slot, ruleId: 'manual'})
    await slotPage.goToSetup()
    await page.getByTestId(`slot-rule-edit-${ruleConfigId}`).click()
    await expect(page.getByTestId('manual-groups')).toContainText(group)
})

/**
 * What the manual approval's `Check` component now says.
 *
 * It used to read "Deployment must be approved manually" whatever had happened to it - a restatement
 * of the rule's own summary, drawn beside it anyway. Who approved and what they wrote were in the
 * deployment's stored rule data all along and reached no screen.
 */
test('the manual approval says who approved and what they wrote', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const ruleConfigId = await ontrack.environments.addManualApproval({slot})

    const branch = await project.createBranch()
    const build = await branch.createBuild()
    const pipeline = await slot.createPipeline({build})

    await login(page, ontrack)
    const pipelinePage = new PipelinePage(page, pipeline, ontrack)
    await pipelinePage.goTo()

    // While it is waiting, the row says who is being waited on rather than only that it is blocking.
    const blocking = await pipelinePage.getAdmissionRule(ruleConfigId)
    await blocking.expectApprovalDetail('Waiting for a manual approval')

    await blocking.manualInput({
        actions: async (dialog) => {
            await dialog.getByLabel('Approval', {exact: true}).click()
            await dialog.getByLabel('Approval message').fill("OK for me")
        }
    })

    // It passes now, so it sits behind "1 check passed" - and says who decided.
    const approved = await pipelinePage.getAdmissionRule(ruleConfigId, {passed: true})
    await approved.expectApprovalDetail('Approved by')
    await approved.expectApprovalDetail('OK for me')
})
