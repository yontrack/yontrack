import {expect} from "@playwright/test";

/**
 * The generic property edit dialog (see `PropertyDialog.js` on the frontend side).
 */
export class PropertyDialog {

    constructor(page) {
        this.page = page
        this.dialog = page.getByTestId('property-dialog')
    }

    async checkOnDialog() {
        await expect(this.dialog).toBeVisible()
    }

    /**
     * Gets a form field of the property-specific part of the dialog, by its test ID.
     */
    field(testId) {
        return this.dialog.getByTestId(testId)
    }

    /**
     * Checks that a multiple selection field is visible and holds exactly the expected labels.
     */
    async checkSelectedValues(testId, expectedLabels) {
        const field = this.field(testId)
        await expect(field).toBeVisible()
        const items = field.locator('.ant-select-selection-item')
        await expect(items).toHaveCount(expectedLabels.length)
        for (const label of expectedLabels) {
            await expect(items.filter({hasText: label})).toHaveCount(1)
        }
    }

    async cancel() {
        await this.dialog.getByRole('button', {name: 'Cancel'}).click()
        // the antd Modal keeps its panel mounted after closing, so it's hidden, not gone
        await expect(this.dialog).toBeHidden()
    }
}
