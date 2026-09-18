import {expect} from "@playwright/test";
import {DeployDialog} from "./DeployDialog";

export const getBuildEnvironmentSection = async (page, build) => {
    const section = page.getByTestId('environments')
    await expect(section).toBeVisible()
    return new BuildEnvironmentSection(page, section, build)
}

export class BuildEnvironmentSection {

    constructor(page, section, build) {
        this.page = page
        this.section = section
        this.build = build
    }

    async expectBuildDeployButton({environment}) {
        await expect(this.section.getByRole('link', {name: environment.name})).toBeVisible()
        await expect(this.section.getByRole('button', {name: this.build.name})).toBeVisible()
    }

    /**
     * Starts a deployment from the build page's environments cell.
     *
     * Since #1797 the cell's button opens the shared deploy dialog rather than a `Popconfirm`: the
     * choice of slot is made in the dialog, which is also where the cancellation warning and the
     * refusal reasons live.
     */
    async buildDeploy({environment, slot}) {
        await this.section.getByRole('button', {name: this.build.name}).click()
        const dialog = new DeployDialog(this.page)
        await dialog.expectOpen()
        await dialog.deployToSlot(slot)
        // The dialog closes and the page behind it reloads the cell.
        await expect(this.page.getByRole('dialog')).toHaveCount(0)
        await expect(this.section.getByRole('link', {name: environment.name})).toBeVisible()
    }

    /**
     * Opens the deploy dialog without going through with it, for the tests which are about what the
     * dialog says rather than about what it does.
     */
    async openDeployDialog() {
        await this.section.getByRole('button', {name: this.build.name}).click()
        const dialog = new DeployDialog(this.page)
        await dialog.expectOpen()
        return dialog
    }

}
