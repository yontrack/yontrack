import {expect} from "@playwright/test";

/**
 * The deploy dialog - the one way to start a deployment (#1797).
 *
 * It replaced four entry points which each had their own UI and their own confirmation, so this page
 * object is shared by the build page's environments cell, the build user menu and the slot page's
 * eligible builds. Whichever of them opened it, the dialog is the same list of choices.
 */
export class DeployDialog {

    constructor(page) {
        this.page = page
        this.dialog = page.getByRole('dialog')
    }

    async expectOpen() {
        await expect(this.dialog).toBeVisible()
    }

    /**
     * The dialog opened from a build offers one row per slot of the build's project.
     */
    slotRow(slot) {
        return this.page.getByTestId(`deploy-dialog-slot-${slot.id}`)
    }

    async expectSlotOffered(slot) {
        await expect(this.page.getByTestId(`deploy-dialog-slot-start-${slot.id}`)).toBeVisible()
    }

    async expectSlotRefused(slot) {
        // Listed, not hidden: a user is never left wondering where an environment went.
        await expect(this.page.getByTestId(`deploy-dialog-slot-ineligible-${slot.id}`)).toBeVisible()
        await expect(this.page.getByTestId(`deploy-dialog-slot-start-${slot.id}`)).toHaveCount(0)
    }

    async expectSlotRefusalReason(slot, reason) {
        await expect(this.page.getByTestId(`deploy-dialog-slot-reasons-${slot.id}`)).toContainText(reason)
    }

    /**
     * The warning that starting this deployment cancels the slot's active one - which is what
     * `SlotServiceImpl.startPipeline` has always done, silently.
     */
    async expectCancellationWarning(slot, {number, buildName, status}) {
        await expect(this.page.getByTestId(`deploy-dialog-slot-cancels-${slot.id}`))
            .toContainText(`Deployment #${number} (build ${buildName}, ${status}) will be cancelled.`)
    }

    async expectNoCancellationWarning(slot) {
        await expect(this.page.getByTestId(`deploy-dialog-slot-cancels-${slot.id}`)).toHaveCount(0)
    }

    async deployToSlot(slot) {
        await this.page.getByTestId(`deploy-dialog-slot-start-${slot.id}`).click()
    }

    /**
     * The dialog opened from a slot offers one row per candidate build.
     */
    async expectBuildOffered(build) {
        await expect(this.page.getByTestId(`deploy-dialog-build-start-${build.id}`)).toBeVisible()
    }

    async deployBuild(build) {
        await this.page.getByTestId(`deploy-dialog-build-start-${build.id}`).click()
    }

    async cancel() {
        await this.page.getByTestId('deploy-dialog-cancel').click()
        await expect(this.dialog).toHaveCount(0)
    }
}
