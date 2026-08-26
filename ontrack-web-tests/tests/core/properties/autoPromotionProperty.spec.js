import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../login";
import {PromotionLevelPage} from "../promotionLevels/PromotionLevelPage";

const AUTO_PROMOTION = 'general.AutoPromotionPropertyType'

test('the auto promotion property form displays the validation stamp and promotion level selectors', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const vs = await branch.createValidationStamp()
    const bronze = await branch.createPromotionLevel('BRONZE')
    const silver = await branch.createPromotionLevel('SILVER')

    await silver.setAutoPromotionProperty({
        validationStamps: [vs],
        promotionLevels: [bronze],
    })

    await login(page, ontrack)

    const promotionLevelPage = new PromotionLevelPage(page, silver)
    await promotionLevelPage.goTo()

    const properties = await promotionLevelPage.openProperties()
    const dialog = await properties.editProperty(AUTO_PROMOTION)

    // Both selectors must be rendered, with the values which were set beforehand
    await dialog.checkSelectedValues('auto-promotion-validation-stamps', [vs.name])
    await dialog.checkSelectedValues('auto-promotion-promotion-levels', [bronze.name])

    await dialog.cancel()
})

test('the auto revoke checkbox reflects the stored property and saves', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const vs = await branch.createValidationStamp()
    const silver = await branch.createPromotionLevel('SILVER')

    // Stored with auto revoke off
    await silver.setAutoPromotionProperty({
        validationStamps: [vs],
        autoRevoke: false,
    })

    await login(page, ontrack)

    const promotionLevelPage = new PromotionLevelPage(page, silver)
    await promotionLevelPage.goTo()

    const properties = await promotionLevelPage.openProperties()
    const dialog = await properties.editProperty(AUTO_PROMOTION)

    const autoRevoke = dialog.field('auto-promotion-auto-revoke')
    await expect(autoRevoke).not.toBeChecked()

    // Turns it on and saves
    await autoRevoke.check()
    await dialog.submit()

    // The saved property carries the flag
    await expect.poll(() => silver.getAutoPromotionProperty().then(it => it?.autoRevoke)).toBe(true)

    // ... and the form reloads it as checked
    const reopened = await properties.editProperty(AUTO_PROMOTION)
    await expect(reopened.field('auto-promotion-auto-revoke')).toBeChecked()
    await reopened.cancel()
})
