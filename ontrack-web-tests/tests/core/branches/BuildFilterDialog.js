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

    async ok() {
        await this.dialog.getByRole('button', {name: "OK"}).click()
    }
}

module.exports = {BuildFilterDialog}
