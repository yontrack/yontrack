import {expect} from "@playwright/test";
import {getTable} from "../../support/antd-table-support";

/**
 * The events page, under Information › Events.
 */
export class EventsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/core/admin/events`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId("events")).toBeVisible()
    }

    async table() {
        return getTable(this.page, "events")
    }

    /**
     * Rows of the table, past antd's hidden measure row and its placeholder row.
     */
    rows() {
        return this.page.getByTestId("events").locator('tbody tr.ant-table-row')
    }

    /**
     * Row whose message contains the given text.
     */
    row(text) {
        return this.rows().filter({hasText: text})
    }

    async selectEventTypes(eventTypes) {
        const select = this.page.locator('#events-filter-event-types')
        // Removing the event types selected before
        const clear = this.page.getByTestId('events-filter-event-types').locator('.ant-select-clear')
        if (await clear.count() > 0) {
            await this.page.getByTestId('events-filter-event-types').hover()
            await clear.click()
        }
        for (const eventType of eventTypes) {
            await select.fill(eventType)
            await this.page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText: eventType}).first().click()
        }
        await this.page.keyboard.press('Escape')
    }

    /**
     * Sets the time range, as `YYYY-MM-DD HH:mm` texts.
     */
    async setRange(from, to) {
        const start = this.page.getByPlaceholder('Start date')
        await start.click()
        await start.fill(from)
        await this.page.keyboard.press('Enter')
        await this.page.getByPlaceholder('End date').fill(to)
        await this.page.keyboard.press('Enter')
    }

    async setUser(user) {
        await this.page.getByTestId('events-filter-user').fill(user)
    }

    async selectProject(name) {
        const select = this.page.locator('#events-filter-project')
        await select.fill(name)
        await this.page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText: name}).first().click()
    }

    async filter() {
        await this.page.getByRole('button', {name: 'Filter', exact: true}).click()
    }
}
