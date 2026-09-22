import {expect} from "@playwright/test";
import {waitUntilCondition} from "../../support/timing";
import {dragOnto} from "../../support/drag";

// Each promotion level is a list item whose test id carries its name
const ITEM_TEST_ID_PREFIX = 'promotion-level-item-'

export class BranchPromotionLevelsPage {

    constructor(page, branch) {
        this.page = page
        this.branch = branch
    }

    async goTo() {
        await this.page.goto(`${this.branch.ontrack.connection.ui}/branch/${this.branch.id}/promotionLevels`)
        await this.checkOnPage()
    }

    async checkOnPage() {
        await expect(this.page.getByText("Promotion levels", {exact: true})).toBeVisible()
        await expect(this.page.getByText(this.branch.name, {exact: true})).toBeVisible()
    }

    getListItem(name) {
        return this.page.getByTestId(`${ITEM_TEST_ID_PREFIX}${name}`)
    }

    // Returns the promotion level names in display order
    async getPromotionLevelNames() {
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
                const names = await this.getPromotionLevelNames()
                return JSON.stringify(names) === JSON.stringify(expectedNames)
            },
            timeout: 10000,
            message: `Promotion levels order to be ${JSON.stringify(expectedNames)}`,
        })
    }
}
