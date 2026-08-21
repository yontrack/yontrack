const {expect} = require('@playwright/test');
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {ValidationStampPage} = require("./validationStamp");
const {test} = require("../../fixtures/connection");
const {ChartOptionsDialog} = require("../charts/ChartOptionsDialog");

test('display last status description in validation stamp page', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    const description = generate("Description ");
    await build.validate(validationStamp, {description: description})
    // Login
    await login(page, ontrack)
    // Navigating to the validation stamp
    const vsPage = new ValidationStampPage(page, validationStamp)
    await vsPage.goTo()
    // Expecting the last run description to appear
    await expect(page.getByText(description)).toBeVisible()
})
test('changing the chart interval and period reloads the charts', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    // Login
    await login(page, ontrack)
    // Spying on the chart requests
    const chartRequests = []
    await page.route('**/api/protected/graphql', async (route) => {
        const body = route.request().postDataJSON()
        if (body?.query?.includes('getChart')) {
            chartRequests.push({query: body.query, variables: body.variables})
        }
        await route.continue()
    })
    // Navigating to the validation stamp
    const vsPage = new ValidationStampPage(page, validationStamp)
    await vsPage.goTo()
    // The charts are displayed using the default options
    const leadTimeChart = page.getByTestId("chart-lead-time")
    await expect(leadTimeChart).toContainText(/3m\s*\/\s*1w/)
    // Setting other options
    await leadTimeChart.getByRole('button', {name: /3m/}).click()
    await new ChartOptionsDialog(page).setOptions({interval: '1m', period: '1d'})
    // The options are displayed by all the charts
    await expect(leadTimeChart).toContainText(/1m\s*\/\s*1d/)
    await expect(page.getByTestId("chart-frequency")).toContainText(/1m\s*\/\s*1d/)
    await expect(page.getByTestId("chart-stability")).toContainText(/1m\s*\/\s*1d/)
    // ... and all the charts have actually been reloaded using these options
    const chartQueries = [
        'ValidationStampLeadTimeChart',
        'ValidationStampFrequencyChart',
        'ValidationStampStabilityChart',
    ]
    for (const chartQuery of chartQueries) {
        await expect.poll(
            () => chartRequests.filter(it =>
                it.query.includes(chartQuery) &&
                it.variables.interval === '1m' &&
                it.variables.period === '1d'
            ).length,
            {message: `No reload of ${chartQuery} using the new options`}
        ).toBeGreaterThan(0)
    }
})
