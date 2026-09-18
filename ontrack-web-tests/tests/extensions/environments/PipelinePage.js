import {expect} from "@playwright/test";
import {PipelineRule} from "./PipelineRule";
import {PipelineWorkflow} from "./PipelineWorkflow";

/**
 * The deployment page, as redesigned by #1792.
 *
 * The page is now a header with a `Steps` bar and **one** primary action, a "What's blocking" list
 * for the current phase, the phases already over below it, and an audit timeline down the side.
 * This object speaks that vocabulary; the specs that used the old `List` of steps were rewritten
 * against it rather than being given a compatibility layer, because a page object that pretends a
 * screen still looks the way it used to is how a suite stops describing the product.
 *
 * Two locator rules the phase-1 and phase-2 work paid for, worth repeating here:
 *
 * - every row of "What's blocking" carries `whats-blocking-<key>`, where the key is `rule-<configId>`
 *   or `workflow-<slotWorkflowId>`; a phase shown as history carries the same keys under a
 *   `deployment-phase-<PHASE>-` prefix, which is what `phase` below selects;
 * - Ant Design puts an unknown prop on the underlying control for `Input` and `Checkbox` and on a
 *   wrapper for `Segmented` and `Select`, so a test id is never assumed to be on a wrapper.
 */
export class PipelinePage {
    /**
     * @param page The Playwright page
     * @param pipeline The deployment, as the fixtures return it
     * @param ontrack The connection
     * @param phase Which rendering of the checks to address: the current phase (the default) or a
     *   phase shown as history, named by its status - `CANDIDATE`, `RUNNING` or `DONE`.
     */
    constructor(page, pipeline, ontrack, {phase = null} = {}) {
        this.page = page
        this.pipeline = pipeline
        this.ontrack = ontrack
        this.prefix = phase ? `deployment-phase-${phase}` : 'whats-blocking'
    }

    /**
     * The same page, addressed through one of the phases it shows as history.
     */
    forPhase(phase) {
        return new PipelinePage(this.page, this.pipeline, this.ontrack, {phase})
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/environments/pipeline/${this.pipeline.id}`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        // The breadcrumb still names the slot, and the title now names both.
        await expect(this.page.getByText(`Slot ${this.pipeline.slot.environment.name} - ${this.pipeline.slot.project.name}`)).toBeVisible()
        await expect(this.page.getByText(`Deployment #${this.pipeline.number}`)).toBeVisible()
    }

    /**
     * Opens the phase which is shown as history, so its checks are readable.
     *
     * A finished deployment has them open already; a running one keeps them collapsed, which is the
     * point of them.
     */
    async openPhase(phase) {
        const label = this.page.getByTestId(`deployment-phase-label-${phase}`)
        await expect(label).toBeVisible()
        const body = this.page.getByTestId(`deployment-phase-${phase}`)
        if (!(await body.isVisible())) {
            await label.click()
        }
        await expect(body).toBeVisible()
        return this.forPhase(phase)
    }

    /**
     * @param ruleConfigId The admission rule configuration
     * @param passed Whether the rule is expected to be PASSING, in which case it sits behind the
     *   "N checks passed" section and the section is opened first.
     */
    async getAdmissionRule(ruleConfigId, {passed = false} = {}) {
        const rule = new PipelineRule(this.page, this.pipeline, ruleConfigId, this.prefix);
        if (passed) {
            await rule.expandPassed()
        } else {
            await rule.expectToBeVisible()
        }
        return rule
    }

    /**
     * Gets a workflow row given the ID of the slot workflow.
     *
     * @param passed As above: a workflow which passed is one click away, not on the list.
     */
    async getWorkflow(slotWorkflowId, {passed = false} = {}) {
        const workflow = new PipelineWorkflow(this.page, this.pipeline, slotWorkflowId, this.prefix);
        if (passed) {
            await workflow.expandPassed()
        } else {
            await workflow.expectToBeVisible()
        }
        return workflow
    }

    /**
     * "N of M checks passed" - the summary line of the current phase, in the mobile screen's own
     * words. It replaces the progress bar the old page drew beside the Run button: the same two
     * numbers, said rather than drawn.
     */
    async expectChecksSummary({passed, total}) {
        const summary = this.page.getByTestId(`${this.prefix}-summary`)
        await expect(summary).toBeVisible()
        await expect(summary).toHaveText(`${passed} of ${total} checks passed`)
    }

    /**
     * Which step of the bar the deployment is on, and which steps the bar has at all.
     */
    async expectStep(status, {present = true} = {}) {
        await expect(this.page.getByTestId(`deployment-step-${status}`)).toBeVisible({visible: present})
    }

    async expectPipelineErrorMessage(message) {
        const error = this.page.getByTestId('deployment-error')
        await expect(error).toBeVisible()
        await expect(error).toContainText(message)
    }

    async expectNoPipelineErrorMessage() {
        await expect(this.page.getByTestId('deployment-error')).not.toBeVisible()
    }

    locatorRunAction() {
        return this.page.getByTestId('deployment-start')
    }

    async checkRunAction({visible = true, disabled = false}) {
        const locator = this.locatorRunAction()
        await expect(locator).toBeVisible({visible})
        if (visible) {
            if (disabled) {
                await expect(locator).toBeDisabled()
            } else {
                await expect(locator).toBeEnabled()
            }
        }
    }

    async running() {
        await this.locatorRunAction().click()
        // One primary action, and it becomes the next one: Start gives way to Finish.
        await expect(this.locatorFinishAction()).toBeVisible()
    }

    locatorFinishAction() {
        return this.page.getByTestId('deployment-finish')
    }

    async checkFinishAction({visible = true, disabled = false}) {
        const locator = this.locatorFinishAction()
        await expect(locator).toBeVisible({visible})
        if (visible) {
            if (disabled) {
                await expect(locator).toBeDisabled()
            } else {
                await expect(locator).toBeEnabled()
            }
        }
    }

    async finish() {
        await this.locatorFinishAction().click()
        // A finished deployment offers nothing at all - which is the signal, since the `Deployed`
        // step is drawn on the bar from the start.
        await expect(this.page.getByTestId('deployment-actions')).not.toBeVisible()
    }

    async checkCancelAction({visible = true}) {
        await expect(this.page.getByTestId('deployment-cancel')).toBeVisible({visible})
    }

    /**
     * A line of the audit timeline, found by the text it carries. The timeline is the only place
     * the page shows an override's message, which is what makes it worth asserting on by content.
     */
    async expectTimelineEntry(text) {
        const timeline = this.page.getByTestId('deployment-timeline')
        await expect(timeline).toBeVisible()
        await expect(timeline.getByText(text, {exact: false}).first()).toBeVisible()
    }

    async expectNoTimelineEntry(text) {
        const timeline = this.page.getByTestId('deployment-timeline')
        await expect(timeline.getByText(text, {exact: false})).toHaveCount(0)
    }

    async expectCommand(name, {visible = true} = {}) {
        await expect(this.page.getByRole('button', {name})).toBeVisible({visible})
    }

    async forceDone({message}) {
        const forceCommand = this.page.getByRole('button', {name: "Force deployment"})
        await expect(forceCommand).toBeVisible()
        await forceCommand.click()

        const forceMessage = this.page.getByLabel('Message')
        await expect(forceMessage).toBeVisible()
        await forceMessage.fill(message)
        const okButton = this.page.getByRole('button', {name: 'OK'});
        await okButton.click()
    }
}
