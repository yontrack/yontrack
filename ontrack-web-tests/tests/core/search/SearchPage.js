import {expect} from "@playwright/test";

export class SearchPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async expectOnPage() {
    }

    async expectProjectResultPresent(name) {
        const link = this.page.getByRole('link', {name, exact: true}).filter(this.page.locator('.ot-search-result'))
        await expect(link).toBeVisible({timeout: 20_000}) // Waiting a bit longer, in case ES is not ready yet
    }

    /**
     * The same commit can be found in several projects: its result is told apart by its link.
     */
    scmCommitResultLink({project, commitId}) {
        return this.page.locator(`a[href="/extension/scm/${project.name}/commit-info/${commitId}"]`)
    }

    async expectScmCommitResultPresent({project, commitId}) {
        const link = this.scmCommitResultLink({project, commitId})
        await expect(link).toBeVisible({timeout: 20_000}) // Waiting a bit longer, in case ES is not ready yet
    }

    async expectScmIssueResultPresent({issueKey}) {
        const link = this.page.getByRole('link', {name: issueKey, exact: true})
        await expect(link).toBeVisible({timeout: 20_000}) // Waiting a bit longer, in case ES is not ready yet
    }

    async clickProjectResult(name) {
        const link = this.page.getByRole('link', {name, exact: true})
        await link.click()
    }

    async clickScmCommitResult({project, commitId}) {
        const link = this.scmCommitResultLink({project, commitId})
        await link.click()
    }

    async clickScmIssueResult({issueKey}) {
        const link = this.page.getByRole('link', {name: issueKey, exact: true})
        await link.click()
    }
}