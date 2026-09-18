import {expect} from "@playwright/test";
import {PipelineInputDialog} from "./PipelineInputDialog";

/**
 * One admission rule's row in "What's blocking".
 *
 * Passed rules are **collapsed** behind "N checks passed" rather than dropped (#1797), so a test
 * looking at a rule which has just started passing opens that section first - see [expandPassed].
 * A blocking or overridden rule is always drawn, which is the whole point of the list.
 */
export class PipelineRule {

    constructor(page, pipeline, ruleConfigId, prefix = 'whats-blocking') {
        this.page = page
        this.pipeline = pipeline
        this.ruleConfigId = ruleConfigId
        this.prefix = prefix
    }

    locatePipelineRule() {
        return this.page.getByTestId(`${this.prefix}-rule-${this.ruleConfigId}`);
    }

    /**
     * Opens the "N checks passed" section of this phase, where a rule goes once it passes.
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
        if (!(await this.locatePipelineRule().isVisible())) {
            await header.click()
        }
        await expect(this.locatePipelineRule()).toBeVisible()
    }

    async expectToBeVisible() {
        await expect(this.locatePipelineRule()).toBeVisible()
    }

    async expectToBeUnchecked() {
        const locator = this.locatePipelineRule()
        await expect(locator.getByTestId(`${this.prefix}-rule-${this.ruleConfigId}-nok`)).toBeVisible()
    }

    async expectToBeChecked() {
        const locator = this.locatePipelineRule()
        await expect(locator.getByTestId(`${this.prefix}-rule-${this.ruleConfigId}-ok`)).toBeVisible()
    }

    /**
     * The inline fix on a rule which is waiting for somebody: "Answer".
     */
    locatorManualInputButton() {
        return this.page.getByTestId(`${this.prefix}-answer-${this.ruleConfigId}`)
    }

    async expectManualInputButton(present = true) {
        await expect(this.locatorManualInputButton()).toBeVisible({visible: present})
    }

    async manualInput({actions}) {
        await this.locatorManualInputButton().click()
        const pipelineInputDialog = new PipelineInputDialog(this.page)
        await pipelineInputDialog.manualInput({actions})
    }

    /**
     * The override, once taken, shows who took it and why - on the row and, since #1792, in the
     * audit timeline as well.
     */
    async checkRuleOverridden({overridden = true}) {
        const detail = this.page.getByTestId(`${this.prefix}-override-detail-rule-${this.ruleConfigId}`)
        await expect(detail).toBeVisible({visible: overridden})
    }

    locatorOverrideRuleButton() {
        return this.page.getByTestId(`${this.prefix}-override-${this.ruleConfigId}`)
    }

    async checkOverrideRuleButton({visible = true}) {
        await expect(this.locatorOverrideRuleButton()).toBeVisible({visible})
    }

    async overrideRule({message}) {
        await this.locatorOverrideRuleButton().click()
        const messageInput = this.page.getByLabel("Message", {exact: true})
        await expect(messageInput).toBeVisible()
        await messageInput.fill(message)
        await this.page.getByRole("button", {name: "OK"}).click()
    }
}
