import {expect} from "@playwright/test";

/**
 * Both dialogs have a **Description**, and an Ant Design `Form.Item` names its control after the
 * field - so once each dialog has been opened once, two `id="description"` inputs are in the page.
 * A closed Ant Design modal stays mounted, and a duplicated id makes `label[for="description"]`
 * point at whichever one is *first* in the document, which is the one belonging to the dialog that
 * was opened first and is now hidden. Looking a Description up by its label therefore finds an
 * invisible field and waits out the whole timeout, and scoping the label lookup to the visible
 * dialog does not help - the association is computed against the document, not against the scope.
 *
 * So the Description is reached by its id *within* the open dialog: a plain CSS lookup inside a
 * subtree, which has no such document-wide behaviour. Every other field of these two dialogs has a
 * name of its own and is left alone.
 */
const descriptionField = (dialog) => dialog.locator('#description')

export class EnvironmentDialog {
    constructor(page) {
        this.page = page
    }

    async set({name, description, order, tags}) {
        const dialog = this.page.getByRole('dialog')
        await expect(dialog.getByLabel('Name')).toBeVisible()
        await dialog.getByLabel('Name').fill(name)
        await descriptionField(dialog).fill(description)
        await dialog.getByLabel('Order').fill(order.toString())
        const tagsField = dialog.getByTestId('tags')
        for (const tag of tags) {
            await tagsField.click()
            await tagsField.type(tag)
        }
    }

    async ok() {
        await this.page.getByRole('button', {name: 'OK'}).click()
        // Waiting for the dialog to be gone
        await expect(this.page.getByRole('button', {name: 'OK'})).toHaveCount(0)
    }
}

export class SlotDialog {
    constructor(page) {
        this.page = page
    }

    async set({projectName, qualifier, description, environmentNames}) {
        const dialog = this.page.getByRole('dialog')
        await expect(dialog.getByLabel('Project', {exact: true})).toBeVisible()

        await this.page.getByTestId('projectId').getByLabel('Project').click()
        await this.page.getByTestId('projectId').getByLabel('Project').fill(projectName)
        await this.page.getByTitle(projectName, {exact: true}).locator('div').click()

        if (qualifier) {
            await this.page.getByLabel('Qualifier').fill(qualifier)
        }

        if (description) {
            await descriptionField(dialog).fill(description)
        }

        /*
         * Reached by its id inside the open dialog, not by its label. `getByLabel('Environments')`
         * was enough while the only thing called Environments was this field; since #1793 the Setup
         * page has a tab panel of that name, and a page-wide label lookup resolves to both - which
         * is a strict-mode violation, not a wrong click, so it fails rather than misbehaving.
         */
        const environments = dialog.locator('#environmentIds')
        await environments.click()
        for (const environmentName of environmentNames) {
            await environments.type(environmentName)
            await environments.press('Enter')
        }
        await environments.press('Escape')
    }

    async ok() {
        await this.page.getByRole('button', {name: 'OK'}).click()
        // Waiting for the dialog to be gone
        await expect(this.page.getByRole('button', {name: 'OK'})).toHaveCount(0)
    }
}
