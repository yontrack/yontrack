import {login} from "../login";
import {test} from "../../fixtures/connection";
import {BuildPage} from "./BuildPage";
import {expect} from "@playwright/test";

/**
 * "What's missing" on the build page (#2023).
 *
 * SILVER is auto promoted on BUILD and UNIT.TESTS. The build has passed BUILD only: UNIT.TESTS is
 * what it lacks.
 */
test('the readiness popover of a promotion level lists the missing stamp, then says Ready once validated', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const buildVs = await branch.createValidationStamp('BUILD')
    const unitTests = await branch.createValidationStamp('UNIT.TESTS')
    const silver = await branch.createPromotionLevel('SILVER')
    await silver.setAutoPromotionProperty({validationStamps: [buildVs, unitTests]})
    const build = await branch.createBuild()
    await build.validate(buildVs)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // The control, named for what it is about
    const control = page.getByRole('button', {name: "What's missing for SILVER"})
    await expect(control).toBeVisible()
    await control.click()

    // The missing stamp, linked to it
    const popover = page.getByTestId(`build-readiness-pl-${silver.id}-popover`)
    await expect(popover).toBeVisible()
    const missing = popover.getByTestId('readiness-item-VALIDATION-UNIT.TESTS')
    await expect(missing).toContainText('Not validated')
    await expect(missing.getByRole('link')).toHaveAttribute('href', `/validationStamp/${unitTests.id}`)
    // ... and only that one
    await expect(popover.getByTestId(/^readiness-item-/)).toHaveCount(1)
    await expect(popover.getByTestId('readiness-ready')).toHaveCount(0)

    // Validating the build from its page
    await page.getByTestId('validations').getByRole('button', {name: 'Validate'}).click()
    const dialog = page.locator('.ant-modal').filter({hasText: 'Validation stamp to promote to'})
    await expect(dialog).toBeVisible()
    await dialog.getByRole('combobox', {name: 'Validation stamp to promote to'}).click()
    await page.locator('.ant-select-item-option').filter({hasText: 'UNIT.TESTS'}).click()
    await dialog.locator('.ant-form-item').filter({hasText: 'Status'}).getByRole('combobox').click()
    await page.locator('.ant-select-item-option').filter({hasText: 'Passed'}).click()
    await dialog.getByRole('button', {name: 'OK'}).click()
    await expect(dialog).toBeHidden()

    // Clicking "Validate" closed the popover; reopened, it asks again, and now says Ready
    await expect(popover).toBeHidden()
    await control.click()
    await expect(popover.getByTestId('readiness-ready')).toContainText('Ready')
    await expect(popover.getByTestId(/^readiness-item-/)).toHaveCount(0)
})

/**
 * The same control on the slots of the build's journey: a slot requiring GOLD refuses a build which
 * has no promotion at all.
 */
test('the readiness popover of a slot lists the admission rule refusing the build', async ({page, ontrack}) => {
    const environment = await ontrack.environments.createEnvironment({})
    const project = await ontrack.createProject()
    const slot = await environment.createSlot({project})
    await ontrack.environments.addPromotionRule({slot, promotion: 'GOLD'})
    const branch = await project.createBranch()
    await branch.createPromotionLevel('GOLD')
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const control = page.getByTestId(`build-readiness-slot-${slot.id}`)
    await expect(control).toHaveAccessibleName(`What's missing for ${environment.name}`)
    await control.click()

    const popover = page.getByTestId(`build-readiness-slot-${slot.id}-popover`)
    await expect(popover.getByTestId('readiness-group-ADMISSION_RULE')).toContainText('Admission rules')
    await expect(popover.getByTestId(/^readiness-item-ADMISSION_RULE-/)).toHaveCount(1)
    await expect(popover.getByTestId('readiness-ready')).toHaveCount(0)
})
