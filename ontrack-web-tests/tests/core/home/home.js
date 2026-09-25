import {expect} from "@playwright/test";
import {EnvironmentsPage} from "../../extensions/environments/Environments";
import {CommandPalette} from "../search/CommandPalette";

export class HomePage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async checkOnPage() {
        await expect(this.page.getByRole('button', {name: 'New project'})).toBeVisible()
    }

    async newProject({name, description, disabled}) {
        await this.page.getByRole('button', {name: 'New project'}).click()
        await expect(this.page.getByPlaceholder('Project name')).toBeVisible()
        await this.page.getByPlaceholder('Project name').fill(name)
        if (description) await this.page.getByPlaceholder('Project description').fill(name)
        if (disabled === true) await this.page.getByLabel('Disabled').click()
        await this.page.getByRole('button', {name: 'OK'}).click()
    }

    async selectEnvironments() {
        await this.page.getByRole('button', {name: 'Environments'}).click()
        const environmentsPage = new EnvironmentsPage(this.page, this.ontrack)
        await environmentsPage.expectOnPage()
        return environmentsPage
    }

    /**
     * Opens the command palette from the navigation bar, and types the text into it.
     */
    async search(text) {
        const palette = new CommandPalette(this.page)
        await palette.openByButton()
        await palette.type(text)
        return palette
    }

}