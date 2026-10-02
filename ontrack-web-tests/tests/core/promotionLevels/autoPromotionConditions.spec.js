import {login} from "../login";
import {test} from "../../fixtures/connection";
import {BuildPage} from "../builds/BuildPage";
import {BranchPage} from "../branches/branch";
import {expect} from "@playwright/test";

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
    return {branch, build, silver, gold, silverRun, buildRun}
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
