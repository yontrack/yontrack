const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {graphQLCall, graphQLCallMutation} = require("@ontrack/graphql");
const {generate} = require("@ontrack/utils");

const applyDashboardsMutation = `
    mutation ApplyDashboards($yaml: String!) {
        applyDashboards(input: { yaml: $yaml }) {
            dashboards { uuid name }
            errors { message }
        }
    }
`

const widgetYaml = (dashboardName, config) => [
    `- name: "${dashboardName}"`,
    `  widgets:`,
    `    - key: "home/HighestPromotionsPerBranch"`,
    `      layout: {x: 0, y: 0, w: 12, h: 30}`,
    `      config:`,
    ...config.map(line => `        ${line}`),
].join('\n')

/**
 * Creates the dashboard through the API and displays it by its URL: selecting it from the menu would
 * make it the dashboard of the user for the next tests as well.
 */
const displayDashboard = async (page, ontrack, config) => {
    const dashboardName = generate('highest-promotions-')
    const data = await graphQLCallMutation(
        ontrack.connection,
        'applyDashboards',
        applyDashboardsMutation,
        {yaml: widgetYaml(dashboardName, config)}
    )
    const dashboardUuid = data.applyDashboards.dashboards[0].uuid
    await login(page, ontrack)
    await page.goto(`${ontrack.connection.ui}/?dashboard=${dashboardUuid}`)
    const widget = page.getByTestId('highest-promotions-per-branch')
    await expect(widget).toBeVisible()
    return {dashboardUuid, widget}
}

const row = (widget, key) => widget.getByTestId(`highest-promotions-row-${key}`)

/** The keys of the rows, in the order they are shown. */
const expectRowOrder = async (widget, keys) => {
    const rows = widget.locator('[data-testid^="highest-promotions-row-"]')
    await expect(rows).toHaveCount(keys.length)
    for (const [index, key] of keys.entries()) {
        await expect(rows.nth(index)).toHaveAttribute('data-testid', `highest-promotions-row-${key}`)
    }
}

/**
 * A branch whose latest build has BRONZE and GOLD but not SILVER, and a branch whose latest build
 * has no promotion at all, while an older one has SILVER. The latest build of the second branch is
 * the most recent one.
 */
const provision = async (ontrack) => {
    const project = await ontrack.createProject()

    const promoted = await project.createBranch()
    const bronze = await promoted.createPromotionLevel()
    const silver = await promoted.createPromotionLevel()
    const gold = await promoted.createPromotionLevel(undefined, {description: 'Ready for production'})
    await (await promoted.createBuild()).promote(silver)
    const latest = await promoted.createBuild()
    await latest.promote(bronze)
    const goldRun = await latest.promote(gold)

    const notPromoted = await project.createBranch()
    const otherSilver = await notPromoted.createPromotionLevel()
    await (await notPromoted.createBuild()).promote(otherSilver)
    const notPromotedBuild = await notPromoted.createBuild()

    return {project, promoted, bronze, silver, gold, latest, goldRun, notPromoted, notPromotedBuild}
}

test('the table format shows the latest build of each branch and its highest promotion', async ({page, ontrack}) => {
    const {project, promoted, latest, gold, goldRun, notPromoted, notPromotedBuild} = await provision(ontrack)

    const {widget} = await displayDashboard(page, ontrack, [
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
        `  - project: "${project.name}"`,
        `    branch: "${notPromoted.name}"`,
    ])
    await expect(widget).toHaveAttribute('data-format', 'TABLE')

    const promotedRow = row(widget, `branch-${promoted.id}`)
    await expect(promotedRow.getByRole('link', {name: promoted.name, exact: true})).toBeVisible()
    await expect(promotedRow.getByRole('link', {name: latest.name, exact: true})).toBeVisible()
    // GOLD, the highest level of the branch, and not BRONZE, granted to the same build
    await expect(promotedRow.getByTestId(`promotion-run-link-${goldRun.id}`)).toBeVisible()
    await expect(promotedRow).toContainText(gold.name)

    // The latest build, even if an older one is promoted
    const notPromotedRow = row(widget, `branch-${notPromoted.id}`)
    await expect(notPromotedRow.getByRole('link', {name: notPromotedBuild.name, exact: true})).toBeVisible()
    await expect(notPromotedRow).toContainText('Not promoted yet')

    // By last activity by default, not in the order of the configuration
    await expectRowOrder(widget, [`branch-${notPromoted.id}`, `branch-${promoted.id}`])
})

