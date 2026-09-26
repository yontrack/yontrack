import {login} from "../login";
import {test} from "../../fixtures/connection";
import {BuildPage} from "../builds/BuildPage";
import {BranchPage} from "../branches/branch";
import {expect} from "@playwright/test";

/**
 * SILVER is auto promoted on BUILD, on the stamps matching `.*TESTS`, and on BRONZE.
 *
 * On the build: BUILD passed, UNIT.TESTS failed, INTEGRATION.TESTS never ran, SECURITY.SCAN
 * (not required) passed, BRONZE granted - and SILVER granted by hand.
 */
const setup = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const buildVs = await branch.createValidationStamp('BUILD')
    const unitTests = await branch.createValidationStamp('UNIT.TESTS')
    await branch.createValidationStamp('INTEGRATION.TESTS')
    const securityScan = await branch.createValidationStamp('SECURITY.SCAN')
    const bronze = await branch.createPromotionLevel('BRONZE')
    const silver = await branch.createPromotionLevel('SILVER')
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
    const silverRun = await build.promote(silver)
    return {branch, build, silver, silverRun, buildRun}
}

const checkBuildConditions = async (popover, {buildRun}) => {
    const conditions = popover.getByTestId('auto-promotion-conditions')
    await expect(popover.getByText('Auto promotion conditions')).toBeVisible()
    await expect(conditions.getByTestId('auto-promotion-summary'))
        .toHaveText('1/3 validations passed · 1/1 promotions granted')
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
