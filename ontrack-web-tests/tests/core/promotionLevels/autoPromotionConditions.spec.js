import {login} from "../login";
import {test} from "../../fixtures/connection";
import {BuildPage} from "../builds/BuildPage";
import {BranchPage} from "../branches/branch";
import {PromotionLevelPage} from "./PromotionLevelPage";
import {expect} from "@playwright/test";
import path from "node:path";

/**
 * SILVER is auto promoted on BUILD, on the stamps matching `.*TESTS`, and on BRONZE.
 *
 * On the build: BUILD passed, UNIT.TESTS failed, INTEGRATION.TESTS never ran, SECURITY.SCAN
 * (not required) passed, BRONZE granted - and SILVER granted by hand, unless `grantSilver` is false.
 *
 * GOLD has no auto promotion, and is never granted.
 */
const setup = async (ontrack, {grantSilver = true} = {}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const buildVs = await branch.createValidationStamp('BUILD')
    const unitTests = await branch.createValidationStamp('UNIT.TESTS')
    await branch.createValidationStamp('INTEGRATION.TESTS')
    const securityScan = await branch.createValidationStamp('SECURITY.SCAN')
    const bronze = await branch.createPromotionLevel('BRONZE')
    const silver = await branch.createPromotionLevel('SILVER')
    const gold = await branch.createPromotionLevel('GOLD', {description: 'Ready for production'})
    await silver.setAutoPromotionProperty({
        validationStamps: [buildVs],
        include: '.*TESTS',
        promotionLevels: [bronze],
    })
    const build = await branch.createBuild()
    const buildRun = await build.validate(buildVs)
    await build.validate(unitTests, {status: 'FAILED'})
    await build.validate(securityScan)
    await build.promote(bronze)
    const silverRun = grantSilver ? await build.promote(silver) : null
    return {branch, build, bronze, silver, gold, silverRun, buildRun}
}

const checkBuildConditions = async (popover, {buildRun}) => {
    const conditions = popover.getByTestId('auto-promotion-conditions')
    await expect(popover.getByText('Auto promotion conditions')).toBeVisible()
    await expect(conditions.getByTestId('auto-promotion-summary'))
        .toHaveText('1/3 validations passed · 1/1 promotion granted')
    // Ran - linked to its latest run
    await expect(conditions.getByTestId('auto-promotion-vs-link-BUILD'))
        .toHaveAttribute('href', `/validationRun/${buildRun.id}`)
    await expect(conditions.getByTestId('auto-promotion-vs-UNIT.TESTS')).toContainText('Failed')
    // Never ran
    await expect(conditions.getByTestId('auto-promotion-vs-INTEGRATION.TESTS')).toContainText('Not run')
    // Not required
    await expect(conditions.getByTestId('auto-promotion-vs-SECURITY.SCAN')).toHaveCount(0)
    // Granted
    await expect(conditions.getByTestId('auto-promotion-pl-link-BRONZE')).toBeVisible()
    await expect(conditions.getByTestId('auto-promotion-patterns')).toContainText('.*TESTS')
}

