import {expect} from "@playwright/test";
import {EnvironmentDialog, SlotDialog} from "./EnvironmentDialogs";

/**
 * The Setup page (#1793) - `/extension/environments/setup`.
 *
 * Where creating and deleting environments and slots moved to, out of the operational screens. It is
 * also the only screen of the feature allowed to say *slot*, which is why the assertions below read
 * the way they do.
 */
export class SetupPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/environments/setup`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('setup-tabs')).toBeVisible()
    }

    /**
     * The tabs are Ant Design's, so each is a `tab` role - not a button and not a link.
     */
    async selectTab(name) {
        await this.page.getByRole('tab', {name, exact: true}).click()
    }

    async createEnvironment({name, description, order, tags}) {
        await this.selectTab("Environments")
        await this.page.getByTestId('setup-new-environment').click()
        const dialog = new EnvironmentDialog(this.page)
        await dialog.set({name, description, order, tags})
        await dialog.ok()
    }

    async createSlot({projectName, qualifier, description, environmentNames}) {
        await this.selectTab("Slots")
        await this.page.getByTestId('setup-new-slot').click()
        const dialog = new SlotDialog(this.page)
        await dialog.set({projectName, qualifier, description, environmentNames})
        await dialog.ok()
    }

    async expectEnvironmentRow(environment) {
        await this.selectTab("Environments")
        await expect(this.page.getByTestId(`setup-environment-${environment.id}`)).toBeVisible()
    }

    async expectEnvironmentSlotCount(environment, count) {
        await this.selectTab("Environments")
        await expect(this.page.getByTestId(`setup-environment-slots-${environment.id}`))
            .toHaveText(String(count))
    }

    async expectSlotRow(slot) {
        await this.selectTab("Slots")
        await expect(this.page.getByTestId(`setup-slot-${slot.id}`)).toBeVisible()
    }

    async expectSlotCounts(slot, {rules, workflows}) {
        await this.selectTab("Slots")
        await expect(this.page.getByTestId(`setup-slot-rules-${slot.id}`)).toHaveText(String(rules))
        await expect(this.page.getByTestId(`setup-slot-workflows-${slot.id}`)).toHaveText(String(workflows))
    }

    /**
     * The link a Slots row carries: the slot's own page, opened straight on its Setup tab.
     */
    async openSlotSetup(slot) {
        await this.selectTab("Slots")
        await this.page.getByTestId(`setup-slot-link-${slot.id}`).click()
    }

    /**
     * The "still under experiment" note, which lives here and nowhere else since #1793.
     */
    async expectExperimentalNote() {
        await expect(this.page.getByText(/still.*under experiment/i)).toBeVisible()
    }

    async expectCannotCreateEnvironment() {
        await this.selectTab("Environments")
        await expect(this.page.getByTestId('setup-new-environment')).toHaveCount(0)
    }

    async expectCannotCreateSlot() {
        await this.selectTab("Slots")
        await expect(this.page.getByTestId('setup-new-slot')).toHaveCount(0)
    }
}
