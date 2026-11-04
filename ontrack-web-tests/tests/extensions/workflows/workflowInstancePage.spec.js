import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {generate} from "@ontrack/utils";
import {subscribeToWorkflow} from "../../support/workflows";
import {PromotionRunPage} from "../../core/promotionRuns/PromotionRunPage";

const openInstancePage = async (page, ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel()

    const workflowName = generate("wf-")
    await subscribeToWorkflow(pl, {name: workflowName})

    const build = await branch.createBuild()
    const run = await build.promote(pl)

    await login(page, ontrack)
    const runPage = new PromotionRunPage(page, run)
    await runPage.goTo()

    const card = await runPage.getWorkflowCard(workflowName, {status: "Success"})
    return await card.openWorkflow()
}

test('the workflow instance page takes the full width of a wide window', async ({page, ontrack}) => {
    await page.setViewportSize({width: 1900, height: 1000})
    const instancePage = await openInstancePage(page, ontrack)

    await instancePage.expectGraphSectionToSpanThePage()

    // The information of the selected node goes in its own column and does not change the width
    await instancePage.selectNode("build")
    await instancePage.expectGraphSectionToSpanThePage()
})

test('the workflow graph refits itself when the window is resized', async ({page, ontrack}) => {
    await page.setViewportSize({width: 1900, height: 1000})
    const instancePage = await openInstancePage(page, ontrack)
    await instancePage.expectNodesToFitInTheGraphColumn(3)

    await page.setViewportSize({width: 1100, height: 800})
    await instancePage.expectNodesToFitInTheGraphColumn(3)
})

test('the status stays on one line in a narrow window', async ({page, ontrack}) => {
    await page.setViewportSize({width: 1100, height: 800})
    const instancePage = await openInstancePage(page, ontrack)

    await instancePage.expectStatusOnOneLine()
})

test('the workflow graph nodes do not overlap, with or without their details', async ({page, ontrack}) => {
    await page.setViewportSize({width: 1900, height: 1000})
    const instancePage = await openInstancePage(page, ontrack)

    // The details are displayed by default
    await instancePage.expectNodesNotToOverlap()

    await instancePage.toggleNodeDetails()
    await instancePage.expectNodesNotToOverlap()

    await instancePage.toggleNodeDetails()
    await instancePage.expectNodesNotToOverlap()
})
