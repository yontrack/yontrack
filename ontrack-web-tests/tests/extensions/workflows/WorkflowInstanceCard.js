import {expect} from "@playwright/test";
import {WorkflowInstancePage} from "./WorkflowInstancePage";

/**
 * One workflow card, as rendered in the Workflows section of an entity page.
 */
export class WorkflowInstanceCard {

    constructor(page, card) {
        this.page = page
        this.card = card
    }

    /**
     * Checks that the node strip carries a chip for each of the given node ids.
     */
    async checkNodes(nodeIds) {
        for (const nodeId of nodeIds) {
            await expect(this.card.getByTestId(`workflow-node-chip-${nodeId}`)).toBeVisible()
        }
    }

    async checkNodeStatus(nodeId, status) {
        await expect(this.card.getByTestId(`workflow-node-chip-${nodeId}`))
            .toHaveAttribute('data-status', status, {timeout: 30_000})
    }

    /**
     * Checks that the error block is displayed, names the given node and contains the given text.
     */
    async checkNodeError(nodeId, text) {
        const error = this.card.getByTestId('workflow-node-error')
        await expect(error).toBeVisible({timeout: 30_000})
        await expect(error).toContainText(nodeId)
        if (text) {
            await expect(error).toContainText(text)
        }
    }

    /**
     * Clicks "Open workflow" and returns the page object of the workflow instance page.
     */
    async openWorkflow() {
        const link = this.card.getByRole('link', {name: /Open workflow/})
        await expect(link).toBeVisible()
        const href = await link.getAttribute('href')
        const instanceId = href.substring(href.lastIndexOf('/') + 1)
        await link.click()
        const instancePage = new WorkflowInstancePage(this.page, instanceId)
        await instancePage.expectToBeVisible()
        return instancePage
    }
}
