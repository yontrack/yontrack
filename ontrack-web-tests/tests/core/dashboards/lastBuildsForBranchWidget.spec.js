const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");

const importAndSelectDashboard = async (page, name, yaml) => {
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Import dashboards as YAML').click()
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()
    await modal.locator('textarea').fill(yaml)
    await modal.getByRole('button', {name: 'Import'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(name).click()
}

const widgetYaml = (dashboardName, config) => [
    `- name: "${dashboardName}"`,
    `  widgets:`,
    `    - key: "home/LastBuildsForBranch"`,
    `      layout: {x: 0, y: 0, w: 12, h: 30}`,
    `      config:`,
    ...config.map(line => `        ${line}`),
].join('\n')

test('the last builds widget shows the last builds of a branch, with all their promotions and validations', async ({page, ontrack}) => {
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

    const dashboardName = generate('last-builds-')
    await login(page, ontrack)
    await importAndSelectDashboard(page, dashboardName, widgetYaml(dashboardName, [
        `project: "${project.name}"`,
        `branch: "${branch.name}"`,
        `count: 2`,
    ]))

    const table = page.getByTestId('last-builds-for-branch')
    await expect(table).toBeVisible()

    // The two last builds only
    await expect(table.getByRole('link', {name: latest.name, exact: true})).toBeVisible()
    await expect(table.getByRole('link', {name: middle.name, exact: true})).toBeVisible()
    await expect(table.getByRole('link', {name: oldest.name, exact: true})).toHaveCount(0)

    // All the promotions
    await expect(table.getByTestId(`promotion-run-link-${silverRun.id}`)).toBeVisible()
    await expect(table.getByTestId(`promotion-run-link-${goldRun.id}`)).toBeVisible()

    // One column per validation stamp, all of them
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

    const dashboardName = generate('last-builds-')
    await login(page, ontrack)
    await importAndSelectDashboard(page, dashboardName, widgetYaml(dashboardName, [
        `project: "${project.name}"`,
        `branch: "${branch.name}"`,
        `promotions:`,
        `  - "${gold.name}"`,
        `validations:`,
        `  - "${unit.name}"`,
    ]))

    const table = page.getByTestId('last-builds-for-branch')
    await expect(table.getByRole('link', {name: build.name, exact: true})).toBeVisible()

    await expect(table.getByTestId(`promotion-run-link-${goldRun.id}`)).toBeVisible()
    await expect(table.getByTestId(`promotion-run-link-${silverRun.id}`)).toHaveCount(0)

    await expect(table.getByTestId(`${build.id}-${unit.id}`)).toBeVisible()
    await expect(table.getByTestId(`${build.id}-${integration.id}`)).toHaveCount(0)
})
