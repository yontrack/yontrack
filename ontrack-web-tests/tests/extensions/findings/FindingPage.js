import {expect} from "@playwright/test";

/**
 * The page of one security finding, `/extension/findings/finding/{id}`.
 */
export class FindingPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo(id) {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/findings/finding/${id}`)
    }

    async expectOnPage(externalId) {
        await expect(this.page).toHaveURL(/\/extension\/findings\/finding\/\d+$/)
        await expect(this.summary()).toBeVisible()
        await expect(this.summary().getByText(externalId, {exact: true})).toBeVisible()
    }

    summary() {
        return this.page.getByTestId('finding-summary')
    }

    exposure(branch, validationStamp) {
        return this.page.getByTestId(`finding-exposure-${branch}-${validationStamp}`)
    }

    exposureRows() {
        return this.page.getByTestId('finding-exposure').locator('tbody tr.ant-table-row')
    }

    observations() {
        return this.page.getByTestId('finding-observation')
    }
}

/**
 * The page of a validation run, `/validationRun/{id}`, and the findings of a security scan on it.
 */
export class ValidationRunFindingsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo(id) {
        await this.page.goto(`${this.ontrack.connection.ui}/validationRun/${id}`)
        await expect(this.page.getByTestId('table-run-statuses')).toBeVisible()
    }

    section() {
        return this.page.getByTestId('table-run-findings')
    }

    rows() {
        return this.page.getByTestId('validation-run-findings').locator('tbody tr.ant-table-row')
    }
}
