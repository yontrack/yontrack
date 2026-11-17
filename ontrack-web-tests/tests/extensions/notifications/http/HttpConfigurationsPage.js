import {expect} from "@playwright/test";

export class HttpConfigurationsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/notifications/http/configurations`)
        await expect(this.page.getByText("HTTP configurations")).toBeVisible()
        await expect(this.page.getByText("Create config")).toBeVisible()
    }

    async createConfig({name, url, authenticationType, fill}) {
        await this.page.getByText("Create config").click()
        await this.page.getByLabel("Configuration name").fill(name)
        await this.page.getByLabel("Base URL").fill(url)
        if (authenticationType) {
            await this.selectAuthenticationType(authenticationType)
        }
        if (fill) {
            await fill(this.page)
        }
        await this.page.getByRole("button", {name: "OK"}).click()
    }

    async selectAuthenticationType(label) {
        const combobox = this.page.getByRole('combobox', {name: 'Authentication type'})
        // Forced click: the AntD Select displays its current value in a span
        // which intercepts the pointer events aimed at the combobox itself.
        await combobox.click({force: true})
        const listId = await combobox.getAttribute('aria-controls')
        expect(listId, `No list associated with the authentication type select`).toBeTruthy()
        const dropdown = this.page.locator(`.ant-select-dropdown:has(#${listId})`)
        await dropdown.waitFor({state: 'visible'})
        await dropdown
            .locator('.ant-select-item-option')
            .filter({hasText: new RegExp(`^${label}$`)})
            .dispatchEvent('click')
    }

    async checkConfiguration({name, url, authenticationType}) {
        const row = this.page.locator(`tr[data-row-key="config-${name}"]`)
        await expect(row.getByText(url, {exact: true})).toBeVisible()
        await expect(row.getByText(authenticationType, {exact: true})).toBeVisible()
    }
}
