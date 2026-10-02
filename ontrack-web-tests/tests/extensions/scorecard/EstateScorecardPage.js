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
}
