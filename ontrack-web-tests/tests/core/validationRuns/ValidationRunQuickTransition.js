import {expect} from "@playwright/test";
import {ValidationRunHistoryDialog} from "./ValidationRunHistoryDialog";

/**
 * The quick-transition popover, opened by clicking the status of a validation run.
 */
export class ValidationRunQuickTransition {
    constructor(page, run) {
        this.page = page
        this.run = run
        this.popover = page.getByTestId(`validation-run-quick-transition-${run.id}`)
    }

    async waitFor() {
        await expect(this.popover).toBeVisible()
        // The status actions appear once the run is loaded: the clicks on them wait for that
        await expect(this.historyButton()).toBeVisible()
    }

    statusButton(status) {
        return this.popover.getByRole('button', {name: status, exact: true})
    }

    historyButton() {
        return this.popover.getByRole('button', {name: 'History…', exact: true})
    }

    async applyStatus(status) {
        await this.statusButton(status).click()
        await expect(this.popover).toBeHidden()
    }

    async applyStatusWithComment(status, comment) {
        await this.popover.getByRole('button', {name: 'With comment…', exact: true}).click()
        // antd draws the radio as a button and hides the input itself: clicking its label
        await this.popover.getByText(status, {exact: true}).click()
        await expect(this.popover.getByRole('radio', {name: status, exact: true})).toBeChecked()
        await this.popover.getByRole('textbox', {name: 'Comment'}).fill(comment)
        await this.popover.getByRole('button', {name: 'Confirm', exact: true}).click()
        await expect(this.popover).toBeHidden()
    }

    async history() {
        await this.historyButton().click()
        const dialog = new ValidationRunHistoryDialog(this.page, this.run)
        await dialog.waitFor()
        return dialog
    }
}