test('the ladder format shows all the levels of the branch, granted or not, and the highest one', async ({page, ontrack}) => {
    const {project, promoted, bronze, silver, gold, notPromoted} = await provision(ontrack)

    const {widget} = await displayDashboard(page, ontrack, [
        `format: LADDER`,
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
        `  - project: "${project.name}"`,
        `    branch: "${notPromoted.name}"`,
    ])
    await expect(widget).toHaveAttribute('data-format', 'LADDER')

    const step = level => row(widget, `branch-${promoted.id}`).getByTestId(`highest-promotions-step-${level.id}`)
    await expect(step(bronze)).toHaveAttribute('data-granted', 'true')
    await expect(step(bronze)).toHaveAttribute('data-highest', 'false')
    // Promotions are not cumulative: SILVER is not granted to the latest build
    await expect(step(silver)).toHaveAttribute('data-granted', 'false')
    await expect(step(gold)).toHaveAttribute('data-granted', 'true')
    await expect(step(gold)).toHaveAttribute('data-highest', 'true')
    // The name of the highest level shows under its step only, without a line of its own
    await expect(row(widget, `branch-${promoted.id}`).getByText(gold.name, {exact: true})).toHaveCount(1)

    await expect(row(widget, `branch-${notPromoted.id}`)).toContainText('Not promoted yet')

    // No legend: the popovers of the steps say what they mean
    await expect(widget).not.toContainText('highest promotion of the latest build')
    await expect(widget).not.toContainText('not granted to that build')
})

test('the popover of a ladder step says when the level was granted, or that it was not', async ({page, ontrack}) => {
    const {project, promoted, silver, gold} = await provision(ontrack)

    const {widget} = await displayDashboard(page, ontrack, [
        `format: LADDER`,
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
    ])

    const step = level => row(widget, `branch-${promoted.id}`).getByTestId(`highest-promotions-step-${level.id}`)
    const popover = level => page.getByTestId(`highest-promotions-step-popover-${level.id}`)

    await step(gold).hover()
    await expect(popover(gold)).toBeVisible()
    await expect(popover(gold)).toContainText(gold.name)
    await expect(popover(gold)).toContainText('promoted ')
    await expect(popover(gold)).toContainText('Ready for production')

    await step(silver).hover()
    await expect(popover(silver)).toBeVisible()
    await expect(popover(silver)).toContainText(silver.name)
    await expect(popover(silver)).toContainText('not granted to this build')
})

test('the popover of a ladder step not granted opens from the keyboard', async ({page, ontrack}) => {
    const {project, promoted, silver} = await provision(ontrack)

    const {widget} = await displayDashboard(page, ontrack, [
        `format: LADDER`,
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
    ])

    await row(widget, `branch-${promoted.id}`).getByTestId(`highest-promotions-step-${silver.id}`).focus()
    await expect(page.getByTestId(`highest-promotions-step-popover-${silver.id}`)).toContainText('not granted to this build')
})

test('the project of the branches can be hidden, but not for a branch which is not found', async ({page, ontrack}) => {
    const {project, promoted} = await provision(ontrack)

    const {widget} = await displayDashboard(page, ontrack, [
        `showProject: false`,
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
        `  - project: "${project.name}"`,
        `    branch: "unknown-branch"`,
    ])

    const promotedRow = row(widget, `branch-${promoted.id}`)
    await expect(promotedRow.getByRole('link', {name: promoted.name, exact: true})).toBeVisible()
    await expect(promotedRow.getByRole('link', {name: project.name, exact: true})).toHaveCount(0)

    await expect(row(widget, 'entry-1')).toContainText(`${project.name} / unknown-branch`)
})

