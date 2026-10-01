// @ts-check
const {expect} = require('@playwright/test');
const {login} = require("../login");
const {BranchPage} = require("../branches/branch");
const {BuildPage} = require("../builds/BuildPage");
const {generate} = require("@ontrack/utils");
const {test} = require("../../fixtures/connection");

const provisionWarningRun = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    const run = await build.validate(validationStamp, {status: "WARNING"})
    return {branch, build, run}
}

test('WARNING to FIXED in one click from the branch builds grid', async ({page, ontrack}) => {
    const {branch, run} = await provisionWarningRun(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const popover = await branchPage.validationRunQuickTransition(run)
    await popover.applyStatus('Fixed')

    // The grid has been refreshed
    await expect(branchPage.validationRunCell(run).getByRole('img', {name: 'Fixed'})).toBeVisible()
    // Checking through the API
    const updated = await ontrack.getValidationRunById(run.id)
    expect(updated.lastStatus.statusID.id).toBe('FIXED')
})

test('WARNING to FIXED in one click from the build page validations table', async ({page, ontrack}) => {
    const {build, run} = await provisionWarningRun(ontrack)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const popover = await buildPage.validationRunQuickTransition(run)
    await popover.applyStatus('Fixed')

    // The table has been refreshed
    await expect(buildPage.validationRunStatus(run)).toContainText('Fixed')
    // Checking through the API
    const updated = await ontrack.getValidationRunById(run.id)
    expect(updated.lastStatus.statusID.id).toBe('FIXED')
})

test('changing a status with a comment', async ({page, ontrack}) => {
    const {branch, run} = await provisionWarningRun(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const comment = `Known issue ${generate('id_')}`
    const popover = await branchPage.validationRunQuickTransition(run)
    await popover.applyStatusWithComment('Explained', comment)

    // The comment shows in the run's status list
    await expect(branchPage.validationRunCell(run).getByRole('img', {name: 'Explained'})).toBeVisible()
    const history = await branchPage.validationRunHistory(run)
    await history.checkStatus('Explained', comment)
    // Checking through the API
    const updated = await ontrack.getValidationRunById(run.id)
    expect(updated.lastStatus.statusID.id).toBe('EXPLAINED')
})
