import {expect} from "@playwright/test";
import {generate} from "@ontrack/utils";
import {login} from "../../core/login";
import {test} from "../../fixtures/connection";
import {ProjectEnvironmentsPage} from "./ProjectEnvironmentsPage";
import {SlotDrawerPanel} from "./SlotDrawerPanel";

/**
 * The project environments page, as the slot graph of slot cells (#1795).
 *
 * The page kept its route and its command on the project page and lost everything else: the builds
 * panel, the actions panel and the "pick a build *and* a slot before anything happens" flow are
 * replaced by the shared drawer and the shared deploy dialog. What is left is the one thing that is
 * genuinely project-scoped - which slots the project has, and which admits from which - and a Matrix
 * view of the same project beside it.
 *
 * Everything is provisioned through the API and then read off the screen. Each test creates its own
 * project, so nothing here depends on what the shared stack already holds.
 */

/**
 * A project across two environments, ordered - which is what gives the graph its edge:
 * `ProjectSlotGraphService` reads a slot's parents as the slots of the immediately lower
 * environment order, so two ordered environments are a two-node, one-edge graph.
 */
const twoEnvironments = async (ontrack) => {
    const prefix = generate('pg')
    const staging = await ontrack.environments.createEnvironment({order: 100})
    const production = await ontrack.environments.createEnvironment({order: 200})
    const project = await ontrack.createProject(`${prefix}-app`)
    const stagingSlot = await staging.createSlot({project})
    const productionSlot = await production.createSlot({project})
    return {prefix, staging, production, project, stagingSlot, productionSlot}
}

test('the graph draws one node per slot, and each node is a slot cell', async ({page, ontrack}) => {
    const {project, stagingSlot, productionSlot} = await twoEnvironments(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    const pipeline = await stagingSlot.createPipeline({build})
    await ontrack.environments.startPipeline({pipeline})
    await ontrack.environments.finishPipeline({pipeline})

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()

    await environments.expectGraph()
    await environments.expectNode(stagingSlot)
    await environments.expectNode(productionSlot)
    // The node says what the matrix cell says, because it *is* the matrix cell
    await environments.expectNodeDeployed(stagingSlot, build.name)
    await environments.expectFreshness()
})

test('clicking a node opens the drawer, at an address of its own', async ({page, ontrack}) => {
    const {project, stagingSlot} = await twoEnvironments(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await stagingSlot.createPipeline({build})

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()

    const drawer = new SlotDrawerPanel(page, stagingSlot)
    await drawer.expectClosed()
    await environments.clickNode(stagingSlot)
    await drawer.expectOpen()
    await drawer.expectInFlight(build.name)
    await expect(page).toHaveURL(new RegExp(`slot=${stagingSlot.id}`))
})

test('the Graph / Matrix toggle switches to the matrix of this project alone', async ({page, ontrack}) => {
    const {prefix, staging, project, stagingSlot, productionSlot} = await twoEnvironments(ontrack)
    // A second project in the same environments, to tell "the matrix" from "the matrix of this
    // project" apart
    const other = await ontrack.createProject(`${prefix}-other`)
    const otherSlot = await staging.createSlot({project: other})

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()
    await environments.expectGraph()

    await environments.selectView('Matrix')
    await expect(page).toHaveURL(/view=matrix/)
    await environments.expectMatrixPinned()
    await environments.expectMatrixSlot(stagingSlot)
    await environments.expectMatrixSlot(productionSlot)
    await environments.expectNoMatrixRowFor(other)
    await expect(page.getByTestId(`slot-cell-${otherSlot.id}`)).toHaveCount(0)

    // ...and the address is the view, so a reload comes back to the matrix
    await page.reload()
    await environments.expectMatrixPinned()

    await environments.selectView('Graph')
    await expect(page).not.toHaveURL(/view=matrix/)
    await environments.expectGraph()
})

test('the qualifier selector appears with a second qualifier, and changes the graph', async ({page, ontrack}) => {
    const {production, project, stagingSlot, productionSlot} = await twoEnvironments(ontrack)
    const canarySlot = await ontrack.environments.createSlot({
        project,
        qualifier: 'canary',
        environment: production,
    })

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()

    // The default qualifier's graph: the two slots created without one, and not the canary
    await environments.expectQualifierSelector()
    await environments.expectNode(stagingSlot)
    await environments.expectNode(productionSlot)
    await environments.expectNoNode(canarySlot)

    await environments.selectQualifier('canary')
    await expect(page).toHaveURL(/qualifier=canary/)
    await environments.expectNode(canarySlot)
    await environments.expectNoNode(stagingSlot)
})

test('a project with only the default qualifier gets no selector to choose from', async ({page, ontrack}) => {
    const {project} = await twoEnvironments(ontrack)

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()

    await environments.expectNoQualifierSelector()
})

test('the builds and actions panels are gone, and so is the experimental banner', async ({page, ontrack}) => {
    const {project} = await twoEnvironments(ontrack)

    await login(page, ontrack)
    const environments = new ProjectEnvironmentsPage(page, project)
    await environments.goTo()

    // The three-panel splitter asked for a build and a slot before it would do anything
    await expect(page.getByText('Select a build and an environment')).toHaveCount(0)
    await expect(page.getByText('Select an environment for the deployment')).toHaveCount(0)
    await expect(page.getByText('still under experiment')).toHaveCount(0)

    // Two commands, and "All environments" leads back to the home matrix. The command is a Button
    // wrapping a Link, and it is the link that navigates.
    await page.getByRole('link', {name: 'All environments'}).click()
    await expect(page.getByTestId('matrix-toolbar')).toBeVisible()
})
