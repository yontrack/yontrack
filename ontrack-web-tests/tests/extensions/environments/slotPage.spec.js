import {expect} from "@playwright/test";
import {login} from "../../core/login";
import {createSlot} from "./slotFixtures";
import {SlotPage} from "./SlotPage";
import {test} from "../../fixtures/connection";

/**
 * The slot page rebuilt as a header block and three tabs (#1793).
 *
 * What it replaces stacked four sections in an 8/16 split and mixed operation with configuration.
 * The tests below are about that shape: the header is the drawer's own component, the tabs are
 * separate places, and the tab is in the URL so a link can point at one.
 */

test('the slot page opens on its header block and its three tabs', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await slot.createPipeline({build})

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    // The header is the slot drawer's Now / In flight / Next - the same component.
    await slotPage.expectHeaderNeverDeployed()
    await slotPage.expectInFlight(build.name)
    // ...minus Recent, because the Deployments tab below is the whole history.
    await slotPage.expectHeaderHasNoRecentSection()

    await expect(page.getByRole('tab', {name: 'Deployments', exact: true})).toBeVisible()
    await expect(page.getByRole('tab', {name: 'Eligible builds', exact: true})).toBeVisible()
    await slotPage.expectSetupTab()
})

test('a slot page link can point at the Setup tab', async ({page, ontrack}) => {
    // What the Setup page's Slots rows link to: the rules of one slot, without having to find the
    // third tab on arrival.
    const {slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goToSetup()

    await expect(page.getByText('Admission rules', {exact: true})).toBeVisible()
    await expect(page.getByText('Workflows', {exact: true})).toBeVisible()
})

test('deleting a slot from its Setup tab', async ({page, ontrack}) => {
    // "Delete slot" moved off the page header: it was the one header command of an operational page
    // that destroyed configuration, sitting next to Close.
    const {slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()
    await slotPage.delete()

    // Back on the Environments home.
    await expect(page.getByTestId('matrix-toolbar')).toBeVisible()
})

/**
 * The Deployments tab's filters, all three handled by the server so that the answer does not depend
 * on how much of the history happened to be on the page.
 */
test('filtering the deployments of a slot', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()

    // Two finished deployments and one candidate. Started in this order on purpose: starting a
    // deployment cancels the one in flight before it, so each has to be finished before the next.
    const first = await branch.createBuild()
    const firstPipeline = await slot.createPipeline({build: first})
    await ontrack.environments.startPipeline({pipeline: firstPipeline})
    await ontrack.environments.finishPipeline({pipeline: firstPipeline})

    const second = await branch.createBuild()
    const secondPipeline = await slot.createPipeline({build: second})
    await ontrack.environments.startPipeline({pipeline: secondPipeline})
    await ontrack.environments.finishPipeline({pipeline: secondPipeline})

    const third = await branch.createBuild()
    const candidate = await slot.createPipeline({build: third})

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    const table = await slotPage.getSlotPipelineTable()
    await table.expectRow(firstPipeline)
    await table.expectRow(secondPipeline)
    await table.expectRow(candidate)

    // By status
    await slotPage.filterDeploymentsByStatus("Candidate")
    await table.expectRow(candidate)
    await table.expectNoRow(firstPipeline)
    await table.expectNoRow(secondPipeline)

    // By build. The filters combine, so the status is cleared first rather than assumed away.
    await slotPage.goTo()
    await slotPage.filterDeploymentsByBuild(second.name)
    await table.expectRow(secondPipeline)
    await table.expectNoRow(firstPipeline)
    await table.expectNoRow(candidate)

    // By user: everybody on the deployment's audit trail, which for a deployment started by this
    // test is whoever is signed in.
    await slotPage.goTo()
    await slotPage.filterDeploymentsByUser("nobody-at-all")
    await table.expectNoRow(firstPipeline)
    await table.expectNoRow(secondPipeline)
    await table.expectNoRow(candidate)
})
