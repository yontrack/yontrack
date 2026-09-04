const {expect} = require("@playwright/test");
const {login} = require("../login");
const {BranchPage} = require("./branch");
const {BranchPipelinePage} = require("./branchPipeline");
const {provisionChangeLog} = require("../../extensions/scm/scm");
const {test} = require("../../fixtures/connection");

/**
 * The pipeline branch content view: a branch read as a promotion pipeline.
 *
 * What is checked here is what only a browser can answer - that selecting a build actually changes
 * the inspector, that a `?build=` link lands on the build it names, that range selection still
 * reaches the change log from this view, and that a branch with no promotion levels does not get an
 * empty band. The selection rules themselves, and the rest of the degradation matrix, are pinned by
 * the Jest tests, which can cover them far faster.
 */

/**
 * Local storage key behind the experimental alert's dismissal - `CloseableAlert` prefixes the
 * alert's own id with `closeable-alert-`.
 *
 * The specs which care about the alert clear it on the way in AND on the way out: the one which
 * dismisses it would otherwise leave every spec running after it looking at a page whose alert is
 * already gone. Playwright gives each test a fresh context today, so nothing leaks in practice -
 * but that is a property of the config, not of this file, and the file should not need it to hold.
 *
 * Cleared with `evaluate` rather than from an init script: an init script runs again on every
 * navigation, so it would also wipe the dismissal the "stays dismissed" test observes surviving a
 * reload.
 */
const experimentalAlertKey = 'closeable-alert-feature-branch-pipeline-view'

const forgetExperimentalAlertDismissal = async (page) => {
    await page.evaluate((key) => localStorage.removeItem(key), experimentalAlertKey)
}

const branchWithAPipeline = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    const bronze = await branch.createPromotionLevel("BRONZE")
    const silver = await branch.createPromotionLevel("SILVER")
    const gold = await branch.createPromotionLevel("GOLD")

    const smoke = await branch.createValidationStamp("SMOKE")

    // Oldest first, so the timeline shows `recent` at the head
    const old = await branch.createBuild()
    await old.promote(bronze)

    const recent = await branch.createBuild()
    await recent.promote(bronze)
    await recent.promote(silver)
    await recent.validate(smoke)

    return {branch, bronze, silver, gold, smoke, old, recent}
}

