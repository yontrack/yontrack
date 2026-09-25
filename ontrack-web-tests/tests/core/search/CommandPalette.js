import {expect} from "@playwright/test";

/**
 * The ⌘K command palette of the desktop UI (#1884).
 *
 * Driven the way a keyboard user drives it: the focus stays in the text field, the arrow keys move
 * the active option, Enter opens it.
 */
export class CommandPalette {

    constructor(page) {
        this.page = page
    }

    dialog() {
        return this.page.getByRole('dialog', {name: 'Search'})
    }

    input() {
        return this.dialog().getByRole('combobox', {name: 'Search'})
    }

    async openByShortcut() {
        await this.page.keyboard.press('ControlOrMeta+K')
        await expect(this.input()).toBeFocused()
    }

    async openBySlash() {
        await this.page.keyboard.press('/')
        await expect(this.input()).toBeFocused()
    }

    async openByButton() {
        await this.page.getByTestId('search-button').click()
        await expect(this.input()).toBeFocused()
    }

    async close() {
        await this.input().press('Escape')
        await expect(this.dialog()).toBeHidden()
    }

    async type(text) {
        await this.input().fill(text)
    }

    /**
     * An option, by its accessible name: `<title>, <type>, in <project>` for a search result.
     */
    option(name) {
        return this.dialog().getByRole('option', {name})
    }

    /**
     * An option of the given group - "Recently visited", "Menu", or `<type> (<count>)`.
     */
    groupOption(group, name) {
        return this.dialog().getByRole('group', {name: group}).getByRole('option', {name})
    }

    /**
     * The option of a search result opening the given page.
     *
     * The result draws the links of the per-type component, inert inside the option: they tell the
     * option apart when its name does not - the same commit in two projects, for example.
     */
    resultOptionTo(href) {
        return this.dialog().getByRole('option').filter({has: this.page.locator(`a[href="${href}"]`)})
    }

    /**
     * Waits for an option to be listed. The palette searches once per text, and the index may be a
     * moment behind the data just created: the text is typed again until the option shows.
     */
    async expectOption(option, text) {
        await expect(async () => {
            try {
                await expect(option).toBeVisible({timeout: 3_000})
            } catch (e) {
                await this.input().fill('')
                await this.input().fill(text)
                throw e
            }
        }).toPass({timeout: 30_000})
    }

    /**
     * Moves to the option with the arrow keys, and opens it with Enter.
     */
    async openWithEnter(option) {
        for (let i = 0; i < 50; i++) {
            if (await option.getAttribute('aria-selected') === 'true') break
            await this.input().press('ArrowDown')
        }
        await expect(option).toHaveAttribute('aria-selected', 'true')
        await this.input().press('Enter')
        await expect(this.dialog()).toBeHidden()
    }
}
