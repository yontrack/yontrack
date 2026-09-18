import {expect} from "@playwright/test";

/**
 * The slot drawer - the shared "what is going on in this slot?" quick view (#1797).
 *
 * It is addressed by `?slot=<id>` rather than by a local open/closed flag, so a test can check not
 * only that a click opens it but that the address it opens at brings it back after a reload. That is
 * the property the URL exists for.
 */
export class SlotDrawerPanel {

    constructor(page, slot) {
        this.page = page
        this.slot = slot
    }

    async expectOpen() {
        await expect(this.page.getByTestId(`slot-drawer-${this.slot.id}`)).toBeVisible()
    }

    async expectClosed() {
        await expect(this.page.getByTestId(`slot-drawer-${this.slot.id}`)).toHaveCount(0)
    }

    async expectTitle(text) {
        await expect(this.page.getByTestId('slot-drawer-title')).toContainText(text)
    }

    async expectNeverDeployed() {
        await expect(this.page.getByTestId('slot-drawer-never-deployed')).toBeVisible()
    }

    async expectInFlight(buildName) {
        const section = this.page.getByTestId('slot-drawer-in-flight')
        await expect(section).toBeVisible()
        await expect(section).toContainText(buildName)
    }

    async expectNothingInFlight() {
        await expect(this.page.getByTestId('slot-drawer-in-flight')).toHaveCount(0)
    }

    async expectRecentDeployment(number) {
        await expect(this.page.getByTestId(`slot-drawer-recent-${number}`)).toBeVisible()
    }

    async expectNextBuild(build) {
        await expect(this.page.getByTestId(`slot-drawer-next-${build.id}`)).toBeVisible()
    }

    async expectNoNextBuild() {
        await expect(this.page.getByTestId('slot-drawer-next-none')).toBeVisible()
    }

    async expectFreshness() {
        await expect(this.page.getByTestId('slot-drawer-freshness-age')).toContainText('Updated')
    }

    /**
     * The drawer's own address - what a shared link looks like.
     */
    async goToDirectly(uiUrl) {
        await this.page.goto(`${uiUrl}/extension/environments/environments?slot=${this.slot.id}`)
    }
}
