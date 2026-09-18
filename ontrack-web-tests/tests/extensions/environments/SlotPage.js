import {expect} from "@playwright/test";
import {confirmBox} from "../../support/confirm";
import {SlotBuilds} from "./SlotBuilds";
import {SlotPipelineTable} from "./SlotPipelineTable";

/**
 * The slot page - since #1793 a header block and three tabs.
 *
 * Two things about addressing it are worth stating, because both were learned the hard way:
 *
 * - **A tab's content is only in the document once the tab is selected.** Ant Design mounts tab
 *   panels lazily, so every helper below selects its tab first rather than assuming the page opened
 *   on it. The tab is also in the URL (`?tab=`), which is what lets `goToSetup` land on it directly.
 * - **The page's title is now "production · petclinic", not "Slot production - petclinic".** The
 *   word *slot* appears only in Setup, and the breadcrumb carries the same string as the title - so
 *   a `getByText` on it matches twice and has to say which one it means.
 */
export class SlotPage {
    constructor(page, slot) {
        this.page = page
        this.slot = slot
    }

    /**
     * How the page names this slot - the same string the matrix cell and the drawer use.
     */
    get title() {
        const base = `${this.slot.environment.name} · ${this.slot.project.name}`
        return this.slot.qualifier ? `${base} [${this.slot.qualifier}]` : base
    }

    async goTo({tab} = {}) {
        const suffix = tab ? `?tab=${tab}` : ''
        await this.page.goto(`${this.slot.ontrack.connection.ui}/extension/environments/slot/${this.slot.id}${suffix}`)
        await this.expectOnPage()
    }

    async goToSetup() {
        await this.goTo({tab: 'setup'})
        await expect(this.page.getByTestId('slot-setup')).toBeVisible()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId(`slot-${this.slot.id}`)).toBeVisible()
        // The title and the breadcrumb carry the same string, so `first()` rather than a strict
        // match on a locator that legitimately resolves to two elements.
        await expect(this.page.getByText(this.title, {exact: true}).first()).toBeVisible()
    }

    async selectTab(name) {
        await this.page.getByRole('tab', {name, exact: true}).click()
    }

    /**
     * The header block: the slot drawer's Now / In flight / Next, the same component.
     */
    async expectHeaderNow(text) {
        const now = this.page.getByTestId('slot-header-now')
        await expect(now).toBeVisible()
        if (text) await expect(now).toContainText(text)
    }

    async expectHeaderNeverDeployed() {
        await expect(this.page.getByTestId('slot-header-never-deployed')).toBeVisible()
    }

    async expectHeaderHasNoRecentSection() {
        // The Deployments tab is the whole history; five rows of it above the tab holding all of
        // them is the stacked-sections page coming back.
        await expect(this.page.getByTestId('slot-header-recent')).toHaveCount(0)
    }

    async expectSetupTab({visible = true} = {}) {
        await expect(this.page.getByRole('tab', {name: 'Setup', exact: true}))
            .toHaveCount(visible ? 1 : 0)
    }

    async addAdmissionRule({rule, providedName, name, description, config}) {
        await this.goToSetup()
        await this.page.getByTestId('slot-add-rule').click()
        await expect(this.page.getByText('Rule configuration', {exact: true})).toBeVisible()

        if (providedName) {
            await this.page.getByLabel('Name').fill(providedName)
        }

        await this.page.getByLabel('Description').fill(description)
        await this.page.getByLabel('Rule configuration').click()
        await this.page.getByText(rule, {exact: true}).click()

        if (config) {
            await config()
        }

        await this.page.getByRole('button', {name: "OK"}).click()

        await expect(this.page.getByRole('button', {name: "OK"})).not.toBeVisible()
        await expect(this.page.getByText(name, {exact: true})).toBeVisible()
        await expect(this.page.getByText(description, {exact: true})).toBeVisible()
    }

    /**
     * Editing a rule in place - new in #1793. Before it, a rule could only be deleted and added
     * back, which gives it a new id and detaches every answer already given to it.
     */
    async editAdmissionRule(ruleConfigId, {description, config} = {}) {
        await this.goToSetup()
        await this.page.getByTestId(`slot-rule-edit-${ruleConfigId}`).click()
        await expect(this.page.getByText('Rule configuration', {exact: true})).toBeVisible()

        if (description !== undefined) {
            await this.page.getByLabel('Description').fill(description)
        }
        if (config) {
            await config()
        }

        await this.page.getByRole('button', {name: "OK"}).click()
        await expect(this.page.getByRole('button', {name: "OK"})).not.toBeVisible()
    }

    async expectAdmissionRuleEditable(ruleConfigId) {
        await this.goToSetup()
        await expect(this.page.getByTestId(`slot-rule-edit-${ruleConfigId}`)).toBeVisible()
    }

    /**
     * The one action on the slot page itself: the header block's, which is the drawer's.
     *
     * The Deployments tab deliberately carries none - it is the archive, and the deployment page is
     * where a deployment is acted on.
     */
    async startDeployment() {
        const button = this.page.getByTestId('slot-header-start')
        await expect(button).toBeEnabled()
        await button.click()
    }

    async finishDeployment() {
        const button = this.page.getByTestId('slot-header-finish')
        await expect(button).toBeEnabled()
        await button.click()
    }

    async expectInFlight(text) {
        const section = this.page.getByTestId('slot-header-in-flight')
        await expect(section).toBeVisible()
        if (text) await expect(section).toContainText(text)
    }

    async expectNothingInFlight() {
        await expect(this.page.getByTestId('slot-header-in-flight')).toHaveCount(0)
    }

    async delete() {
        await this.goToSetup()
        const button = this.page.getByRole('button', {name: 'Delete slot'})
        await expect(button).toBeVisible()
        await button.click()
        await confirmBox(this.page, "Deleting slot", {okText: "Delete"})
    }

    async getSlotBuilds() {
        await this.selectTab("Eligible builds")
        const slotBuildsSection = this.page.getByTestId("slotBuilds")
        await expect(slotBuildsSection).toBeVisible()
        return new SlotBuilds(this.page, this.slot)
    }

    async getSlotPipelineTable() {
        await this.selectTab("Deployments")
        const table = new SlotPipelineTable(this.page, this.slot)
        await table.expectToBeVisible()
        return table
    }

    /**
     * The Deployments tab's filters, all three handled by the server.
     *
     * `data-testid` on an Ant Design `Input` lands on the `<input>` itself, so the test id *is* the
     * box; on a `Select` it lands on a wrapper, so the option is clicked inside it. Getting that
     * backwards costs the whole timeout, twice over with retries.
     */
    async filterDeploymentsByStatus(label) {
        await this.selectTab("Deployments")
        await this.page.getByTestId('slot-deployments-status').click()
        await this.page.getByTitle(label, {exact: true}).click()
    }

    async filterDeploymentsByBuild(name) {
        await this.selectTab("Deployments")
        const box = this.page.getByTestId('slot-deployments-build')
        await box.fill(name)
        await box.press('Enter')
    }

    async filterDeploymentsByUser(name) {
        await this.selectTab("Deployments")
        const box = this.page.getByTestId('slot-deployments-user')
        await box.fill(name)
        await box.press('Enter')
    }

    async expectDeploymentVisible(pipeline) {
        await expect(this.page.locator(`tr[data-row-key="${pipeline.id}"]`)).toBeVisible()
    }

    async expectDeploymentNotVisible(pipeline) {
        await expect(this.page.locator(`tr[data-row-key="${pipeline.id}"]`)).toHaveCount(0)
    }
}
