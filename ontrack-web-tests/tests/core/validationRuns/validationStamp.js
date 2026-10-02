import {expect} from "@playwright/test";
import {AbstractImagePage} from "../common/AbstractImagePage";

export class ValidationStampPage extends AbstractImagePage {

    constructor(page, validationStamp) {
        super(page)
        this.validationStamp = validationStamp
    }


    id() {
        return `validation-stamp-image-${this.validationStamp.id}`
    }

    async goTo() {
        await this.page.goto(`${this.validationStamp.ontrack.connection.ui}/validationStamp/${this.validationStamp.id}`)
        await expect(this.page.getByText(this.validationStamp.name)).toBeVisible()
    }

    /**
     * Opens the update dialog of the validation stamp and returns the dialog
     */
    async openUpdateDialog() {
        await this.page.getByRole('button', {name: 'Update validation stamp'}).click()
        const dialog = this.page.getByRole('dialog')
        await expect(dialog).toBeVisible()
        return dialog
    }

    history() {
        return this.page.getByTestId("validation-stamp-history")
    }

    /**
     * Rows of the validation history
     */
    historyRows() {
        return this.history().locator('tbody tr.ant-table-row')
    }

    async openLastStatusFilter() {
        await this.history().locator('th', {hasText: 'Last status'}).locator('.ant-table-filter-trigger').click()
        const dropdown = this.page.locator('.ant-table-filter-dropdown')
        await expect(dropdown).toBeVisible()
        return dropdown
    }

    /**
     * Filters the validation history on the last status of its runs
     *
     * @param label `Passed` or `Not passed`
     */
    async filterHistoryOnLastStatus(label) {
        const dropdown = await this.openLastStatusFilter()
        // The select box, not its search input: once a value is selected, its label covers the input
        await dropdown.locator('.ant-select').click()
        await this.page
            .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option', {hasText: new RegExp(`^${label}$`)})
            .click()
        await dropdown.getByRole('button', {name: 'Search'}).click()
    }

    async resetHistoryLastStatusFilter() {
        const dropdown = await this.openLastStatusFilter()
        await dropdown.getByRole('button', {name: 'Reset'}).click()
    }

}