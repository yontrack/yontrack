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

    async selectTab(name) {
        await this.dialog.getByRole('tab', {name}).click()
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
