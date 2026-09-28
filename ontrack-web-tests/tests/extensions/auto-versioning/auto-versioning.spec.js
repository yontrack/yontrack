const {test} = require("../../fixtures/connection");
const {setupSimpleAutoVersioning} = require("./auto-versioning");
const {expect} = require("@playwright/test");
const {login} = require("../../core/login");
const {AutoVersioningAuditPage} = require("./AutoVersioningAuditPage");
const {AutoVersioningAuditDetailsPage} = require("./AutoVersioningAuditDetailsPage");
const {BranchPage} = require("../../core/branches/branch");

test('rescheduling an auto-versioning order', async ({page, ontrack}) => {
    // Auto-versioning context
    const autoVersioning = await setupSimpleAutoVersioning({page, ontrack})
    // Launching a promotion, triggering an auto-versioning request
    const depBuild = await autoVersioning.createDepBuild()
    await autoVersioning.promoteDepBuild(depBuild)
    const entry = await autoVersioning.waitForAutoVersioningCompletion({depBuild})
    await expect(entry.mostRecentState.state).toBe('PR_MERGED')

    // Checking the audit page
    await login(page, ontrack)
    const avAuditPage = new AutoVersioningAuditPage(page, ontrack)
    await avAuditPage.goTo()

    // Check that the initial order is present in the page and in the correct state
    const entryRow = await avAuditPage.getEntryRow(entry.order.uuid)
    await entryRow.checkState('PR merged')

    // Going to the details page
    const avDetailsPage = await entryRow.showDetails()

    // On the details page, rescheduling the order
    await avDetailsPage.reschedule()

    // Getting the new entry, which must be different
    const rescheduledEntry = await autoVersioning.waitForAutoVersioningCompletion({
        depBuild,
        state: 'PROCESSING_ABORTED'
    })
    await expect(rescheduledEntry.order.uuid).not.toBe(entry.order.uuid)

    // It must be aborted (same version)
    await expect(rescheduledEntry.mostRecentState.state).toBe('PROCESSING_ABORTED')

    // We must be on the new entry page
    const rescheduledDetailsPage = new AutoVersioningAuditDetailsPage(page, ontrack, rescheduledEntry.order.uuid)
    await rescheduledDetailsPage.expectOnPage()

    // We expect the entry to be aborted (after some time)
    await rescheduledDetailsPage.waitState('Aborted')

    // The new entry links back to the one it reschedules...
    await rescheduledDetailsPage.expectRescheduledFrom(entry.order.uuid)

    // ... and the rescheduled entry links forward to it
    const originalDetailsPage = new AutoVersioningAuditDetailsPage(page, ontrack, entry.order.uuid)
    await originalDetailsPage.goTo()
    await originalDetailsPage.expectRescheduledAs(rescheduledEntry.order.uuid)
})

test('seeing the status of the PR linked to the auto-versioning order', async ({page, ontrack}) => {
    // Auto-versioning context
    const autoVersioning = await setupSimpleAutoVersioning({page, ontrack})
    // Launching a promotion, triggering an auto-versioning request
    const depBuild = await autoVersioning.createDepBuild()
    await autoVersioning.promoteDepBuild(depBuild)
    const entry = await autoVersioning.waitForAutoVersioningCompletion({depBuild})
    expect(entry.mostRecentState.state).toBe('PR_MERGED')

    // Going to the details page
    await login(page, ontrack)
    const avDetailsPage = new AutoVersioningAuditDetailsPage(page, ontrack, entry.order.uuid)
    await avDetailsPage.goTo()

    // Checks that we have a link to the PR
    await avDetailsPage.expectPRLink(entry.mostRecentState.data.prName)

    // Checks that we have the PR status
    await avDetailsPage.expectPRStatus(entry.mostRecentState.data.prName, "Merged")
})

test('loading the statuses of the PRs in the audit page', async ({page, ontrack}) => {
    // Auto-versioning context
    const autoVersioning = await setupSimpleAutoVersioning({page, ontrack})
    // Launching a promotion, triggering an auto-versioning request
    const depBuild = await autoVersioning.createDepBuild()
    await autoVersioning.promoteDepBuild(depBuild)
    const entry = await autoVersioning.waitForAutoVersioningCompletion({depBuild})
    expect(entry.mostRecentState.state).toBe('PR_MERGED')

    // Going to the audit page
    await login(page, ontrack)
    const avAuditPage = new AutoVersioningAuditPage(page, ontrack)
    await avAuditPage.goTo()

    // The PR status is not visible yet
    let entryRow = await avAuditPage.getEntryRow(entry.order.uuid)
    await entryRow.checkState('PR merged')
    await entryRow.expectPRStatusAbsent(entry.mostRecentState.data.prName)

    // Loading the PR statuses
    await avAuditPage.loadPRStatuses()

    // Checking the PR statuses are visible in the audit page
    entryRow = await avAuditPage.getEntryRow(entry.order.uuid)
    await entryRow.expectPRStatus(entry.mostRecentState.data.prName, 'Merged')
})

test('scheduled auto-versioning order shows pending schedule state', async ({page, ontrack}) => {
    // Create an AV config with a cron schedule set far in the future
    const autoVersioning = await setupSimpleAutoVersioning({page, ontrack, cronSchedule: '0 0 23 * * *'})

    // Trigger a promotion to create an AV order
    const depBuild = await autoVersioning.createDepBuild()
    await autoVersioning.promoteDepBuild(depBuild)

    // Wait for the order to settle into PENDING_SCHEDULE state (not immediately processed)
    const entry = await autoVersioning.waitForAutoVersioningCompletion({depBuild, state: 'PENDING_SCHEDULE'})

    // Navigate to the global audit page and check the state is shown correctly
    await login(page, ontrack)
    const avAuditPage = new AutoVersioningAuditPage(page, ontrack)
    await avAuditPage.goTo()

    const entryRow = await avAuditPage.getEntryRow(entry.order.uuid)
    await entryRow.checkState('Pending schedule')
})

test('auto-versioning cron schedule being displayed', async ({page, ontrack}) => {
    // Auto-versioning context
    const {depProject, targetBranch} = await setupSimpleAutoVersioning({page, ontrack, cronSchedule: '0 0 23 * * *'})

    // Going to the target branch page
    await login(page, ontrack)
    const branchPage = new BranchPage(page, targetBranch)
    await branchPage.goTo()

    // Going to the AV config page
    const avConfigPage = await branchPage.navigateToAutoVersioningConfig()

    // Once on the AV config page, display the config for the dependency
    const avConfigDetails = await avConfigPage.displayConfig(depProject.name)

    // Checks the schedule being displayed
    await avConfigDetails.expectCronSchedule('0 0 23 * * *')
})
