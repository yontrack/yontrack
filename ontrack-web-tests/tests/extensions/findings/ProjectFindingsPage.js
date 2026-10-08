import {expect} from "@playwright/test";

/**
 * The findings page of a project, `/extension/findings/project/{id}`, its filter in the query of
 * the URL.
 */
export class ProjectFindingsPage {

    constructor(page, project) {
        this.page = page
        this.project = project
    }

    async goTo({query = ''} = {}) {
        const suffix = query ? `?${query}` : ''
        await this.page.goto(
            `${this.project.ontrack.connection.ui}/extension/findings/project/${this.project.id}${suffix}`
        )
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByText('Security findings', {exact: true}).first()).toBeVisible()
        await expect(this.table()).toBeVisible()
    }

    table() {
        return this.page.getByTestId('project-findings')
    }

    /**
     * Checks the external IDs listed by the table, whatever their order.
     */
    async expectFindings(externalIds) {
        const table = this.table()
        await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(externalIds.length)
        for (const externalId of externalIds) {
            await expect(table.getByRole('link', {name: externalId, exact: true})).toBeVisible()
        }
    }

    /**
     * Chooses a value in one of the selects of the filter.
     */
    async filter(field, label) {
        await this.page.getByTestId(`findings-filter-${field}`).click()
        await this.page.locator('.ant-select-dropdown:visible')
            .getByTitle(label, {exact: true})
            .click()
    }

    /**
     * The external IDs listed by the table, in their order.
     */
    async externalIds() {
        return this.table().locator('tbody tr.ant-table-row a code').allTextContents()
    }

    /**
     * Header of the *Exposed for* column, which sorts the findings by exposure. The sticky header is
     * rendered apart from the body: its cell is the first one.
     */
    exposedForHeader() {
        return this.table().locator('th', {hasText: 'Exposed for'}).first()
    }

    /**
     * The *Exposed for* cell of a finding, by its ID.
     */
    exposedFor(findingId) {
        return this.table().getByTestId(`finding-exposed-for-${findingId}`)
    }
}
