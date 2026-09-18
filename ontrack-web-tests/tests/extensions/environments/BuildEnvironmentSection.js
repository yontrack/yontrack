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
        const dialog = await this.openDeployDialog()
        await dialog.deployToSlot(slot)
        // On success the cell takes the user to the new deployment, as it always has.
        await expect(this.page.getByText(`Slot ${environment.name} - ${this.build.branch.project.name}`)).toBeVisible()
        await expect(this.page.getByRole('link', {name: this.build.name})).toBeVisible()
    }

    /**
     * Opens the deploy dialog without going through with it, for the tests which are about what the
     * dialog says rather than about what it does.
     */
    async openDeployDialog() {
        // The collapsed row's own button, named after the build - `BuildSlotInfo`, not the expanded
        // deployment panel underneath it.
        await this.section.getByRole('button', {name: this.build.name}).first().click()
        const dialog = new DeployDialog(this.page)
        await dialog.expectOpen()
        return dialog
    }

}
