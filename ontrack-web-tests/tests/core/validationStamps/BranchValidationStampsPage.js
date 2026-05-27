import {expect} from "@playwright/test";
import {waitUntilCondition} from "../../support/timing";

export class BranchValidationStampsPage {

    constructor(page, branch) {
        this.page = page
        this.branch = branch
    }

    async goTo() {
        await this.page.goto(`${this.branch.ontrack.connection.ui}/branch/${this.branch.id}/validationStamps`)
        await this.checkOnPage()
    }

    async checkOnPage() {
        await expect(this.page.getByText("Validation stamps", {exact: true})).toBeVisible()
        await expect(this.page.getByText(this.branch.name, {exact: true})).toBeVisible()
    }

    async createValidationStamp({name}) {
        const createButton = this.page.getByRole('button', {name: "Create validation stamp"})
        await expect(createButton).toBeVisible()
        await createButton.click()
        await this.page.getByLabel("Name").fill(name)
        await this.page.getByRole('button', {name: "OK"}).click()
    }

    // Each list item contains a link whose text is "{initials}{name}" due to GeneratedIcon.
    // We locate items using hasText on the name portion.
    getListItem(name) {
        return this.page.locator('.ant-list-item').filter({hasText: name})
    }

    async checkValidationStampVisible({name}) {
        await expect(this.getListItem(name)).toBeVisible()
    }

    async checkValidationStampNotVisible({name}) {
        await expect(this.getListItem(name)).not.toBeVisible()
    }

    async deleteValidationStamp({name}) {
        const listItem = this.getListItem(name)
        await listItem.getByRole('button', {name: "Delete"}).click()
        // Scope the confirmation to the dialog to avoid matching list item Delete buttons
        const dialog = this.page.getByRole('dialog')
        await expect(dialog.getByText(`Delete "${name}"?`, {exact: true})).toBeVisible()
        await dialog.getByRole('button', {name: "Delete"}).click()
        await expect(dialog).not.toBeVisible()
    }

    // Returns the validation stamp names in display order.
    // Each list item's link contains: GeneratedIcon (initials) + name text as two ant-space-items.
    // We take the second ant-space-item (index 1) to get just the name.
    async getValidationStampNames() {
        const items = this.page.locator('.ant-list-item')
        const count = await items.count()
        const names = []
        for (let i = 0; i < count; i++) {
            const nameSpan = items.nth(i).locator('a').nth(0).locator('.ant-space-item').nth(1)
            names.push((await nameSpan.textContent()).trim())
        }
        return names
    }

    getDragHandle(name) {
        return this.getListItem(name).getByText('☰')
    }

    async dragToReorder(fromName, toName) {
        const fromHandle = this.getDragHandle(fromName)
        const toHandle = this.getDragHandle(toName)

        const fromBox = await fromHandle.boundingBox()
        const toBox = await toHandle.boundingBox()

        const fromX = fromBox.x + fromBox.width / 2
        const fromY = fromBox.y + fromBox.height / 2
        const toX = toBox.x + toBox.width / 2
        const toY = toBox.y + toBox.height / 2

        await this.page.mouse.move(fromX, fromY)
        await this.page.mouse.down()
        await this.page.mouse.move(toX, toY, {steps: 10})
        await this.page.mouse.up()
    }

    async waitForOrder(expectedNames) {
        await waitUntilCondition({
            page: this.page,
            condition: async () => {
                const names = await this.getValidationStampNames()
                return JSON.stringify(names) === JSON.stringify(expectedNames)
            },
            timeout: 10000,
            message: `Validation stamps order to be ${JSON.stringify(expectedNames)}`,
        })
    }
}
