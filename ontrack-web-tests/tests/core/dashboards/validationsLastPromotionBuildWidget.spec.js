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

test('editing the widget keeps its promotion level', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const promotionLevel = await branch.createPromotionLevel()

    const title = generate('validations-')
    const yaml = [
        `- name: "${generate('last-promoted-')}"`,
        `  widgets:`,
        `    - key: "home/ValidationsLastPromotionBuild"`,
        `      layout: {x: 0, y: 0, w: 12, h: 25}`,
        `      config:`,
        `        title: "${title}"`,
        `        project: "${project.name}"`,
        `        branch: "${branch.name}"`,
        `        promotion: "${promotionLevel.name}"`,
    ].join('\n')
    const data = await graphQLCallMutation(
        ontrack.connection,
        'applyDashboards',
        applyDashboardsMutation,
        {yaml}
    )
    const dashboardUuid = data.applyDashboards.dashboards[0].uuid

    await login(page, ontrack)
    await page.goto(`${ontrack.connection.ui}/?dashboard=${dashboardUuid}`)
    await expect(page.getByText(title)).toBeVisible()

    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Edit current dashboard').click()
    await expect(page.getByText('Dashboard in edition mode')).toBeVisible()

    // The first command of the header is "Configure this widget"
    const header = page.locator('.ant-card-head').filter({hasText: title})
    await header.getByRole('button').first().click()

    // The current promotion level is displayed
    const form = page.locator('.ant-form-item').filter({hasText: 'Promotion level'})
    await expect(form.getByText(promotionLevel.name)).toBeVisible()

    // Saving without any change: the first command of the header is now "Saves the changes for this widget"
    await header.getByRole('button').first().click()
    await expect(form).toHaveCount(0)
    await page.getByRole('button', {name: 'Save dashboard'}).click()
    await expect(page.getByText('Dashboard in edition mode')).not.toBeVisible()

    const saved = await graphQLCall(ontrack.connection, `
        query UserDashboards {
            userDashboards {
                uuid
                widgets { key config }
            }
        }
    `)
    const config = saved.userDashboards
        .find(it => it.uuid === dashboardUuid)
        .widgets.find(it => it.key === 'home/ValidationsLastPromotionBuild')
        .config
    expect(config.project).toBe(project.name)
    expect(config.branch).toBe(branch.name)
    expect(config.promotion).toBe(promotionLevel.name)
})
