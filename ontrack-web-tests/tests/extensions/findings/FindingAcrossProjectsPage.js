import {expect} from "@playwright/test";

/**
 * The page following one finding across all the projects, `/extension/findings/fan-out`, its
 * finding and its search in the query of the URL.
 */
export class FindingAcrossProjectsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo({query = ''} = {}) {
        const suffix = query ? `?${query}` : ''
        await this.page.goto(`${this.ontrack.connection.ui}/extension/findings/fan-out${suffix}`)
        await expect(this.page.getByTestId('finding-fanout')).toBeVisible()
    }

    explanation() {
        return this.page.getByTestId('finding-fanout-explanation')
    }

    searchBox() {
        return this.page.getByTestId('finding-fanout-search').getByRole('searchbox')
    }

    async search(text) {
        const input = this.searchBox()
        await input.fill(text)
        await input.press('Enter')
    }

    summary() {
        return this.page.getByTestId('finding-fanout-summary')
    }

    /**
     * The names of the projects of the fan-out, in the order of its rows.
     */
    async projectNames() {
        await expect(this.page.getByTestId('finding-fanout-table')).toBeVisible()
        return this.page.getByTestId(/^finding-fanout-project-/).allTextContents()
    }

    state(project) {
        return this.page.getByTestId(`finding-fanout-state-${project}`)
    }

    branches(project) {
        return this.page.getByTestId(`finding-fanout-branches-${project}`)
    }

    branch(project, branch) {
        return this.page.getByTestId(`finding-fanout-branch-${project}-${branch}`)
    }

    searchedRow(externalId) {
        return this.page.getByTestId(`finding-fanout-searched-finding-${externalId}`)
    }

    searchedProjects(externalId) {
        return this.page.getByTestId(`finding-fanout-searched-projects-${externalId}`)
    }

    backToSearch() {
        return this.page.getByTestId('finding-fanout-back-search')
    }
}
