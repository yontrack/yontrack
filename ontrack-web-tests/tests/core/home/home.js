import {expect} from "@playwright/test";
import {EnvironmentsPage} from "../../extensions/environments/Environments";
import {SearchPage} from "../search/SearchPage";

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

    async search(token) {
        const searchBox = this.page.getByRole('searchbox')
        await searchBox.click()
        await searchBox.fill(token)
        await searchBox.press('Enter')

        const searchPage = new SearchPage(this.page, this.ontrack)
        await searchPage.expectOnPage()

        return searchPage
    }

}