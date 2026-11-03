const {expect} = require("@playwright/test");
const {login} = require("../login");
const {BranchPage} = require("./branch");
const {test} = require("../../fixtures/connection");

/**
 * What `BranchPage.checkOnPage` promises about the builds of the page.
 *
 * The response of the builds query is held back, so that the page is reliably still waiting for its
 * builds at the moment the check would be satisfied too early.
 */

const holdBuilds = async (page, ms) => {
    await page.route('**/api/protected/graphql', async (route) => {
        if (route.request().postDataJSON()?.query?.includes('query LoadBuilds(')) {
            await new Promise(resolve => setTimeout(resolve, ms))
        }
        return route.continue()
    })
}

test('the page is settled once the builds have arrived', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    await branch.createBuild()

    await login(page, ontrack)
    await holdBuilds(page, 2000)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo({withBuilds: true})

    // Counted on purpose and not auto-waited on: anything waiting here would hide a check returning early
    expect(await page.locator('.ant-table-row[data-row-key]').count()).toBe(1)
})
