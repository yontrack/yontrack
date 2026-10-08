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

    timeline() {
        return this.page.getByTestId('finding-exposure-timeline')
    }

    timelineLane(branch, validationStamp) {
        return this.page.getByTestId(`finding-exposure-lane-${branch}-${validationStamp}`)
    }

    /**
     * Link to the remediation time of the project on its scorecard, in the header of the exposure
     */
    scorecardRemediationLink() {
        return this.page.getByTestId('finding-scorecard-remediation-link')
    }

    historyEntry(type) {
        return this.page.getByTestId(`finding-history-${type}`)
    }

    historyGroups() {
        return this.page.getByTestId('finding-history-group')
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
