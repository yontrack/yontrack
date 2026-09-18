import {expect} from "@playwright/test";
import {SetupPage} from "./SetupPage";

/**
 * The Environments home - since #1791 the project x environment matrix.
 *
 * `goTo` lands on `?scope=all` rather than on the bare route. The matrix opens on **Favourites**,
 * and a project a test has just created is nobody's favourite, so the bare route would show an empty
 * matrix for one render and then - because the test account usually has no favourite at all - be
 * moved onto All by the screen itself. Asking for All outright makes every test below deterministic
 * whatever the account happens to have starred. The Favourites behaviour has tests of its own.
 */
export class EnvironmentsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo({query = 'scope=all'} = {}) {
        const suffix = query ? `?${query}` : ''
        await this.page.goto(`${this.ontrack.connection.ui}/extension/environments/environments${suffix}`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('matrix-toolbar')).toBeVisible()
    }

    /**
     * Setup is one header command: the matrix is an operational screen, and creating environments
     * and slots moved out of it. Since #1793 it is a link to the Setup page rather than a dropdown
     * of the two dialogs it used to carry.
     */
    async setup() {
        await this.page.getByTestId('environments-setup').click()
        const setup = new SetupPage(this.page, this.ontrack)
        await setup.expectOnPage()
        return setup
    }

    /**
     * Creates an environment through Setup and comes back to the matrix *as it was*.
     *
     * The tests below are about what the matrix then shows, so coming back is part of the action
     * rather than something each of them has to remember - and coming back to the **same URL**
     * matters: the scope and the project search live in the query string, and a matrix reopened
     * without them pages twenty projects at a time over a stack shared with every other test.
     */
    async createEnvironment({name, description, order, tags}) {
        await this.#throughSetup(setup => setup.createEnvironment({name, description, order, tags}))
    }

    async createSlot({projectName, qualifier, description, environmentNames}) {
        await this.#throughSetup(
            setup => setup.createSlot({projectName, qualifier, description, environmentNames})
        )
    }

    async #throughSetup(action) {
        const matrixUrl = this.page.url()
        const setup = await this.setup()
        await action(setup)
        await this.page.goto(matrixUrl)
        await this.expectOnPage()
    }

    /**
     * An environment is a *column* of the matrix, so it is visible only when some visible row has a
     * slot in it - which is the rule the matrix is built on and worth asserting through rather than
     * around.
     */
    async checkEnvironmentIsVisible(name) {
        let environmentId = null
        await expect.poll(async () => {
            const environment = await this.ontrack.environments.findEnvironmentByName(name)
            environmentId = environment?.id
            return environmentId
        }).toBeDefined()
        if (!environmentId) throw new Error(`Environment with name ${name} not found`)
        await expect(this.page.getByTestId(`matrix-column-${environmentId}`)).toBeVisible()
    }

    async checkEnvironmentIsNotVisible(environment) {
        await expect(this.page.getByTestId(`matrix-column-${environment.id}`)).toHaveCount(0)
    }

    async checkSlotIsVisible(slot) {
        await expect(this.page.getByTestId(`slot-cell-${slot.id}`)).toBeVisible()
    }

    async checkSlotIsNotVisible(slot) {
        await expect(this.page.getByTestId(`slot-cell-${slot.id}`)).toHaveCount(0)
    }

    async checkProjectIsVisible(project) {
        await expect(this.page.getByText(project.name, {exact: true})).toBeVisible()
    }

    async checkProjectIsNotVisible(project) {
        await expect(this.page.getByText(project.name, {exact: true})).toHaveCount(0)
    }

    /**
     * A qualifier row, nested under its project's row.
     */
    async checkQualifierRow(project, qualifier) {
        await expect(this.page.getByTestId(`matrix-qualifier-${project.id}-${qualifier}`)).toBeVisible()
    }

    /**
     * `data-testid` on an Ant Design `Input.Search` lands on the `<input>` itself, not on a wrapper
     * around it - `Input` forwards the props it does not know straight to the control. So the test
     * id *is* the box, and looking for a control inside it finds nothing and waits out the whole
     * timeout.
     */
    async searchProject(text) {
        const search = this.page.getByTestId('matrix-search-project')
        await search.fill(text)
        await search.press('Enter')
    }

    /**
     * Same story as `searchProject`: `data-testid` on a `Checkbox` ends up on the `<input>`. The
     * click goes to the label's text rather than to that input, which Ant Design draws at zero
     * opacity under its own box - clicking what a reader can actually see is one less thing that
     * can silently stop being clickable.
     */
    async toggleActivity() {
        await this.page.getByTestId('matrix-toolbar').getByText('Only with activity').click()
    }

    async selectScope(name) {
        await this.page.getByTestId('matrix-scope').getByText(name, {exact: true}).click()
    }

    async checkFreshness() {
        await expect(this.page.getByTestId('matrix-freshness-age')).toContainText('Updated')
    }
}
