import {expect} from "@playwright/test";

/**
 * Form fields of a CHML (Critical / high / medium / low) validation data type configuration,
 * inside a validation stamp dialog.
 */
export class CHMLConfigForm {

    constructor(page, dialog) {
        this.page = page
        this.dialog = dialog
    }

    row(text) {
        return this.dialog.locator('.ant-space-align-baseline').filter({hasText: text})
    }

    failedRow() {
        return this.row('Failed if # of')
    }

    warningRow() {
        return this.row('Warning if # of')
    }

    async checkRow(row, level, value) {
        await expect(row.locator('.ant-select-content')).toHaveText(level)
        await expect(row.getByRole('spinbutton')).toHaveValue(String(value))
    }

    /**
     * Checks the levels (as displayed, like `Critical`) and the values of the form
     */
    async checkConfig({failedLevel, failedValue, warningLevel, warningValue}) {
        await this.checkRow(this.failedRow(), failedLevel, failedValue)
        await this.checkRow(this.warningRow(), warningLevel, warningValue)
    }

    async setRow(row, level, value) {
        await row.locator('.ant-select').click()
        await this.page
            .locator('.ant-select-dropdown:visible .ant-select-item-option')
            .filter({hasText: new RegExp(`^${level}$`)})
            .click()
        await row.getByRole('spinbutton').fill(String(value))
    }

    async setWarning(level, value) {
        await this.setRow(this.warningRow(), level, value)
    }
}
