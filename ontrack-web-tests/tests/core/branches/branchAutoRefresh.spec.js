const {expect} = require("@playwright/test");
const {login} = require("../login");
const {BranchPage} = require("./branch");
const {BranchPipelinePage} = require("./branchPipeline");
const {AutoRefreshControl} = require("../../support/autoRefresh");
const {test} = require("../../fixtures/connection");

/**
 * Auto refresh in the Builds and Pipeline branch views (#1195).
 *
 * What only a browser can answer: that a build created behind the page's back shows up in it without
 * a reload, and that the refresh, owned by the branch rather than by one view, stays on across a view
 * switch. How a refresh rebuilds the list of builds - no duplicates after "load more", new ones on
 * top - is pinned by the Jest tests of `useBranchBuildsPage`.
 */

const branchWithABuild = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    return {branch, build}
}

// Long enough for at least two ticks at the shortest interval, and the fetch after them
const refreshTimeout = 15_000

test('the builds view shows a new build without a page reload', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo({view: 'builds'})
    await branchPage.checkBuildPresent(build.name)

    const autoRefresh = new AutoRefreshControl(page)
    await autoRefresh.checkDisabled()
    await autoRefresh.enable()

    const created = await branch.createBuild()
    await expect(page.getByRole('link', {name: created.name, exact: true}))
        .toBeVisible({timeout: refreshTimeout})
    await branchPage.checkBuildPresent(build.name)
})

test('the pipeline view shows a new build without a page reload', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()
    await pipelinePage.checkBuildPresent(build)

    const autoRefresh = new AutoRefreshControl(page)
    await autoRefresh.checkDisabled()
    await autoRefresh.enable()

    const created = await branch.createBuild()
    await expect(pipelinePage.timelineCard(created)).toBeVisible({timeout: refreshTimeout})
    await pipelinePage.checkBuildPresent(build)
})

test('auto refresh stays on when switching from the builds view to the pipeline view', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo({view: 'builds'})

    const autoRefresh = new AutoRefreshControl(page)
    await autoRefresh.enable()

    // Picking a view from the menu saves it in the user's preferences, on the server: every spec
    // after this one which opens a branch without `?view=` would land on the pipeline view. The
    // builds view is put back whatever happens here.
    try {
        await branchPage.selectContentView("Pipeline")
        const pipelinePage = new BranchPipelinePage(page, branch)
        await pipelinePage.checkOnPage()
        await pipelinePage.checkBuildPresent(build)
        await autoRefresh.checkEnabled()

        // And still not remembered across a page reload
        await pipelinePage.goTo()
        await autoRefresh.checkDisabled()
    } finally {
        await branchPage.selectContentView("Builds")
    }
})
