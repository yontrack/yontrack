import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {SlotDrawerPanel} from "./SlotDrawerPanel";
import {BranchDeliveryMapPage} from "../../core/branches/branchDeliveryMap";

/**
 * The surfaces phase 7 brought onto the journey chip (#1796): the build search "Deployments" column
 * and the delivery map's slot checkpoints.
 *
 * What the chip *says* in each of its five states is covered by its own Jest tests, far more cheaply
 * than a browser can. What is worth a browser here is what those cannot reach: that each of these
 * surfaces really does put a chip in front of a live deployment, and that clicking one opens the
 * shared drawer rather than navigating away.
 */

/**
 * Takes a build all the way into a slot, as `buildJourney.spec.js` does it.
 */
const deploy = async (ontrack, slot, build) => {
    const pipeline = await slot.createPipeline({build})
    await ontrack.environments.startPipeline({pipeline})
    await ontrack.environments.finishPipeline({pipeline})
    return pipeline
}

/**
 * A project with one environment, one slot, and one build deployed into it.
 */
const aDeployedBuild = async (ontrack) => {
    const environment = await ontrack.environments.createEnvironment({})
    const project = await ontrack.createProject()
    const slot = await environment.createSlot({project})
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await deploy(ontrack, slot, build)
    return {environment, project, slot, branch, build}
}

/**
 * The build search page, reached from the project. Its own page object would be a page object for
 * one spec; it lives here until a second spec needs it.
 */
const searchForEveryBuild = async (page, ontrack, project) => {
    await page.goto(`${ontrack.connection.ui}/project/search/${project.id}`)
    // Scoped to the form: the nav bar carries a search button of its own, and an unscoped role
    // lookup matches both
    const form = page.locator('form')
    // No criterion at all: the point is the column, not the filtering
    await form.getByRole('button', {name: "Search", exact: true}).click()
}

test('the build search Deployments column states where a build is, and opens the drawer', async ({page, ontrack}) => {
    const {environment, project, slot, build} = await aDeployedBuild(ontrack)

    await login(page, ontrack)
    await searchForEveryBuild(page, ontrack, project)

    // The cell is addressed by the build, which is unique here: nothing is selected, and the
    // summary rows above the table only draw a build a user has picked for a change log.
    const cell = page.getByTestId(`build-deployments-${build.id}`)
    await expect(cell).toBeVisible()

    const chip = cell.getByTestId(`journey-chip-${slot.id}`)
    // The state as the server's enum spells it, so a change of wording is a change of wording
    await expect(chip).toHaveAttribute('data-state', 'DEPLOYED')
    await expect(chip).toContainText(environment.name)

    // Clicking opens the shared drawer, at an address of its own
    const drawer = new SlotDrawerPanel(page, slot)
    await drawer.expectClosed()
    await chip.click()
    await drawer.expectOpen()
    await drawer.expectTitle(`${environment.name} · ${project.name}`)
    await drawer.expectDeployed(build.name)
})

test('a build deployed nowhere gets no chip rather than an empty one', async ({page, ontrack}) => {
    // The column is titled "Deployments"; a row with nothing to say says nothing, so that a column
    // of chips can be scanned for the builds which are actually somewhere
    const environment = await ontrack.environments.createEnvironment({})
    const project = await ontrack.createProject()
    await environment.createSlot({project})
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    await searchForEveryBuild(page, ontrack, project)

    await expect(page.getByRole('link', {name: build.name})).toBeVisible()
    await expect(page.getByTestId(`build-deployments-${build.id}`)).toHaveCount(0)
})

test('a delivery map slot checkpoint states its build is deployed, and opens the drawer', async ({page, ontrack}) => {
    const {environment, project, slot, branch, build} = await aDeployedBuild(ontrack)

    await login(page, ontrack)
    const map = new BranchDeliveryMapPage(page, branch)
    await map.goTo()

    const checkpoint = map.slotCheckpoint(slot)
    await expect(checkpoint).toBeVisible()
    await expect(checkpoint).toContainText(build.name)
    // The same word as the build page strip, the decorations and the matrix
    await expect(checkpoint.getByTestId(`journey-chip-${slot.id}`))
        .toHaveAttribute('data-state', 'DEPLOYED')

    // The name opens the drawer rather than leaving the map, which is expensive to lay out
    const drawer = new SlotDrawerPanel(page, slot)
    await drawer.expectClosed()
    await checkpoint.getByTestId(`slot-checkpoint-name-${slot.id}`).click()
    await drawer.expectOpen()
    await drawer.expectTitle(`${environment.name} · ${project.name}`)
    await drawer.expectDeployed(build.name)
})