test('the pipeline view shows the shape of the pipeline, reached or not', async ({page, ontrack}) => {
    const {branch, bronze, silver, gold, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.checkTotalBuilds(2)

    // A level nobody has reached still renders, dimmed: the band shows what a release has to go
    // through, not only what has already happened
    await pipelinePage.checkStage(bronze, {reached: true})
    await pipelinePage.checkStage(silver, {reached: true})
    await pipelinePage.checkStage(gold, {reached: false})

    // On load with no ?build=, the most recent build is selected
    await pipelinePage.checkBuildSelected(recent)
})

test('selecting a build in the timeline changes the inspector', async ({page, ontrack}) => {
    const {branch, silver, smoke, old, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    // The most recent build reached SILVER and ran SMOKE
    await pipelinePage.checkBuildSelected(recent)
    await pipelinePage.checkInspectorPromotion(silver)
    await pipelinePage.checkInspectorValidation(smoke)

    // The older one reached neither
    await pipelinePage.selectBuild(old)
    await pipelinePage.checkBuildSelected(old)
    await pipelinePage.checkBuildNotSelected(recent)
    await pipelinePage.checkInspectorPromotion(silver, {present: false})

    // ... and the selection is now linkable
    await expect(page).toHaveURL(new RegExp(`[?&]build=${old.id}`))
})

test('a ?build= link lands on the build it names', async ({page, ontrack}) => {
    const {branch, bronze, silver, old, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo({build: old})

    // The deep link wins over the default, which would have been the most recent build
    await pipelinePage.checkBuildSelected(old)
    await pipelinePage.checkBuildNotSelected(recent)
    // ... and the inspector describes THAT build: the older one reached BRONZE and no further
    await pipelinePage.checkInspectorPromotion(bronze)
    await pipelinePage.checkInspectorPromotion(silver, {present: false})
})

test('a stage card selects its latest build', async ({page, ontrack}) => {
    const {branch, bronze, silver, old, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo({build: old})
    await pipelinePage.checkBuildSelected(old)

    // SILVER's latest build is the recent one, so picking it moves the selection
    await pipelinePage.selectStageBuild(silver)
    await pipelinePage.checkBuildSelected(recent)
    await pipelinePage.checkInspectorPromotion(bronze)
})

test('a stage card loads up to its latest build when it is below the loaded page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const bronze = await branch.createPromotionLevel("BRONZE")
    const gold = await branch.createPromotionLevel("GOLD")

    // Only the oldest build reached GOLD, and the page size is 10, so its card is not loaded
    const oldest = await branch.createBuild()
    await oldest.promote(gold)
    for (let i = 0; i < 12; i++) {
        const build = await branch.createBuild()
        await build.promote(bronze)
    }

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    // Picking it loads further pages up to it rather than doing nothing - and, crucially, the
    // selection is not corrected back to the most recent build while those pages are in flight
    await pipelinePage.selectStageBuild(gold)
    await pipelinePage.checkBuildSelected(oldest)
    await pipelinePage.checkInspectorPromotion(gold)
})

test('a branch with no builds shows neither a pipeline band nor an inspector', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    // Levels configured, but nothing has ever been built
    await branch.createPromotionLevel("BRONZE")

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    // A full band of "never reached" stages above an empty timeline states the obvious loudly; the
    // empty state below says the one thing worth saying
    await pipelinePage.checkNoPipelineBand()
    await pipelinePage.checkNoBuilds()
    await pipelinePage.checkNoInspector()
})

test('a branch with no promotion levels shows no pipeline band', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    // Hidden entirely, not an empty state: the branch simply does not work that way
    await pipelinePage.checkNoPipelineBand()
    await pipelinePage.checkBuildPresent(build)
    await pipelinePage.checkInspectorNoPromotion()
})

test('range selection still reaches the change log from the pipeline view', async ({page, ontrack}) => {
    // The pipeline view is meant to become the default way to read a branch, so it cannot silently
    // drop one of the branch page's most valuable features
    const {from, to} = await provisionChangeLog(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, from.branch)
    await pipelinePage.goTo()

    await pipelinePage.checkChangeLogButtonPresent({disabled: true})

    await pipelinePage.selectRangeBuild(from)
    await pipelinePage.selectRangeBuild(to)

    await pipelinePage.checkChangeLogButtonPresent({disabled: false})

    const changeLogPage = await pipelinePage.goToChangeLog()
    await changeLogPage.checkBuildFrom(from)
    await changeLogPage.checkBuildTo(to)
})

test('the view menu offers the pipeline view, and the choice is remembered', async ({page, ontrack}) => {
    const {branch, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    // Explicitly, because the stored view is ONE GLOBAL KEY PER USER and every UI test signs in as
    // the same admin: whatever a spec which ran earlier picked is still stored. The parameter is
    // authoritative, so it puts the page in a known state.
    await branchPage.goTo({view: 'builds'})
    await branchPage.checkContentViews(["Builds", "Pipeline"])
    await branchPage.checkContentViewSelected("Builds")

    await branchPage.openContentViewMenu()
    await branchPage.contentViewMenuItem("Pipeline").click()

    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.checkOnPage()
    await pipelinePage.checkBuildSelected(recent)
    await expect(page).toHaveURL(/[?&]view=pipeline/)

    // Back on the page without any parameter: the stored preference applies
    await page.goto(`${ontrack.connection.ui}/branch/${branch.id}`)
    await pipelinePage.checkOnPage()

    // Switches back, which both checks the menu the other way round and leaves that same shared
    // preference where the specs running after this one expect to find it
    await branchPage.selectContentView("Builds")
    await branchPage.checkBuildPresent(recent.name)
})

test('the inspector names the selected build and leads to its page', async ({page, ontrack}) => {
    // The panel is partial by design, so the way out to the full record is part of what it owes the
    // reader - and a header naming the build is what stops the two sections belonging to nothing
    const {branch, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.selectBuild(recent)
    await pipelinePage.checkInspectorNames(recent)

    // Only a browser can say the link actually lands on the build
    await pipelinePage.goToBuildFromInspector(recent)
})

test('the inspector header follows the selection', async ({page, ontrack}) => {
    const {branch, old, recent} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.selectBuild(recent)
    await pipelinePage.checkInspectorNames(recent)

    await pipelinePage.selectBuild(old)
    await pipelinePage.checkInspectorNames(old)
})

test('the pipeline view says it is experimental and invites feedback', async ({page, ontrack}) => {
    const {branch} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    await forgetExperimentalAlertDismissal(page)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.checkExperimentalAlert()
    await expect(pipelinePage.experimentalAlert()).toContainText("experimental")
    // The invitation is the point of the alert, so the link is part of what is checked
    await expect(pipelinePage.experimentalAlert().getByRole('link', {name: "GitHub Discussions"}))
        .toHaveAttribute('href', 'https://github.com/yontrack/yontrack/discussions')
})

test('the experimental alert stays dismissed once dismissed', async ({page, ontrack}) => {
    const {branch} = await branchWithAPipeline(ontrack)

    await login(page, ontrack)
    await forgetExperimentalAlertDismissal(page)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.checkExperimentalAlert()
    await pipelinePage.dismissExperimentalAlert()

    // Dismissal is persisted, not merely hidden for this render
    await page.reload()
    await pipelinePage.checkOnPage()
    await pipelinePage.checkNoExperimentalAlert()

    // ... and the shared preference is put back where the specs after this one expect it
    await forgetExperimentalAlertDismissal(page)
})
