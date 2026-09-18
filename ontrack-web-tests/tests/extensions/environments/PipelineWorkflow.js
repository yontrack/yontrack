import {expect} from "@playwright/test";
import {WorkflowInstancePage} from "../workflows/WorkflowInstancePage";

/**
 * One slot workflow's row in "What's blocking".
 *
 * A workflow which passed is collapsed behind "N checks passed" like any other passed check, so
 * [expandPassed] is what a test looking at a green one calls first.
 */
export class PipelineWorkflow {

    constructor(page, pipeline, slotWorkflowId, prefix = 'whats-blocking') {
        this.page = page
        this.pipeline = pipeline
        this.slotWorkflowId = slotWorkflowId
        this.prefix = prefix
    }

    locatePipelineWorkflow() {
        return this.page.getByTestId(`${this.prefix}-workflow-${this.slotWorkflowId}`);
    }

    /**
     * Opens the "N checks passed" section of this phase, where a workflow goes once it passes.
     *
     * The header is found by its own text rather than by a test id on the `Collapse`: Ant Design
     * decides for itself which of its wrappers an unknown prop lands on, and the anchored regular
     * expression below cannot match the phase heading, which reads "Candidate phase (1 of 1 checks
     * passed)".
     */
    async expandPassed() {
        const section = this.page.getByTestId(this.prefix)
        const header = section.getByText(/^\d+ checks? passed$/)
        await expect(header).toBeVisible()
        if (!(await this.locatePipelineWorkflow().isVisible())) {
            await header.click()
        }
        await expect(this.locatePipelineWorkflow()).toBeVisible()
    }

    async expectToBeVisible() {
        await expect(this.locatePipelineWorkflow()).toBeVisible()
    }

    /**
     * The row names the workflow, and its verdict is the check icon beside it.
     *
     * The old page drew the workflow *instance's* status word ("Success", "Not started"); the
     * redesign says Passed or Blocking, in the mobile screen's vocabulary, because "Not started"
     * and "Error" are two ways of saying the same thing to somebody asking why nothing is moving.
     */
    async checkState({name, ok}) {
        const locator = this.locatePipelineWorkflow()
        if (name) {
            await expect(
                locator.getByTestId(`${this.prefix}-workflow-link-${this.slotWorkflowId}`)
            ).toContainText(name)
        }
        if (ok !== undefined) {
            const suffix = ok ? 'ok' : 'nok'
            await expect(
                locator.getByTestId(`${this.prefix}-workflow-${this.slotWorkflowId}-${suffix}`)
            ).toBeVisible()
        }
    }

    async checkWorkflowOverridden({overridden = true}) {
        const detail = this.page.getByTestId(
            `${this.prefix}-override-detail-workflow-${this.slotWorkflowId}`
        )
        await expect(detail).toBeVisible({visible: overridden})
    }

    locatorOverrideWorkflowButton() {
        return this.page.getByTestId(`${this.prefix}-override-${this.slotWorkflowId}`)
    }

    async checkOverrideWorkflowButton({visible = true}) {
        await expect(this.locatorOverrideWorkflowButton()).toBeVisible({visible})
    }

    async overrideWorkflow({message}) {
        await this.locatorOverrideWorkflowButton().click()
        const messageInput = this.page.getByLabel("Message", {exact: true})
        await expect(messageInput).toBeVisible()
        await messageInput.fill(message)
        await this.page.getByRole("button", {name: "OK"}).click()
    }

    async goToWorkflowInstance() {
        const link = this.locatePipelineWorkflow()
            .getByTestId(`${this.prefix}-workflow-link-${this.slotWorkflowId}`)
        const workflowInstanceId = await link.getAttribute('data-workflow-instance-id')
        await expect(workflowInstanceId).toBeTruthy()
        await expect(link).toBeVisible()
        await link.click()
        const workflowInstancePage = new WorkflowInstancePage(this.page, workflowInstanceId)
        await workflowInstancePage.expectToBeVisible()
        return workflowInstancePage
    }

}
