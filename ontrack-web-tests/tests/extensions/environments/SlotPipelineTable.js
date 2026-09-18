import {expect} from "@playwright/test";

/**
 * The slot page's **Deployments** tab - the slot's full history.
 *
 * It carries no action buttons since #1793: the tab is the archive, the header block above it holds
 * the one action on the current deployment, and everything else about a deployment is on the
 * deployment page. The rows are addressed by the deployment's id, which the table sets as the
 * `data-row-key` of each.
 */
export class SlotPipelineTable {

    constructor(page, slot) {
        this.page = page
        this.slot = slot
    }

    locateTable() {
        return this.page.getByTestId(`slot-pipelines-${this.slot.id}`);
    }

    async expectToBeVisible() {
        await expect(this.locateTable()).toBeVisible()
    }

    locateRow(pipelineId) {
        return this.locateTable().locator(`tr[data-row-key="${pipelineId}"]`);
    }

    async expectRow(pipeline, {status} = {}) {
        const row = this.locateRow(pipeline.id)
        await expect(row).toBeVisible()
        if (status) await expect(row).toContainText(status)
    }

    async expectNoRow(pipeline) {
        await expect(this.locateRow(pipeline.id)).toHaveCount(0)
    }
}
