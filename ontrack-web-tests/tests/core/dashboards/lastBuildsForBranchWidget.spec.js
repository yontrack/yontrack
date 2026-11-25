const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");

/**
 * Imports a dashboard holding only the "Last builds for a branch" widget, and selects it.
 *
 * @param page Playwright page
 * @param configLines Lines of the `config` block of the widget, without their indentation
 */
const importAndSelectDashboard = async (page, configLines) => {
    const dashboardName = generate('last-builds-')
    const yaml = [
        `- name: "${dashboardName}"`,
        `  widgets:`,
        `    - key: "home/LastBuildsForBranch"`,
        `      layout: {x: 0, y: 0, w: 12, h: 30}`,
        `      config:`,
        ...configLines.map(line => `        ${line}`),
    ].join('\n')

    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Import dashboards as YAML').click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await dialog.locator('textarea').fill(yaml)
    await dialog.getByRole('button', {name: 'Import'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()

    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(dashboardName, {exact: true}).click()
}

test('the last builds widget shows the last N builds with all their promotions and validations', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const silver = await branch.createPromotionLevel()
    const gold = await branch.createPromotionLevel()
    const unit = await branch.createValidationStamp()
    const integration = await branch.createValidationStamp()

    const oldest = await branch.createBuild()
    const middle = await branch.createBuild()
    const latest = await branch.createBuild()

    const silverRun = await latest.promote(silver)
    const goldRun = await middle.promote(gold)
    await latest.validate(unit)
    await middle.validate(integration)

    await login(page, ontrack)
    await importAndSelectDashboard(page, [
        `project: "${project.name}"`,
        `branch: "${branch.name}"`,
        `count: 2`,
    ])

    const table = page.getByTestId('last-builds-for-branch')
    await expect(table).toBeVisible()

    // Only the two latest builds
    await expect(table.getByRole('link', {name: latest.name, exact: true})).toBeVisible()
    await expect(table.getByRole('link', {name: middle.name, exact: true})).toBeVisible()
    await expect(table.getByRole('link', {name: oldest.name, exact: true})).toHaveCount(0)

    // All the promotions
    await expect(table.getByTestId(`promotion-run-link-${silverRun.id}`)).toBeVisible()
    await expect(table.getByTestId(`promotion-run-link-${goldRun.id}`)).toBeVisible()

    // All the validation stamps as columns
    await expect(table.getByTestId(`${latest.id}-${unit.id}`)).toBeVisible()
    await expect(table.getByTestId(`${middle.id}-${integration.id}`)).toBeVisible()
})

test('the last builds widget restricts the promotions and the validations to the configured ones', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const silver = await branch.createPromotionLevel()
    const gold = await branch.createPromotionLevel()
    const unit = await branch.createValidationStamp()
    const integration = await branch.createValidationStamp()

    const build = await branch.createBuild()
    const silverRun = await build.promote(silver)
    const goldRun = await build.promote(gold)
    await build.validate(unit)
    await build.validate(integration)

    await login(page, ontrack)
    await importAndSelectDashboard(page, [
        `project: "${project.name}"`,
        `branch: "${branch.name}"`,
        `promotions:`,
        `  - "${gold.name}"`,
        `validations:`,
        `  - "${unit.name}"`,
    ])

    const table = page.getByTestId('last-builds-for-branch')
    await expect(table.getByRole('link', {name: build.name, exact: true})).toBeVisible()

    // Only the gold promotion
    await expect(table.getByTestId(`promotion-run-link-${goldRun.id}`)).toBeVisible()
    await expect(table.getByTestId(`promotion-run-link-${silverRun.id}`)).toHaveCount(0)

    // Only the unit validation
    await expect(table.getByTestId(`${build.id}-${unit.id}`)).toBeVisible()
    await expect(table.getByTestId(`${build.id}-${integration.id}`)).toHaveCount(0)
})