test('the promotion run popover of the branch builds view shows the auto promotion conditions', async ({page, ontrack}) => {
    const {branch, silver, silverRun, buildRun} = await setup(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await branchPage.hoverPromotionRun(silver)
    await checkBuildConditions(page.getByTestId(`promotion-run-popover-${silverRun.id}`), {buildRun})
})

test('the promotion run popover of the build page shows the auto promotion conditions', async ({page, ontrack}) => {
    const {build, silver, silverRun, buildRun} = await setup(ontrack)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const promotionsSection = await buildPage.getPromotionInfoSection()
    await promotionsSection.hoverPromotionRun(silver)
    await checkBuildConditions(page.getByTestId(`build-promotion-run-popover-${silverRun.id}`), {buildRun})
})

test('the popover of a promotion not granted on the build page shows the auto promotion conditions', async ({page, ontrack}) => {
    const {build, silver, buildRun} = await setup(ontrack, {grantSilver: false})

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const promotionsSection = await buildPage.getPromotionInfoSection()
    await promotionsSection.hoverPromotionLevel(silver)
    const popover = page.getByTestId(`build-promotion-level-popover-${silver.id}`)
    await expect(popover).toBeVisible()
    await expect(page.locator('.ant-popover-title').filter({hasText: 'SILVER'})).toContainText('Not granted')
    await checkBuildConditions(popover, {buildRun})
})

test('the popover of a promotion not granted, without auto promotion, shows its description only', async ({page, ontrack}) => {
    const {build, gold} = await setup(ontrack, {grantSilver: false})

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const promotionsSection = await buildPage.getPromotionInfoSection()
    await promotionsSection.hoverPromotionLevel(gold)
    const popover = page.getByTestId(`build-promotion-level-popover-${gold.id}`)
    await expect(popover).toContainText('Ready for production')
    await expect(page.locator('.ant-popover-title').filter({hasText: 'GOLD'})).toContainText('Not granted')
    await expect(popover.getByTestId(`build-auto-promotion-conditions-${gold.id}`)).toHaveCount(0)
    await expect(popover.getByText('Auto promotion conditions')).toHaveCount(0)
    await expect(popover.getByText('No auto promotion condition')).toHaveCount(0)
})

test('the not-granted medal of the branch builds view shows the conditions, which are only loaded on hover', async ({page, ontrack}) => {
    const {branch, build, silver, buildRun} = await setup(ontrack, {grantSilver: false})

    const conditionsRequests = []
    page.on('request', request => {
        if (request.postData()?.includes('BuildAutoPromotionConditions')) {
            conditionsRequests.push(request)
        }
    })

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const medal = branchPage.notGrantedMedal(build, silver)
    await expect(medal).toBeVisible()
    expect(conditionsRequests).toHaveLength(0)

    const popover = await branchPage.hoverNotGrantedMedal(build, silver)
    await expect(page.getByTestId(`promotion-level-not-granted-title-${silver.id}`)).toContainText('Not granted')
    await checkBuildConditions(popover, {buildRun})
    expect(conditionsRequests).toHaveLength(1)
})

test('the not-granted medal of the branch builds view opens its popover on keyboard focus', async ({page, ontrack}) => {
    const {branch, build, bronze, silver, buildRun} = await setup(ontrack, {grantSilver: false})

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const medal = branchPage.notGrantedMedal(build, silver)
    await expect(medal).toBeVisible()
    // Tabbing from the medal before it
    await page.locator(`.promotion-run-pl-${bronze.id}`).focus()
    await page.keyboard.press('Tab')
    await expect(medal).toBeFocused()

    await expect(page.getByTestId(`promotion-level-not-granted-title-${silver.id}`)).toContainText('Not granted')
    await checkBuildConditions(branchPage.notGrantedPopover(build, silver), {buildRun})
})

test('the not-granted medal of the branch builds view links to the promotion level, and names its state', async ({page, ontrack}) => {
    const {branch, build, silver} = await setup(ontrack, {grantSilver: false})

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const medal = branchPage.notGrantedMedal(build, silver)
    await expect(medal).toHaveAttribute('href', `/promotionLevel/${silver.id}`)
    await expect(medal).toHaveRole('link')
    await expect(medal).toHaveAccessibleName('SILVER, not granted')
})

test('the not-granted medal of the branch builds view is greyed, and faded only when it is an image', async ({page, ontrack}) => {
    const {branch, build, bronze, silver, gold} = await setup(ontrack, {grantSilver: false})
    await gold.setAutoPromotionProperty({include: '.*TESTS'})

    await login(page, ontrack)
    const goldPage = new PromotionLevelPage(page, gold)
    await goldPage.goTo()
    await goldPage.changeImage(path.join(__dirname, 'gold.png'))

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    // Without an image: its initials have to stay readable, so greyed but not faded
    const silverMedal = branchPage.notGrantedMedal(build, silver).locator('.ot-promotion-not-granted-medal')
    await expect(silverMedal).toHaveCSS('filter', 'grayscale(1)')
    await expect(silverMedal.getByTestId(`promotion-level-icon-${silver.id}`)).toHaveCSS('opacity', '1')

    // With an image: a greyed image can look like a granted one, so faded too
    const goldMedal = branchPage.notGrantedMedal(build, gold).locator('.ot-promotion-not-granted-medal')
    await expect(goldMedal).toHaveCSS('filter', 'grayscale(1)')
    await expect(goldMedal.getByTestId(`promotion-level-image-${gold.id}`)).toHaveCSS('opacity', '0.4')

    // A granted medal keeps its colours
    await expect(page.locator(`.promotion-run-pl-${bronze.id}`)).toBeVisible()
    await expect(page.locator(`.promotion-run-pl-${bronze.id} [data-testid="promotion-level-icon-${bronze.id}"]`)).toHaveCSS('filter', 'none')
})

test('the branch builds view has no not-granted medal for a level without auto promotion', async ({page, ontrack}) => {
    const {branch, build, silver, gold} = await setup(ontrack, {grantSilver: false})

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await expect(branchPage.notGrantedMedal(build, silver)).toBeVisible()
    await expect(branchPage.notGrantedMedal(build, gold)).toHaveCount(0)
})

test('the branch builds view shows the normal medal of a granted level, with or without auto promotion', async ({page, ontrack}) => {
    const {branch, build, silver, gold} = await setup(ontrack)
    await build.promote(gold)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await expect(page.locator(`.promotion-run-pl-${silver.id}`)).toBeVisible()
    await expect(branchPage.notGrantedMedal(build, silver)).toHaveCount(0)
    await expect(page.locator(`.promotion-run-pl-${gold.id}`)).toBeVisible()
    await expect(branchPage.notGrantedMedal(build, gold)).toHaveCount(0)
})

test("the medals of the branch builds view follow the branch's order", async ({page, ontrack}) => {
    const {branch, build, bronze, silver, gold} = await setup(ontrack, {grantSilver: false})
    await build.promote(gold)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await expect(branchPage.notGrantedMedal(build, silver)).toBeVisible()
    const medals = page.locator(`.promotion-run-pl-${bronze.id}, .promotion-run-pl-${gold.id}`)
        .or(branchPage.notGrantedMedal(build, silver))
    await expect(medals).toHaveCount(3)
    await expect(medals.nth(0)).toHaveClass(`promotion-run-pl-${bronze.id}`)
    await expect(medals.nth(1)).toHaveAccessibleName('SILVER, not granted')
    await expect(medals.nth(2)).toHaveClass(`promotion-run-pl-${gold.id}`)
})

test('a branch without auto promotion has no not-granted medal', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const bronze = await branch.createPromotionLevel('BRONZE')
    await branch.createPromotionLevel('SILVER')
    const build = await branch.createBuild()
    await build.promote(bronze)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await expect(page.locator(`.promotion-run-pl-${bronze.id}`)).toBeVisible()
    await expect(branchPage.allNotGrantedMedals()).toHaveCount(0)
})

test('the auto promotion decoration of the branch promotion levels shows the conditions', async ({page, ontrack}) => {
    const {branch} = await setup(ontrack)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()
    await branchPage.navigateToPromotions()

    await page.getByTestId('auto-promotion-decoration').hover()
    const conditions = page.getByTestId('auto-promotion-conditions')
    await expect(conditions).toBeVisible()
    // Effective stamps, in branch order
    // (by test id: the text of a row also carries the initials of the stamp's generated icon)
    const stamps = conditions.getByTestId(/^auto-promotion-vs-/)
    await expect(stamps).toHaveCount(3)
    expect(await stamps.evaluateAll(rows => rows.map(row => row.dataset.testid))).toEqual([
        'auto-promotion-vs-BUILD',
        'auto-promotion-vs-UNIT.TESTS',
        'auto-promotion-vs-INTEGRATION.TESTS',
    ])
    await expect(conditions.getByTestId('auto-promotion-pl-BRONZE')).toBeVisible()
    // Plain variant: no state for a build
    await expect(conditions.getByTestId('auto-promotion-summary')).toHaveCount(0)
    await expect(conditions.getByText('Not run')).toHaveCount(0)
})
