import {expect} from "@playwright/test";
import {confirmBox} from "../../support/confirm";
import {getTable} from "../../support/antd-table-support";

/**
 * Display string of a label, the way the UI renders it in a chip.
 */
export const labelDisplay = ({category, name}) => category ? `${category}:${name}` : name

/**
 * The labels admin page, at `core/config/labels`.
 */
export class LabelsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/core/config/labels`)
        await expect(this.page.getByTestId("labels")).toBeVisible()
    }

    /**
     * Creates a label using the "New label" command and its dialog.
     */
    async createLabel({category, name, description}) {
        await this.page.getByRole('button', {name: 'New label'}).click()
        await this.fillLabelForm({category, name, description})
        await this.page.getByRole('button', {name: 'OK', exact: true}).click()
    }

    /**
     * Edits a label using the pencil of its row.
     */
    async editLabel(label, {category, name, description}) {
        await this.page.getByTestId(`label-edit-${labelDisplay(label)}`).click()
        await this.fillLabelForm({category, name, description})
        await this.page.getByRole('button', {name: 'OK', exact: true}).click()
    }

    async fillLabelForm({category, name, description}) {
        const categoryField = this.page.getByPlaceholder('Optional category of the label')
        await expect(categoryField).toBeVisible()
        if (category !== undefined) await categoryField.fill(category)
        if (name !== undefined) await this.page.getByPlaceholder('Name of the label').fill(name)
        if (description !== undefined) {
            await this.page.getByPlaceholder('Optional description of the label').fill(description)
        }
    }

    /**
     * Deletes a label, checking on the way that the confirmation says how many
     * projects carry it.
     */
    async deleteLabel(label, {confirmationText} = {}) {
        const display = labelDisplay(label)
        await this.page.getByTestId(`label-delete-${display}`).click()
        if (confirmationText) {
            await expect(this.page.getByText(confirmationText, {exact: true})).toBeVisible()
        }
        await confirmBox(
            this.page,
            `Do you really want to delete the "${display}" label?`,
            {okText: "Delete"},
        )
    }

    chip(label) {
        return this.page.getByTestId(`label-${labelDisplay(label)}`)
    }

    async expectLabel(label) {
        await expect(this.chip(label)).toBeVisible()
    }

    async expectNoLabel(label) {
        await expect(this.chip(label)).not.toBeVisible()
    }

    /**
     * Filters the table on a category or a name.
     */
    async filter(text) {
        await this.page.getByLabel('Category or name').fill(text)
        await this.page.getByRole('button', {name: 'Filter', exact: true}).click()
    }

    /**
     * The number of projects displayed for a label, as text.
     */
    async projectCount(label) {
        const table = await getTable(this.page, "labels")
        const row = await table.getRow(labelDisplay(label))
        const cell = await row.getCell("Projects")
        return (await cell.textContent()).trim()
    }
}
