const {expect} = require("@playwright/test");
const {login} = require("../login");
const {BranchPage} = require("./branch");
const {test} = require("../../fixtures/connection");

/**
 * The content region of the branch view is pluggable: the builds table is one content view among
 * others, picked from the "View" menu of the command bar.
 *
 * Only the builds view is registered for now, so what can be checked end to end is the mechanism
 * around the choice — the menu, the `?view=` parameter and its fallback. Switching between two views
 * is covered by the Jest tests of the registry and of the selection, until a second view lands.
 */

const branchWithABuild = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    return {branch, build}
}

test('the branch view offers its content views in the command bar', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await branchPage.checkContentViews(["Builds"])
    await branchPage.checkContentViewSelected("Builds")
    await branchPage.checkBuildPresent(build.name)
})

test('selecting a content view makes the choice linkable', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await branchPage.selectContentView("Builds")

    // Unlike the `buildFilter` parameter, the selection is not consumed and cleared: it stays in the URL
    await expect(page).toHaveURL(/[?&]view=builds/)
    await branchPage.checkBuildPresent(build.name)
})

test('the ?view= parameter selects the content view', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo({view: 'builds'})

    await branchPage.checkContentViewSelected("Builds")
    await branchPage.checkBuildPresent(build.name)
})

test('an unknown ?view= parameter falls back to the builds view', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo({view: 'no-such-view'})

    await branchPage.checkContentViewSelected("Builds")
    await branchPage.checkBuildPresent(build.name)
})

test('the selected content view is remembered across a reload', async ({page, ontrack}) => {
    const {branch, build} = await branchWithABuild(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await branchPage.selectContentView("Builds")

    // Back on the page without any parameter: the stored preference applies
    await branchPage.goTo()
    await branchPage.checkContentViewSelected("Builds")
    await branchPage.checkBuildPresent(build.name)
})
