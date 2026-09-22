import {BranchPage} from "../branches/branch";
import {login} from "../login";
import {test} from "../../fixtures/connection";
import {PromotionLevelPage} from "./PromotionLevelPage";
import path from "node:path";
import {BranchPromotionLevelsPage} from "./BranchPromotionLevelsPage";

test('uploading and getting the image for a promotion level', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const promotionLevel = await branch.createPromotionLevel("GOLD")

    await login(page, ontrack)

    const promotionLevelPage = new PromotionLevelPage(page, promotionLevel)
    await promotionLevelPage.goTo()

    await promotionLevelPage.changeImage(path.join(__dirname, 'gold.png'))

    await promotionLevelPage.checkImage()
})

test('promotion level description is not required', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const promotionsPage = await branchPage.navigateToPromotions()
    await promotionsPage.createPromotionLevel({name: "GOLD"})

    await promotionsPage.checkPromotionLevel({name: "GOLD"})
})

test.describe('reordering', () => {

    // Each promotion level embeds its subscriptions, which makes the items tall: three of them do
    // not fit in the default 720px viewport, and a mouse gesture cannot start outside of it.
    test.use({viewport: {width: 1280, height: 1200}})

    test('reordering the promotion levels', async ({page, ontrack}) => {
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        await branch.createPromotionLevel("BRONZE")
        await branch.createPromotionLevel("SILVER")
        await branch.createPromotionLevel("GOLD")

        await login(page, ontrack)

        const promotionLevelsPage = new BranchPromotionLevelsPage(page, branch)
        await promotionLevelsPage.goTo()

        await promotionLevelsPage.waitForOrder(["BRONZE", "SILVER", "GOLD"])

        // Drag GOLD (index 2) to BRONZE (index 0) → expected order: [GOLD, BRONZE, SILVER]
        await promotionLevelsPage.dragToReorder("GOLD", "BRONZE")

        await promotionLevelsPage.waitForOrder(["GOLD", "BRONZE", "SILVER"])
    })

})
