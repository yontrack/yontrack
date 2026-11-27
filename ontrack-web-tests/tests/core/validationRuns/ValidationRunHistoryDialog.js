import {expect} from "@playwright/test";

export class ValidationRunHistoryDialog {
    constructor(page, run) {
        this.page = page
        this.run = run
    }

    async waitFor() {
        await expect(this.page.getByText(`${this.run.validationStamp.name} in build ${this.run.build.name}`)).toBeVisible()
    }

    async selectStatus(status) {
        // The mouse still hovers the run status which opened this dialog, and its quick-transition
        // popover lists the same status names as the select: the mouse is moved away first, and
        // the click is limited to the options of the select (#2185)
        await this.page.mouse.move(0, 0)
        const id = `validation-run-status-${this.run.id}`
        await this.page.locator(`#${id}`).click()
        await this.page.locator('.ant-select-item-option').getByText(status, {exact: true}).click()
    }

    async setDescription(text) {
        return this.page.getByPlaceholder("Optional description").fill(text)
    }

    async addStatus() {
        await this.page.getByRole('button', {name: "Add"}).click()
        return expect(this.page.getByRole('button', {name: "Add"})).toBeDisabled()
    }

    async checkStatus(expectedStatus, expectedMessage) {
        const dialog = this.page.getByRole('dialog')
        await expect(dialog.getByText(expectedStatus, {exact: true}).first()).toBeVisible()
        await expect(dialog.getByText(expectedMessage, {exact: true})).toBeVisible()
    }
}
