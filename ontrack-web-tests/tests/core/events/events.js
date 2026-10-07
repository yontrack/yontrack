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

    /**
     * Downloads the CSV export of the filtered events, and returns its rows, header first.
     */
    async downloadCsv() {
        const downloadPromise = this.page.waitForEvent('download')
        await this.page.getByTestId('events-export-csv').click()
        const download = await downloadPromise
        expect(download.suggestedFilename()).toMatch(/^yontrack-events-\d{8}-\d{6}\.csv$/)
        const stream = await download.createReadStream()
        const chunks = []
        for await (const chunk of stream) {
            chunks.push(chunk)
        }
        return parseCsv(Buffer.concat(chunks).toString('utf-8'))
    }
}

/**
 * Rows of a CSV, whose values may be quoted, with the quotes doubled inside.
 */
const parseCsv = (text) => {
    const rows = []
    let row = []
    let value = ''
    let quoted = false
    for (let i = 0; i < text.length; i++) {
        const c = text[i]
        if (quoted) {
            if (c === '"' && text[i + 1] === '"') {
                value += '"'
                i++
            } else if (c === '"') {
                quoted = false
            } else {
                value += c
            }
        } else if (c === '"') {
            quoted = true
        } else if (c === ',') {
            row.push(value)
            value = ''
        } else if (c === '\n') {
            row.push(value)
            rows.push(row)
            row = []
            value = ''
        } else if (c !== '\r') {
            value += c
        }
    }
    if (value || row.length > 0) {
        row.push(value)
        rows.push(row)
    }
    return rows
}
