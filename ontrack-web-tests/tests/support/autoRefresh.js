const {expect} = require("@playwright/test");

/**
 * The `AutoRefreshButton` of a page - the toggle, and the menu of its intervals.
 *
 * Shared because the control is the same component wherever it shows, and a page object per view
 * that redescribed it would drift from it one by one.
 */
class AutoRefreshControl {

    constructor(page) {
        this.page = page
        // Through the accessible button rather than the class alone: the builds table renders its
        // header twice, the second copy in antd's aria-hidden measure row, which role queries skip
        this.control = page.locator('.ot-auto-refresh')
            .filter({has: page.getByRole('button', {name: 'Auto refresh'})})
        this.toggle = this.control.getByRole('button', {name: 'Auto refresh'})
        this.intervalButton = this.control.getByRole('button', {name: 'Refresh interval'})
    }

    async checkEnabled() {
        await expect(this.control).toHaveClass(/ot-auto-refresh-enabled/)
    }

    async checkDisabled() {
        await expect(this.control).toBeVisible()
        await expect(this.control).not.toHaveClass(/ot-auto-refresh-enabled/)
    }

    /**
     * Picks the interval, then turns the refresh on.
     *
     * @param interval Text of the interval's menu item, e.g. "Every 5 seconds"
     */
    async enable({interval = "Every 5 seconds"} = {}) {
        // The interval menu opens on hover, antd's default for a dropdown
        await this.intervalButton.hover()
        const item = this.page.getByRole('menuitem', {name: interval})
        await expect(item).toBeVisible()
        await item.click()
        await this.toggle.click()
        await this.checkEnabled()
    }
}

module.exports = {AutoRefreshControl}
