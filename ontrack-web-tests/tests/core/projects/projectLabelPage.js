import {expect} from "@playwright/test";
import {expectOnPage} from "../../support/page-utils";
import {labelDisplay} from "../../support/labels";

/**
 * The label page, at `project-labels/<id>`: the chip, the description and the projects carrying
 * the label. It is where a label chip leads, and where the project count of the admin page leads.
 */
export class ProjectLabelPage {

    constructor(page, ontrack, label) {
        this.page = page
        this.ontrack = ontrack
        this.label = label
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/project-labels/${this.label.id}`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expectOnPage(this.page, "project-label")
        await expect(this.page.getByTestId(`label-${labelDisplay(this.label)}`).first()).toBeVisible()
    }

    projectLink(project) {
        return this.page.getByRole('link', {name: project.name, exact: true})
    }

    async expectProject(project) {
        await expect(this.projectLink(project)).toBeVisible()
    }

    async expectNoProject(project) {
        await expect(this.projectLink(project)).not.toBeVisible()
    }
}
