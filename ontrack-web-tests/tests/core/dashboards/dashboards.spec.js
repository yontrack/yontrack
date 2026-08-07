const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {graphQLCallMutation} = require("@ontrack/graphql");

const applyDashboardsMutation = `
    mutation ApplyDashboards($yaml: String!) {
        applyDashboards(input: { yaml: $yaml }) {
            dashboards { uuid name }
            errors { message }
        }
    }
`

test('export a dashboard as YAML', async ({page, ontrack}) => {
    const dashboardName = `test-dash-${Date.now()}`
    const yaml = `- name: "${dashboardName}"\n  widgets:\n  - key: "home/LastActiveProjects"\n    layout: {x: 0, y: 0, w: 6, h: 25}\n    config: {count: 5}`

    // Create a shared dashboard via the API
    await graphQLCallMutation(
        ontrack.connection,
        'applyDashboards',
        applyDashboardsMutation,
        {yaml}
    )

    await login(page, ontrack)

    // Open the Dashboard dropdown menu
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // Select the test dashboard
    await page.getByText(dashboardName).click()

    // Open the dropdown menu again to access actions
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // Click "Export as YAML"
    await page.getByText('Export as YAML').click()

    // Modal should appear
    const modal = page.getByRole('dialog')
    await expect(modal.getByText('Export dashboard as YAML')).toBeVisible()

    // The YAML editor should contain the dashboard name and widget key
    const aceContent = modal.locator('.ace_content')
    await expect(aceContent).toContainText(dashboardName)
    await expect(aceContent).toContainText('LastActiveProjects')

    // Close the modal via the footer button (not the × icon)
    await modal.locator('button').filter({hasText: 'Close'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
})

test('import dashboards as YAML via the UI', async ({page, ontrack}) => {
    const dashboardName = `import-dash-${Date.now()}`
    const yaml = `- name: "${dashboardName}"\n  widgets: []`

    await login(page, ontrack)

    // Open the Dashboard dropdown menu
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // Click "Import dashboards as YAML"
    await page.getByText('Import dashboards as YAML').click()

    // The import dialog should appear
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()

    // Fill in the YAML
    await modal.locator('textarea').fill(yaml)

    // Click Import
    await modal.getByRole('button', {name: 'Import'}).click()

    // Dialog should close after successful import
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // The new dashboard should appear in the menu
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await expect(page.getByText(dashboardName)).toBeVisible()
})

test('import dialog shows validation error for empty YAML', async ({page, ontrack}) => {
    await login(page, ontrack)

    // Open the Dashboard dropdown menu
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // Click "Import dashboards as YAML"
    await page.getByText('Import dashboards as YAML').click()

    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()

    // Submit without filling in the YAML field
    await modal.getByRole('button', {name: 'Import'}).click()

    // Dialog should remain open with a validation error
    await expect(modal).toBeVisible()
    await expect(modal.getByText('YAML content is required.')).toBeVisible()
})

test('export as YAML is not available for built-in dashboards', async ({page, ontrack}) => {
    await login(page, ontrack)

    // The default dashboard is BUILT_IN — open its menu
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // Select the default (built-in) dashboard
    await page.getByText('Default dashboard').click()

    // Open dropdown again
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()

    // "Export as YAML" should NOT appear for built-in dashboards
    await expect(page.getByText('Export as YAML')).not.toBeVisible()
})
