const {expect} = require("@playwright/test");

/**
 * Dialog used to create or edit a build filter on a branch.
 */
class BuildFilterDialog {
    constructor(page) {
        this.page = page
        this.dialog = page.getByRole('dialog')
        this.displayName = this.dialog.getByLabel("With display name")
    }

    async waitFor() {
        await expect(this.dialog).toBeVisible()
    }

    async checkBuildTabSelected() {
        await expect(
            this.dialog.getByRole('tab', {name: "Build"})
        ).toHaveAttribute('aria-selected', 'true')
    }

    async setDisplayName(regex) {
        await this.displayName.fill(regex)
    }

    async checkDisplayNameError(message) {
        await expect(this.dialog.getByText(message)).toBeVisible()
    }

    /**
     * Selects a tab, and confirms that it is selected: a click can be lost. The tabs overflow the
     * dialog, so Playwright scrolls them to reach a tab - and when the opening modal has made it
     * retry, it centers the tab rather than just revealing it. Taking the focus on mouse down makes
     * the tabs reset that scrolling, the tab leaves the mouse, and the mouse up lands on another
     * tab: no click on this one. The tabs are settled by then, so the next click lands.
     */
    async selectTab(name) {
        const tab = this.dialog.getByRole('tab', {name})
        await expect(async () => {
            await tab.click()
            await expect(tab).toHaveAttribute('aria-selected', 'true', {timeout: 1000})
        }).toPass({timeout: 30000})
    }

    /**
     * Selects an option of a select of the dialog, by its exact label.
     */
    async selectOption(fieldLabel, optionLabel) {
        await this.dialog.getByLabel(fieldLabel, {exact: true}).click()
        await this.page
            .locator('.ant-select-dropdown:visible .ant-select-item-option')
            .filter({hasText: new RegExp(`^${optionLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`)})
            .click()
    }

    /**
     * Whether the build was assisted, in the Agents tab: "Assisted", "Not assisted" or "Unknown".
     */
    async selectAssisted(label) {
        await this.selectOption("Assisted", label)
    }

    /**
     * Actor who created the build, in the Agents tab: "Humans", "Agents", or the label of one agent.
     */
    async selectActor(label) {
        await this.selectOption("Created by", label)
    }

    async ok() {
        await this.dialog.getByRole('button', {name: "OK"}).click()
    }
}

module.exports = {BuildFilterDialog}
