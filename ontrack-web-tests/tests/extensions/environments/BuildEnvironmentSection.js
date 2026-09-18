import {expect} from "@playwright/test";
import {DeployDialog} from "./DeployDialog";

export const getBuildEnvironmentSection = async (page, build) => {
    const section = page.getByTestId('environments')
    await expect(section).toBeVisible()
    return new BuildEnvironmentSection(page, section, build)
}

/**
 * The build page's "Environments" cell: the build's journey strip since #1794.
 *
 * One chip per slot of the project, in environment order, each carrying this build's state there.
 * Everything operational - the deployment's details, the slot's history, what it could move to
 * next - is behind the chip, in the shared slot drawer.
 */
export class BuildEnvironmentSection {

    constructor(page, section, build) {
        this.page = page
        this.section = section
        this.build = build
    }

    /**
     * One chip, addressed by its slot.
     *
     * Scoped to the section: a build page carries environment decorations of its own, which are the
     * same chips, and an unscoped test id would match both.
     */
    chip(slot) {
        return this.section.getByTestId(`journey-chip-${slot.id}`)
    }

    /**
     * The build's state in one slot, as the server's enum spells it - `DEPLOYED`, `SUPERSEDED`,
     * `IN_PROGRESS`, `ELIGIBLE`, `NOT_ELIGIBLE`.
     *
     * The state is asserted through `data-state` rather than through the chip's words so that a
     * change of wording is a change of wording rather than a broken test.
     */
    async expectState(slot, state) {
        await expect(this.chip(slot)).toHaveAttribute('data-state', state)
    }

    async expectNoChip(slot) {
        await expect(this.chip(slot)).toHaveCount(0)
    }

    /**
     * Opens the slot drawer from a chip.
     */
    async openDrawer(slot) {
        await this.chip(slot).click()
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
        // "staging · petclinic" since #1793, and both the title and the breadcrumb carry it.
        await expect(
            this.page.getByText(`${environment.name} · ${this.build.branch.project.name}`).first()
        ).toBeVisible()
        await expect(this.page.getByRole('link', {name: this.build.name})).toBeVisible()
    }

    /**
     * Opens the deploy dialog without going through with it, for the tests which are about what the
     * dialog says rather than about what it does.
     */
    async openDeployDialog() {
        // One button for the cell since #1794 - the environment is chosen in the dialog - rather
        // than one per slot row.
        await this.section.getByTestId('build-journey-deploy').click()
        const dialog = new DeployDialog(this.page)
        await dialog.expectOpen()
        return dialog
    }

}
