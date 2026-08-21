const {login} = require("../login");
const {BranchPage} = require("./branch");
const {test} = require("../../fixtures/connection");
const {expect} = require("@playwright/test");

test('filtering builds on their display name', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    // A build whose display name comes from its release label
    const labelled = await branch.createBuild("build-1")
    await labelled.setRelease("1.2.0")
    // A build with no label: its display name is its own name
    await branch.createBuild("2.0.0-plain")
    // A build which must not be selected
    const excluded = await branch.createBuild("build-3")
    await excluded.setRelease("9.9.9")

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const dialog = await branchPage.newStandardBuildFilter()
    // The build criteria are the first ones being displayed
    await dialog.checkBuildTabSelected()
    // Matches the label of the first build and the name of the second one
    await dialog.setDisplayName("^(1\\.2|2\\.0)")
    await dialog.ok()

    await branchPage.checkBuildPresent("1.2.0")
    await branchPage.checkBuildPresent("2.0.0-plain")
    await branchPage.checkBuildNotPresent("9.9.9")
})

test('an invalid display name regex is rejected by the filter form', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    await branch.createBuild("build-1")

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const dialog = await branchPage.newStandardBuildFilter()
    await dialog.setDisplayName("[unclosed")
    await dialog.ok()

    await dialog.checkDisplayNameError("Not a valid regular expression")
    // The dialog stays open, the filter is not applied
    await expect(dialog.dialog).toBeVisible()
})
