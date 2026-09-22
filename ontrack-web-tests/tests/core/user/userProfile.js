import {checkListContainsItemText} from "../../support/list-support";

const {expect} = require("@playwright/test");

export class UserProfilePage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }


    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/core/admin/userProfile`)
        await expect(this.page.getByText("API tokens")).toBeVisible()
    }

    async generateToken(tokenName) {
        await this.page.getByPlaceholder("Token name").fill(tokenName)
        await this.page.getByText("Generate token", {exact: true}).click()

        // Reading from the clipboard is not supported in Firefox
        // await this.page.getByTitle("Copies the generated token into the clipboard.").click()
        // const text = await this.page.evaluate(() => navigator.clipboard.readText())

        return this.page.getByTestId("generatedToken").textContent()
    }

    async checkGroups({assignedGroups, mappedGroups, idpGroups}) {
        if (assignedGroups) {
            const list = this.page.getByTestId("assigned-groups")
            for (const group of assignedGroups) {
                await checkListContainsItemText(list, group);
            }
        }
        if (mappedGroups) {
            const list = this.page.getByTestId("mapped-groups")
            for (const group of mappedGroups) {
                await checkListContainsItemText(list, group)
            }
        }
        if (idpGroups) {
            const list = this.page.getByTestId("idp-groups")
            for (const group of idpGroups) {
                await checkListContainsItemText(list, group)
            }
        }
    }
}
