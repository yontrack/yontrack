import {expect} from "@playwright/test";

/**
 * The scorecard page of a project, `/extension/scorecard/project/{id}`.
 */
export class ProjectScorecardPage {

    constructor(page, project) {
        this.page = page
        this.project = project
    }

    async goTo() {
        await this.page.goto(
            `${this.project.ontrack.connection.ui}/extension/scorecard/project/${this.project.id}`
        )
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByText('Scorecard', {exact: true}).first()).toBeVisible()
        await expect(this.set('Project')).toBeVisible()
    }

    /**
     * Section of one set, `Project` for the set with no estate.
     */
    set(name) {
        return this.page.getByTestId(`scorecard-set-${name}`)
    }

    /**
     * What a set is — its marker, its labels — at the top of its section.
     */
    setExplanation(name) {
        return this.page.getByTestId(`scorecard-set-explanation-${name}`)
    }

    /**
     * Card of one reading of a set.
     */
    reading(set, key) {
        return this.page.getByTestId(`reading-${set}-${key}`)
    }
}
