const {expect} = require("@playwright/test");
const {SCMChangeLogPage} = require("../../extensions/scm/scm");

/**
 * The pipeline branch content view.
 *
 * A page object of its own rather than more methods on `BranchPage`: the two views answer the same
 * questions with different regions, and a single object with `checkBuildPresentInTheTable` next to
 * `checkBuildPresentInTheTimeline` would be one object pretending to be two.
 */
class BranchPipelinePage {

    constructor(page, branch) {
        this.page = page
        this.branch = branch
        this.changeLogButton = this.page.getByRole('button', {name: 'Change log', exact: true})
    }

    async goTo({build} = {}) {
        const query = new URLSearchParams({view: 'pipeline'})
        if (build) {
            query.set('build', String(build.id))
        }
        await this.page.goto(`${this.branch.ontrack.connection.ui}/branch/${this.branch.id}?${query}`)
        await this.checkOnPage()
    }

    async checkOnPage() {
        // Scoped to the page header: after a client-side navigation Next mirrors the document title,
        // which carries the branch name, into its route announcer
        await expect(this.page.getByTestId('branch-title')).toContainText(this.branch.name)
        await expect(this.page.getByTestId('pipeline-stats')).toBeVisible()
    }

    // --- Experimental alert -------------------------------------------------

    experimentalAlert() {
        // The test id is on the alert's message, so the whole band goes when it is dismissed
        return this.page.getByRole('alert')
            .filter({has: this.page.getByTestId('pipeline-experimental-alert')})
    }

    async checkExperimentalAlert() {
        await expect(this.experimentalAlert()).toBeVisible()
    }

    async checkNoExperimentalAlert() {
        await expect(this.experimentalAlert()).toBeHidden()
    }

    async dismissExperimentalAlert() {
        // Ant's own close affordance: an icon button with no accessible name of its own
        await this.experimentalAlert().locator('.ant-alert-close-icon').click()
        await this.checkNoExperimentalAlert()
    }

    // --- Stats -------------------------------------------------------------

    stat(id) {
        return this.page.getByTestId(`pipeline-stat-${id}`)
    }

    async checkTotalBuilds(count) {
        await expect(this.stat('total-builds')).toContainText(String(count))
    }

    async checkLatestVersion(version) {
        await expect(this.stat('latest-version')).toContainText(version)
    }

    async checkNoLatestVersion() {
        await expect(this.stat('latest-version')).toBeHidden()
    }

    // --- Pipeline ----------------------------------------------------------

    stage(promotionLevel) {
        return this.page.getByTestId(`pipeline-stage-${promotionLevel.id}`)
    }

    async checkStage(promotionLevel, {reached}) {
        const stage = this.stage(promotionLevel)
        await expect(stage).toBeVisible()
        await expect(stage).toHaveAttribute('data-reached', String(reached))
    }

    async checkNoPipelineBand() {
        await expect(this.page.getByTestId('pipeline-stages')).toBeHidden()
    }

    async selectStageBuild(promotionLevel) {
        const link = this.page.getByTestId(`pipeline-stage-${promotionLevel.id}-build`)
        await expect(link).toBeVisible()
        await link.click()
    }

    // --- Timeline ----------------------------------------------------------

    timelineCard(build) {
        return this.page.getByTestId(`timeline-build-${build.id}`)
    }

    async checkBuildPresent(build) {
        await expect(this.timelineCard(build)).toBeVisible()
    }

    async selectBuild(build) {
        const card = this.timelineCard(build)
        await expect(card).toBeVisible()
        await card.click()
    }

    async checkBuildSelected(build) {
        // The card is a real button, so "selected" is `aria-pressed`, not a class name
        await expect(this.timelineCard(build)).toHaveAttribute('aria-pressed', 'true')
    }

    async checkBuildNotSelected(build) {
        await expect(this.timelineCard(build)).toHaveAttribute('aria-pressed', 'false')
    }

    /**
     * The decorations drawn on a build's card - what an extension says about the build, the
     * environments it is deployed in among them.
     */
    buildDecorations(build) {
        return this.page.getByTestId(`timeline-build-decorations-${build.id}`)
    }

    async checkNoBuildDecorations(build) {
        await expect(this.buildDecorations(build)).toBeHidden()
    }

    async checkNoBuilds() {
        await expect(this.page.getByText("No build to show on this branch.")).toBeVisible()
        await expect(this.page.getByTestId('build-timeline')).toBeHidden()
    }

    // --- Inspector ---------------------------------------------------------

    inspector() {
        return this.page.getByTestId('build-inspector')
    }

    async checkInspectorPromotion(promotionLevel, {present = true} = {}) {
        const panel = this.page.getByTestId('inspector-promotions')
        await expect(panel).toBeVisible()
        // By id rather than by the level's name: the name also appears on the stage card above and
        // in the promote button below, so a name lookup would pass for the wrong reason
        const entry = panel.getByTestId(`inspector-promotion-${promotionLevel.id}`)
        if (present) {
            await expect(entry).toBeVisible()
        } else {
            await expect(entry).toBeHidden()
        }
    }

    async checkInspectorNoPromotion() {
        await expect(this.page.getByText("This build has not been promoted.")).toBeVisible()
    }

    async checkInspectorValidation(validationStamp) {
        const panel = this.page.getByTestId('inspector-validations')
        await expect(panel).toBeVisible()
        await expect(panel.getByTestId(`inspector-validation-${validationStamp.id}`)).toBeVisible()
    }

    async checkNoInspector() {
        await expect(this.inspector()).toBeHidden()
    }

    async checkNoValidationsPanel() {
        await expect(this.page.getByTestId('inspector-validations')).toBeHidden()
    }

    // --- Range selection ---------------------------------------------------

    async selectRangeBuild(build) {
        // Same affordance, same id as the builds table's: the change log means the same thing here
        await this.page.locator(`#range-${build.id}`).click()
    }

    async checkChangeLogButtonPresent({disabled}) {
        await expect(this.changeLogButton).toBeVisible()
        if (disabled) {
            await expect(this.changeLogButton).toBeDisabled()
        } else {
            await expect(this.changeLogButton).toBeEnabled()
        }
    }

    async goToChangeLog() {
        await this.changeLogButton.click()
        const changeLogPage = new SCMChangeLogPage(this.page)
        await changeLogPage.checkDisplayed()
        return changeLogPage
    }

}

module.exports = {BranchPipelinePage}
