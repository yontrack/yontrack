import {login} from "../login";
import {generate} from "@ontrack/utils";
import {test} from "../../fixtures/connection";
import {PromotionRunPage} from "./PromotionRunPage";
import {subscribeToWorkflow} from "../../support/workflows";

test('the workflows of a promotion are shown on the promotion run page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel()

    const workflowName = generate("wf-")
    await subscribeToWorkflow(pl, {name: workflowName})

    // Promotion, which triggers the workflow
    const build = await branch.createBuild()
    const run = await build.promote(pl)

    await login(page, ontrack)
    const runPage = new PromotionRunPage(page, run)
    await runPage.goTo()

    // The Workflows section is the primary content of the page
    await runPage.expectWorkflowsSection()

    // The card for this workflow, with its status and its node strip
    const card = await runPage.getWorkflowCard(workflowName, {status: "Success"})
    await card.checkNodes(["build", "test-unit", "publish"])
    await card.checkNodeStatus("build", "SUCCESS")

    // "Open workflow" lands on the instance page
    await card.openWorkflow()
})

test('a failing workflow shows the error of the failing node', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel()

    const workflowName = generate("wf-")
    await subscribeToWorkflow(pl, {name: workflowName, failing: true})

    const build = await branch.createBuild()
    const run = await build.promote(pl)

    await login(page, ontrack)
    const runPage = new PromotionRunPage(page, run)
    await runPage.goTo()

    const card = await runPage.getWorkflowCard(workflowName, {status: "Error"})
    // The error block names the failing node and carries its error output
    await card.checkNodeError("publish", "Error in publish node")
})

test('a promotion with no workflow shows an empty state', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const pl = await branch.createPromotionLevel()
    const build = await branch.createBuild()
    const run = await build.promote(pl)

    await login(page, ontrack)
    const runPage = new PromotionRunPage(page, run)
    await runPage.goTo()

    await runPage.expectWorkflowsSection()
    await runPage.expectNoWorkflow()
})
