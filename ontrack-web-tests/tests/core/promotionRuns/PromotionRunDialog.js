import {expect} from "@playwright/test";

export class PromotionRunDialog {

    constructor(page) {
        this.page = page
        this.dialog = this.page.getByTestId("promotion-run-create-dialog")
    }

    async createPromotionRun({textValues = {}, selectValues = {}} = {}) {
        await expect(this.dialog).toBeVisible()

        for (const [label, value] of Object.entries(textValues)) {
            await this.page.getByLabel(label, {exact: true}).fill(value)
        }
        for (const [label, value] of Object.entries(selectValues)) {
            await this.page.getByRole('combobox', {name: label}).click()
            // AntD Select dropdown may render off-screen; use native JS click
            await this.page.evaluate((optionText) => {
                const options = document.querySelectorAll('[role="option"]')
                for (const opt of options) {
                    if (opt.textContent.trim() === optionText) {
                        opt.click()
                        return
                    }
                }
            }, value)
            // Close the dropdown — JS click doesn't trigger AntD's close behavior
            await this.page.keyboard.press('Escape')
        }

        await this.dialog.getByRole('button', {name: "OK"}).click()
    }

}