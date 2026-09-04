const {expect} = require("@playwright/test");
const {SCMChangeLogPage} = require("../../extensions/scm/scm");
const {BuildPage} = require("../builds/BuildPage");

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

    timelineMedal(build, promotionLevel) {
        return this.page.getByTestId(`timeline-medal-${build.id}-${promotionLevel.id}`)
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
        const entry = this.inspectorPromotionRows(promotionLevel)
        if (present) {
            // `first()` because a build promoted twice to one level has two rows here, and the
            // question this method asks - did the build reach this level - is answered by either
            await expect(entry.first()).toBeVisible()
        } else {
            // Not `toBeHidden`, which passes on the FIRST of several matches being hidden
            await expect(entry).toHaveCount(0)
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

    /**
     * The inspector's header: which build the panel is describing, and the way out to its page.
     */
    inspectorBuildTitle() {
        return this.page.getByTestId('inspector-build-title')
    }

    async checkInspectorNames(build) {
        const title = this.inspectorBuildTitle()
        await expect(title).toBeVisible()
        await expect(title).toContainText(build.name)
    }

    async goToBuildFromInspector(build) {
        await this.inspectorBuildTitle().getByRole('link').click()
        // The URL and the build's own name, rather than `BuildPage.checkOnBuildPage`: that method
        // asserts an unscoped `getByText("Promotions")`, which is ambiguous on the build page of a
        // branch that HAS promotion levels - the command bar carries a "Promotions" link too. This
        // view's fixtures always create levels, so it would be a coin toss here.
        await expect(this.page).toHaveURL(new RegExp(`/build/${build.id}$`))
        const buildPage = new BuildPage(this.page, build)
        await buildPage.assertName(build.name)
        return buildPage
    }

    // --- Inspector: promotion run actions ------------------------------------

    /**
     * A promotion row in the inspector, addressed by its level.
     *
     * The per-run controls are found by test id PREFIX inside that row rather than by run id: a
     * caller which has just promoted a build through the API knows the level it asked for, not the
     * id of the run the server made.
     */
    inspectorPromotionRows(promotionLevel) {
        // By attribute, not by test id: rows are identified by RUN, and one level may have several
        return this.page.locator(`[data-promotion-level="${promotionLevel.id}"]`)
    }

    inspectorPromotionRow(promotionLevel) {
        return this.inspectorPromotionRows(promotionLevel).first()
    }

    inspectorPromotionActions(promotionLevel) {
        return this.inspectorPromotionRow(promotionLevel).locator('.ot-row-actions')
    }

    inspectorRunLink(promotionLevel) {
        return this.inspectorPromotionRow(promotionLevel)
            .locator('[data-testid^="inspector-promotion-run-link-"]')
    }

    inspectorDeleteRun(promotionLevel) {
        return this.inspectorPromotionRow(promotionLevel)
            .locator('[data-testid^="build-promotion-delete-"]')
    }

    inspectorRepromote(build, promotionLevel) {
        return this.inspectorPromotionRow(promotionLevel)
            .getByTestId(`build-promote-${build.id}-${promotionLevel.id}`)
    }

    async checkInspectorRunActionsRevealedOnFocus(promotionLevel) {
        const actions = this.inspectorPromotionActions(promotionLevel)
        // Quiet to begin with...
        await expect(actions).toHaveCSS('opacity', '0')
        // ... and revealed by KEYBOARD focus, not only by a pointer: these controls are in the tab
        // order, and one that is focused but invisible is a keyboard trap in all but name
        await this.inspectorRunLink(promotionLevel).focus()
        await expect(actions).toHaveCSS('opacity', '1')
    }

    async deleteInspectorPromotion(promotionLevel) {
        await this.inspectorPromotionRow(promotionLevel).hover()
        await this.inspectorDeleteRun(promotionLevel).click()
        await this.page.getByRole('button', {name: "Confirm deletion"}).click()
    }

    async checkNoInspectorPromotion(promotionLevel) {
        // Counted, so this cannot pass while a second run at the same level is still on screen
        await expect(this.inspectorPromotionRows(promotionLevel)).toHaveCount(0)
    }

    /**
     * The panel's own promote affordance, which names no level.
     */
    promoteButton(build) {
        return this.page.getByTestId(`build-promote-${build.id}`)
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
