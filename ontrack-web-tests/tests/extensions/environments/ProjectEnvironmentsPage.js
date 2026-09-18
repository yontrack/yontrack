import {expect} from "@playwright/test";

/**
 * A project's environments - since #1795 a slot graph of slot cells, with a Matrix view beside it.
 *
 * The three-panel splitter it replaces is gone: there is no builds panel and no actions panel, so
 * every helper here addresses either the toolbar or a node of the graph.
 *
 * Two addressing notes worth stating, both learned across this initiative:
 *
 * - **A node of the graph is a slot cell**, so it carries the cell's own test id
 *   (`slot-cell-<id>`) as well as the node's (`slot-graph-node-<id>`). Asserting on the first is
 *   what makes "the node *is* a cell" a test rather than a claim.
 * - **The view and the qualifier live in the URL**, so switching either is asserted through the
 *   address as well as through the screen - a toggle that changes the table but leaves the address
 *   alone cannot be shared or reloaded, which is the point of putting it there.
 */
export class ProjectEnvironmentsPage {

    constructor(page, project) {
        this.page = page
        this.project = project
    }

    async goTo({query = ''} = {}) {
        const suffix = query ? `?${query}` : ''
        await this.page.goto(
            `${this.project.ontrack.connection.ui}/extension/environments/projects/${this.project.id}${suffix}`
        )
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('project-environments-toolbar')).toBeVisible()
    }

    /**
     * The graph itself, which is what the route opens on.
     */
    async expectGraph() {
        await expect(this.page.getByTestId('project-slot-graph')).toBeVisible()
    }

    /**
     * A node of the graph, asserted as the slot *cell* it is made of.
     */
    async expectNode(slot) {
        await expect(this.page.getByTestId(`slot-graph-node-${slot.id}`)).toBeVisible()
        await expect(this.page.getByTestId(`slot-cell-${slot.id}`)).toBeVisible()
    }

    async expectNoNode(slot) {
        await expect(this.page.getByTestId(`slot-graph-node-${slot.id}`)).toHaveCount(0)
    }

    /**
     * What the cell of a node says is deployed there - the same line the matrix draws.
     */
    async expectNodeDeployed(slot, buildName) {
        await expect(this.page.getByTestId(`slot-cell-${slot.id}-deployed`)).toContainText(buildName)
    }

    async clickNode(slot) {
        await this.page.getByTestId(`slot-cell-${slot.id}`).click()
    }

    /**
     * `data-testid` on an Ant Design `Segmented` lands on a wrapper, so the option is reached by its
     * text inside it - unlike `Input` and `Checkbox`, which put the id on the control itself.
     */
    async selectView(name) {
        await this.page.getByTestId('project-environments-view').getByText(name, {exact: true}).click()
    }

    async expectMatrix() {
        await expect(this.page.getByTestId('environment-matrix')).toBeVisible()
    }

    /**
     * The Matrix view is the home matrix pinned to this project: no toolbar to filter it with, and
     * no other project's row in it.
     */
    async expectMatrixPinned() {
        await this.expectMatrix()
        await expect(this.page.getByTestId('matrix-toolbar')).toHaveCount(0)
    }

    async expectMatrixSlot(slot) {
        await expect(this.page.getByTestId(`slot-cell-${slot.id}`)).toBeVisible()
    }

    async expectNoMatrixRowFor(project) {
        await expect(this.page.getByText(project.name, {exact: true})).toHaveCount(0)
    }

    async expectQualifierSelector() {
        await expect(this.page.getByTestId('project-environments-qualifier')).toBeVisible()
    }

    async expectNoQualifierSelector() {
        await expect(this.page.getByTestId('project-environments-qualifier')).toHaveCount(0)
    }

    /**
     * Ant Design draws a `Select`'s options in a portal at the end of the document, so the option is
     * reached by its title rather than from inside the control.
     */
    async selectQualifier(label) {
        await this.page.getByTestId('project-environments-qualifier').click()
        await this.page.getByTitle(label, {exact: true}).click()
    }

    async expectFreshness() {
        await expect(this.page.getByTestId('project-environments-freshness-age')).toContainText('Updated')
    }
}
