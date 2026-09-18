import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {BuildPage} from "../../core/builds/BuildPage";
import {getBuildEnvironmentSection} from "./BuildEnvironmentSection";
import {SlotDrawerPanel} from "./SlotDrawerPanel";

/**
 * The build page's journey strip (#1794).
 *
 * The five states and their wording are covered by the chip's own Jest tests, far more cheaply than
 * a browser can. What is worth a browser here is the part those tests cannot reach: that
 * `Build.journey` really does answer one state per slot on a live instance - the very states a
 * developer asking "where is my build?" comes to read - and that a chip opens the shared drawer at
 * an address which survives a reload.
 */

/**
 * Takes a build all the way into a slot.
 */
const deploy = async (ontrack, slot, build) => {
    const pipeline = await slot.createPipeline({build})
    await ontrack.environments.startPipeline({pipeline})
    await ontrack.environments.finishPipeline({pipeline})
    return pipeline
}

test('the strip says where the build stands in every slot of its project', async ({page, ontrack}) => {
    const staging = await ontrack.environments.createEnvironment({order: 1})
    const production = await ontrack.environments.createEnvironment({order: 2})
    const project = await ontrack.createProject()
    const stagingSlot = await staging.createSlot({project})
    const productionSlot = await production.createSlot({project})

    // Production takes GOLD builds only, and this branch never declares a GOLD promotion level -
    // which is what makes every one of its builds ineligible there. See `sharedComponents.spec.js`.
    await ontrack.environments.addPromotionRule({slot: productionSlot, promotion: "GOLD"})

    const branch = await project.createBranch()

    // Four builds, arranged so that the four staging states are all on the board at once.
    const superseded = await branch.createBuild()
    await deploy(ontrack, stagingSlot, superseded)

    const deployed = await branch.createBuild()
    await deploy(ontrack, stagingSlot, deployed)

    const inProgress = await branch.createBuild()
    // A candidate, left where it is: it cancels nothing, since the two above are done.
    await stagingSlot.createPipeline({build: inProgress})

    const eligible = await branch.createBuild()

    await login(page, ontrack)

    const expectStates = async (build, {staging: stagingState}) => {
        await new BuildPage(page, build).goTo()
        const section = await getBuildEnvironmentSection(page, build)
        await section.expectState(stagingSlot, stagingState)
        // Every slot of the project is on the strip, including the one refusing the build: an
        // environment missing from it could not be told from an environment that does not exist.
        await section.expectState(productionSlot, 'NOT_ELIGIBLE')
    }

    await expectStates(superseded, {staging: 'SUPERSEDED'})
    await expectStates(deployed, {staging: 'DEPLOYED'})
    await expectStates(inProgress, {staging: 'IN_PROGRESS'})
    await expectStates(eligible, {staging: 'ELIGIBLE'})
})

test('a chip opens the slot drawer, at an address which survives a reload', async ({page, ontrack}) => {
    const environment = await ontrack.environments.createEnvironment({})
    const project = await ontrack.createProject()
    const slot = await environment.createSlot({project})
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await deploy(ontrack, slot, build)

    await login(page, ontrack)
    await new BuildPage(page, build).goTo()

    const section = await getBuildEnvironmentSection(page, build)
    const drawer = new SlotDrawerPanel(page, slot)
    await drawer.expectClosed()

    await section.openDrawer(slot)
    await drawer.expectOpen()
    await drawer.expectTitle(`${environment.name} · ${project.name}`)
    await drawer.expectDeployed(build.name)

    // `?slot=` is the drawer: the build page's address carries it just as the matrix's does, so the
    // reload brings it back rather than dropping the reader onto a bare build page.
    await expect(page).toHaveURL(new RegExp(`slot=${slot.id}`))
    await page.reload()
    await drawer.expectOpen()
    await drawer.expectDeployed(build.name)
})