test('the branches can be sorted by name, the numbers compared as numbers', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const release9 = await project.createBranch('release-9')
    const main = await project.createBranch('Main')
    const release10 = await project.createBranch('release-10')
    // Built in this order: by last activity, release-10, Main, release-9
    for (const branch of [release9, main, release10]) {
        await branch.createBuild()
    }

    const {widget} = await displayDashboard(page, ontrack, [
        `sort: NAME`,
        `branches:`,
        ...[release10, release9, main].flatMap(branch => [
            `  - project: "${project.name}"`,
            `    branch: "${branch.name}"`,
        ]),
    ])

    await expectRowOrder(widget, [main, release9, release10].map(branch => `branch-${branch.id}`))
})

test('the format switched in the widget is kept after a reload, without changing the saved configuration', async ({page, ontrack}) => {
    const {project, promoted} = await provision(ontrack)

    const {dashboardUuid, widget} = await displayDashboard(page, ontrack, [
        `format: TABLE`,
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "${promoted.name}"`,
    ])
    await expect(widget).toHaveAttribute('data-format', 'TABLE')

    await page.getByTestId('highest-promotions-format').getByText('Ladder', {exact: true}).click()
    await expect(widget).toHaveAttribute('data-format', 'LADDER')

    await page.reload()
    await expect(page.getByTestId('highest-promotions-per-branch')).toHaveAttribute('data-format', 'LADDER')

    // The switch belongs to this browser only: the dashboard still says TABLE
    const data = await graphQLCall(ontrack.connection, `
        query UserDashboards {
            userDashboards {
                uuid
                widgets { key config }
            }
        }
    `)
    const dashboard = data.userDashboards.find(it => it.uuid === dashboardUuid)
    const config = dashboard.widgets.find(it => it.key === 'home/HighestPromotionsPerBranch').config
    expect(config.format).toBe('TABLE')
})

test('a pattern entry shows the most recently active branches it matches, up to its count', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const release1 = await project.createBranch('release-1')
    const release2 = await project.createBranch('release-2')
    const release3 = await project.createBranch('release-3')
    const main = await project.createBranch('main')
    // Built in this order, so release-3 is the most recently active release branch
    for (const branch of [release1, release2, release3, main]) {
        await branch.createBuild()
    }

    const {widget} = await displayDashboard(page, ontrack, [
        `branches:`,
        `  - project: "${project.name}"`,
        `    pattern: "release-.*"`,
        `    count: 2`,
    ])

    await expect(row(widget, `branch-${release3.id}`)).toBeVisible()
    await expect(row(widget, `branch-${release2.id}`)).toBeVisible()
    await expect(row(widget, `branch-${release1.id}`)).toHaveCount(0)
    await expect(row(widget, `branch-${main.id}`)).toHaveCount(0)
    // The most recently active first
    await expectRowOrder(widget, [`branch-${release3.id}`, `branch-${release2.id}`])
})

test('the rows with nothing to show say why', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const empty = await project.createBranch()

    const {widget} = await displayDashboard(page, ontrack, [
        `branches:`,
        `  - project: "${project.name}"`,
        `    branch: "unknown-branch"`,
        `  - project: "${project.name}"`,
        `    branch: "${empty.name}"`,
        `  - project: "${project.name}"`,
        `    pattern: "feature-.*"`,
    ])

    await expect(row(widget, 'entry-0')).toContainText('Branch not found')
    await expect(row(widget, `branch-${empty.id}`)).toContainText('No build yet')
    await expect(row(widget, 'entry-2')).toContainText(`No branch of ${project.name} matches feature-.*`)
})
