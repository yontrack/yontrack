import {expect} from "@playwright/test";
import {generate} from "@ontrack/utils";
import {login} from "../../core/login";
import {test} from "../../fixtures/connection";

/**
 * The two dashboard widgets, which since #1791 draw the same matrix as the home page.
 *
 * They keep their widget keys - those live inside people's dashboards - so what is worth asserting
 * is that each still renders and that what it renders is now a slot *cell*: the same reading as the
 * matrix, not the two older ones it replaced (a table of environments down the side, and a card per
 * slot).
 */
test('both environments widgets draw the matrix', async ({page, ontrack}) => {
    const prefix = generate('wdg')
    const staging = await ontrack.environments.createEnvironment({order: 100})
    const production = await ontrack.environments.createEnvironment({order: 200})
    const project = await ontrack.createProject(`${prefix}-app`)
    const stagingSlot = await staging.createSlot({project})
    const productionSlot = await production.createSlot({project})

    const dashboardName = `${prefix}-dashboard`
    const yaml = [
        `- name: "${dashboardName}"`,
        `  widgets:`,
        `    - key: "extension/environments/EnvironmentList"`,
        `      layout: {x: 0, y: 0, w: 12, h: 25}`,
        `      config:`,
        `        title: "Deployments"`,
        `        tags: []`,
        `        projects:`,
        `          - "${project.name}"`,
        `        rowLimit: 5`,
        `    - key: "extension/environments/Environment"`,
        `      layout: {x: 0, y: 25, w: 12, h: 25}`,
        `      config:`,
        `        name: "${production.name}"`,
        `        projects:`,
        `          - "${project.name}"`,
    ].join('\n')

    await login(page, ontrack)

    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Import dashboards as YAML').click()
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()
    await modal.locator('textarea').fill(yaml)
    await modal.getByRole('button', {name: 'Import'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()

    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(dashboardName).click()

    // The list widget shows both columns, so both cells of the project's row are drawn. Each slot
    // appears once per widget, hence the counts rather than `toBeVisible`.
    await expect(page.getByTestId(`slot-cell-${stagingSlot.id}`)).toHaveCount(1)
    // The environment widget is a matrix of one column, so the production slot is drawn twice: once
    // by each widget. That is the assertion - the second widget renders, and renders a cell.
    await expect(page.getByTestId(`slot-cell-${productionSlot.id}`)).toHaveCount(2)
})
