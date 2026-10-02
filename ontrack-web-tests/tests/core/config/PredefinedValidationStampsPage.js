import {expect} from "@playwright/test";

export class PredefinedValidationStampsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/core/config/predefined-validation-stamps`)
        await expect(this.page.getByText("Predefined validation stamps", {exact: true})).toBeVisible()
    }

    /**
     * Opens the update dialog of a predefined validation stamp and returns the dialog
     */
    async openUpdateDialog(name) {
        const row = this.page.locator('tr.ant-table-row').filter({hasText: name})
        await row.getByTitle('Update').click()
        const dialog = this.page.getByRole('dialog')
        await expect(dialog).toBeVisible()
        return dialog
    }
}
