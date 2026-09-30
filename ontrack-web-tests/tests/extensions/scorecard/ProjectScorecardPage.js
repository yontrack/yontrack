import {expect} from "@playwright/test";

/**
 * The scorecard page of a project, `/extension/scorecard/project/{id}`.
 */
export class ProjectScorecardPage {

    constructor(page, project) {
        this.page = page
        this.project = project
    }

    /**
     * @param set Set to open, `project` or the name of an estate - the default one when not given
     */
    async goTo(set) {
        const query = set ? `?set=${encodeURIComponent(set)}` : ''
        await this.page.goto(
            `${this.project.ontrack.connection.ui}/extension/scorecard/project/${this.project.id}${query}`
        )
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByText('Scorecard', {exact: true}).first()).toBeVisible()
        await expect(this.setCard('Project')).toBeVisible()
    }

    /**
     * Card of one set, `Project` for the set with no estate.
     */
    setCard(name) {
        return this.page.getByTestId(`scorecard-set-card-${name}`)
    }

    /**
     * Selects a set by its card.
     */
    async selectSet(name) {
        await this.setCard(name).locator('button[aria-pressed]').click()
        await expect(this.setCard(name)).toHaveAttribute('data-selected', 'true')
    }

    /**
     * Section of the selected set.
     */
    set(name) {
        return this.page.getByTestId(`scorecard-set-${name}`)
    }

    /**
     * What a set is - its marker, its labels - at the top of its section.
     */
    setExplanation(name) {
        return this.page.getByTestId(`scorecard-set-explanation-${name}`)
    }

    /**
     * Tile of one reading of a set.
     */
    reading(set, key) {
        return this.page.getByTestId(`reading-${set}-${key}`)
    }
}
