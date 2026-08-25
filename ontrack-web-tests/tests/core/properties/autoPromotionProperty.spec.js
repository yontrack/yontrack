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
