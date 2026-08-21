import {expect} from "@playwright/test";

/**
 * Dialog used to set the interval and the period of the charts of a page.
 *
 * Shared by the promotion level and validation stamp pages.
 */
export class ChartOptionsDialog {

    constructor(page) {
        this.page = page
        this.dialog = this.page.getByTestId("chart-options-dialog")
    }

    /**
     * Sets the options and submits the dialog.
     *
     * @param interval id of the interval, like "1m"
     * @param period id of the period, like "1d"
     */
    async setOptions({interval, period}) {
        await expect(this.dialog).toBeVisible()

        await this.selectOption("Interval", interval)
        await this.selectOption("Period", period)

        await this.dialog.getByRole('button', {name: "OK"}).click()
        await expect(this.dialog).not.toBeVisible()
    }

    /**
     * Selects an option in one of the AntD selects of the dialog, using the id
     * of the option, displayed between parentheses, like "One month (1m)".
     */
    async selectOption(label, id) {
        const combobox = this.page.getByRole('combobox', {name: label})
        // Forced click: the AntD Select displays its current value in a span
        // which intercepts the pointer events aimed at the combobox itself.
        await combobox.click({force: true})
        // The dropdown of this select is identified by the list it contains, the one
        // referenced by the combobox: the dropdown of a previous select can still be
        // in the DOM, not yet marked as hidden while it closes.
        const listId = await combobox.getAttribute('aria-controls')
        expect(listId, `No list associated with the "${label}" select`).toBeTruthy()
        const dropdown = this.page.locator(`.ant-select-dropdown:has(#${listId})`)
        await dropdown.waitFor({state: 'visible'})
        // The [role=option] elements of the dropdown belong to a hidden list, used
        // for accessibility only: the actual options carry no role.
        // The event is dispatched directly: scrolling an option into view makes the
        // virtual list re-render and detach it, and a regular click never settles.
        await dropdown
            .locator('.ant-select-item-option')
            .filter({hasText: `(${id})`})
            .dispatchEvent('click')
    }

}
