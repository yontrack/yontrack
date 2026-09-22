import {expect} from "@playwright/test";
import {waitUntilCondition} from "../../support/timing";
import {dragOnto} from "../../support/drag";

// Each validation stamp is a list item whose test id carries its name
const ITEM_TEST_ID_PREFIX = 'validation-stamp-item-'

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

    getListItem(name) {
        return this.page.getByTestId(`${ITEM_TEST_ID_PREFIX}${name}`)
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
        // Scope the confirmation to the dialog to avoid matching list item Delete buttons. The visible
        // title: antd 6 renders it twice, as the dialog's label and as its heading
        const dialog = this.page.getByRole('dialog')
        await expect(dialog.getByText(`Delete "${name}"?`, {exact: true}).filter({visible: true})).toBeVisible()
        await dialog.getByRole('button', {name: "Delete"}).click()
        await expect(dialog).not.toBeVisible()
    }

    // Returns the validation stamp names in display order
    async getValidationStampNames() {
        const testIds = await this.page
            .getByTestId(new RegExp(`^${ITEM_TEST_ID_PREFIX}`))
            .evaluateAll(items => items.map(item => item.dataset.testid))
        return testIds.map(testId => testId.substring(ITEM_TEST_ID_PREFIX.length))
    }

    getDragHandle(name) {
        return this.getListItem(name).getByText('☰')
    }

    async dragToReorder(fromName, toName) {
        await dragOnto(this.page, this.getDragHandle(fromName), this.getDragHandle(toName))
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
