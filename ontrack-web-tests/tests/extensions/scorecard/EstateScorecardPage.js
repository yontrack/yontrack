import {expect} from "@playwright/test";

/**
 * The scorecards of the estates, `/extension/scorecard/scorecards`.
 */
export class ScorecardsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/scorecard/scorecards`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('scorecards')).toBeVisible()
    }

    /**
     * Link of an estate to its scorecard.
     */
    estate(name) {
        return this.page.getByTestId(`scorecards-estate-${name}`)
    }
}

/**
 * The scorecard of an estate, `/extension/scorecard/estate/{name}`.
 */
export class EstateScorecardPage {

    constructor(page, ontrack, name) {
        this.page = page
        this.ontrack = ontrack
        this.name = name
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/scorecard/estate/${encodeURIComponent(this.name)}`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('estate-readings')).toBeVisible()
    }

    /**
     * Header of the column of a reading, or of the projects with `project`. The sticky header of
     * the table is rendered apart from its body: the first one is the visible one.
     */
    column(key) {
        return this.page.getByTestId(`estate-column-${key}`).first()
    }

    /**
     * Cell of the header of a column, which carries its sort.
     */
    columnHeaderCell(key) {
        return this.page.locator('th').filter({has: this.page.getByTestId(`estate-column-${key}`)}).first()
    }

    /**
     * The ⓘ of the header of a reading.
     */
    columnInfo(key) {
        return this.column(key).getByRole('button', {name: /^About /})
    }

    /**
     * Opens the ⓘ of the header of a reading, and gives its popover.
     */
    async openColumnInfo(key) {
        await this.columnInfo(key).hover()
        const info = this.page.getByTestId(`estate-column-info-${key}`)
        await expect(info).toBeVisible()
        return info
    }

    /**
     * Opens the ⓘ of the roll-up row, and gives its popover.
     */
    async openRollUpInfo() {
        await this.page.getByRole('button', {name: 'About All projects'}).hover()
        const info = this.page.getByTestId('estate-rollup-info')
        await expect(info).toBeVisible()
        return info
    }

    /**
     * Names of the projects, in the order of the rows.
     */
    async projectNames() {
        return this.page.getByTestId(/^estate-project-/).allTextContents()
    }

    project(name) {
        return this.page.getByTestId(`estate-project-${name}`)
    }

    cell(project, key) {
        return this.page.getByTestId(`estate-cell-${project}-${key}`)
    }

    rollUp(key) {
        return this.page.getByTestId(`estate-rollup-${key}`)
    }

    /**
     * Opens the findings fan-out tab.
     */
    async openFanOut() {
        await this.page.getByRole('tab', {name: 'Findings fan-out'}).click()
        await expect(this.page.getByTestId('estate-fanout')).toBeVisible()
    }

    /**
     * Searches the fan-out of a finding by its external ID.
     */
    async searchFinding(externalId) {
        const input = this.page.getByTestId('estate-fanout-search').getByRole('searchbox')
        await input.fill(externalId)
        await input.press('Enter')
    }

    /**
     * External IDs of the ranked findings, the most widespread first.
     */
    async rankedFindingIds() {
        await expect(this.page.getByTestId('estate-ranked-findings')).toBeVisible()
        const rows = this.page.getByTestId(/^estate-ranked-finding-/)
        const ids = await rows.evaluateAll(elements => elements.map(it => it.getAttribute('data-testid')))
        return ids.map(it => it.substring('estate-ranked-finding-'.length))
    }

    rankedFinding(externalId) {
        return this.page.getByTestId(`estate-ranked-finding-${externalId}`)
    }

    rankedFindingProjects(externalId) {
        return this.page.getByTestId(`estate-ranked-projects-${externalId}`)
    }

    /**
     * The searchbox of the fan-out.
     */
    fanOutSearch() {
        return this.page.getByTestId('estate-fanout-search').getByRole('searchbox')
    }

    fanOutSummary() {
        return this.page.getByTestId('estate-fanout-summary')
    }

    /**
     * Names of the projects of the fan-out, in the order of the rows.
     */
    async fanOutProjectNames() {
        await expect(this.page.getByTestId('estate-fanout-table')).toBeVisible()
        return this.page.getByTestId(/^estate-fanout-project-/).allTextContents()
    }

    fanOutState(project) {
        return this.page.getByTestId(`estate-fanout-state-${project}`)
    }

    fanOutBranches(project) {
        return this.page.getByTestId(`estate-fanout-branches-${project}`)
    }

    fanOutBranch(project, branch) {
        return this.page.getByTestId(`estate-fanout-branch-${project}-${branch}`)
    }
}
