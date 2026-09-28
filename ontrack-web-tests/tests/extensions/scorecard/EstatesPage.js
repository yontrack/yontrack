import {expect} from "@playwright/test";
import {confirmBox} from "../../support/confirm";
import {labelDisplay} from "../../support/labels";

/**
 * The estates admin page, `/extension/scorecard/estates`.
 */
export class EstatesPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/extension/scorecard/estates`)
        await this.expectOnPage()
    }

    async expectOnPage() {
        await expect(this.page.getByTestId('estates')).toBeVisible()
    }

    dialog() {
        return this.page.getByTestId('estate-dialog')
    }

    /**
     * Fills the estate dialog. Every field is optional: only the given ones are changed.
     *
     * @param name Name of the estate
     * @param description Description
     * @param labels Labels to add, as label objects
     * @param marker `{kind: 'Default'}`, `{kind: 'Promotion level', levelName}` or
     *   `{kind: 'Environment', environment, qualifier}`
     * @param readings By reading name, `{window, target, unit}`, `null` to clear a field
     */
    async fill({name, description, labels = [], marker, readings = {}}) {
        const dialog = this.dialog()
        await expect(dialog).toBeVisible()
        if (name !== undefined) await dialog.getByLabel('Name', {exact: true}).fill(name)
        if (description !== undefined) await dialog.getByLabel('Description', {exact: true}).fill(description)
        for (const label of labels) {
            const select = dialog.getByLabel('Labels', {exact: true})
            await select.click()
            await select.fill(labelDisplay(label))
            // The display string being unique, the first matching option is the only one
            await select.press('Enter')
            await select.press('Escape')
        }
        if (marker) {
            await dialog.getByText(marker.kind, {exact: true}).click()
            if (marker.levelName !== undefined) {
                await dialog.getByLabel('Level name', {exact: true}).fill(marker.levelName)
            }
            if (marker.environment !== undefined) {
                await dialog.getByLabel('Environment name', {exact: true}).fill(marker.environment)
            }
            if (marker.qualifier !== undefined) {
                await dialog.getByLabel('Qualifier', {exact: true}).fill(marker.qualifier)
            }
        }
        for (const [reading, {window, target, unit}] of Object.entries(readings)) {
            if (window !== undefined) {
                await dialog.getByLabel(`Window of ${reading}`, {exact: true}).fill(window === null ? '' : String(window))
            }
            if (target !== undefined) {
                await dialog.getByLabel(`Target of ${reading}`, {exact: true}).fill(target === null ? '' : String(target))
            }
            if (unit !== undefined) {
                await dialog.getByLabel(`Unit of the target of ${reading}`, {exact: true}).click()
                await this.page.locator('.ant-select-dropdown:visible .ant-select-item-option')
                    .filter({hasText: new RegExp(`^${unit}$`)})
                    .click()
            }
        }
    }

    async submit() {
        await this.dialog().getByRole('button', {name: 'OK', exact: true}).click()
        await expect(this.dialog()).not.toBeVisible()
    }

    async create(values) {
        await this.page.getByTestId('estate-create').click()
        await this.fill(values)
        await this.submit()
    }

    async edit(name, values) {
        await this.page.getByTestId(`estate-edit-${name}`).click()
        await this.fill(values)
        await this.submit()
    }

    async recompute(name) {
        await this.page.getByTestId(`estate-recompute-${name}`).click()
    }

    async delete(name) {
        await this.page.getByTestId(`estate-delete-${name}`).click()
        await confirmBox(this.page, `Do you really want to delete the "${name}" estate?`, {okText: 'Delete'})
    }

    row(name) {
        return this.page.getByRole('row').filter({has: this.page.getByTestId(`estate-name-${name}`)})
    }

    marker(name) {
        return this.page.getByTestId(`estate-marker-${name}`)
    }

    reading(name, key) {
        return this.page.getByTestId(`estate-reading-${name}-${key}`)
    }

    projects(name) {
        return this.page.getByTestId(`estate-projects-${name}`)
    }

    computed(name) {
        return this.page.getByTestId(`estate-computed-${name}`)
    }
}
