import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {createSlot} from "./slotFixtures";
import {BuildPage} from "../../core/builds/BuildPage";
import {getBuildEnvironmentSection} from "./BuildEnvironmentSection";
import {DeployDialog} from "./DeployDialog";
import {SlotDrawerPanel} from "./SlotDrawerPanel";
import {EnvironmentsPage} from "./Environments";
import {SlotPage} from "./SlotPage";

/**
 * The five shared components of the environments UI redesign, phase 1 (#1797).
 *
 * What is worth a browser here, rather than a Jest render, is everything the components do *not*
 * own: that the queries they send are accepted by the real schema, that the deploy dialog reaches
 * the real mutation, and that the drawer's `?slot=` address survives a real reload. The states of
 * each component are covered far more cheaply by their unit tests.
 */

test('starting a deployment through the dialog, from a build', async ({page, ontrack}) => {
    const {environment, project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const section = await getBuildEnvironmentSection(page, build)
    const dialog = await section.openDeployDialog()

    await dialog.expectSlotOffered(slot)
    // Nothing is in flight in this slot, so nothing would be cancelled.
    await dialog.expectNoCancellationWarning(slot)

    await dialog.deployToSlot(slot)
    await expect(page.getByRole('dialog')).toHaveCount(0)

    // The deployment now exists, which the slot's drawer will show below.
    await expect.poll(async () => {
        const pipeline = await ontrack.environments.getCurrentPipeline({slot})
        return pipeline?.build?.name
    }).toBe(build.name)
})

test('the dialog warns, by name, about the deployment it would cancel', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()

    // One deployment already under way in the slot...
    const first = await branch.createBuild()
    const pipeline = await slot.createPipeline({build: first})

    // ... and another build looking for a way in.
    const second = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, second)
    await buildPage.goTo()

    const section = await getBuildEnvironmentSection(page, second)
    const dialog = await section.openDeployDialog()

    // This is what starting a deployment has always done, silently
    // (`SlotServiceImpl.startPipeline`, "Cancelled by more recent pipeline.").
    await dialog.expectCancellationWarning(slot, {
        number: pipeline.number,
        buildName: first.name,
        status: 'CANDIDATE',
    })

    await dialog.cancel()
})

test('the dialog lists a slot which refuses the build, with the rule that refuses it', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    // The slot requires a GOLD promotion. The branch below never defines one, which is what makes
    // the build ineligible: `PromotionSlotAdmissionRule.isBuildEligible` asks whether the *branch*
    // has the promotion level at all - "could a build of this branch ever get here" - and leaves
    // "is this particular build promoted" to the deployability check further on.
    await ontrack.environments.addPromotionRule({slot, promotion: "GOLD"})

    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const section = await getBuildEnvironmentSection(page, build)
    const dialog = await section.openDeployDialog()

    // Listed, not hidden: a user is never left wondering where an environment went.
    await dialog.expectSlotRefused(slot)
    await dialog.expectSlotRefusalReason(slot, 'GOLD')

    await dialog.cancel()
})

test('the dialog warns that an unpromoted build is not deployable yet, and still starts it (#1851)', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    await ontrack.environments.addPromotionRule({slot, promotion: "BRONZE"})

    // The branch has the level, so the build is eligible; the build is not promoted, so it is not
    // deployable - a deployment started for it waits as a candidate.
    const branch = await project.createBranch()
    await branch.createPromotionLevel("BRONZE")
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    const section = await getBuildEnvironmentSection(page, build)
    const dialog = await section.openDeployDialog()

    await dialog.expectSlotNotDeployableYet(slot, 'Build not promoted')

    await dialog.deployToSlot(slot)
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await expect.poll(async () => {
        const pipeline = await ontrack.environments.getCurrentPipeline({slot})
        return pipeline && `${pipeline.build?.name} ${pipeline.status}`
    }).toBe(`${build.name} CANDIDATE`)
})

test('starting a deployment through the dialog, from a slot', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const slotPage = new SlotPage(page, slot)
    await slotPage.goTo()

    // The eligible builds are a tab of their own since #1793, and Ant Design mounts a tab's content
    // only once the tab is selected.
    await slotPage.getSlotBuilds()
    await page.getByTestId(`slot-eligible-deploy-${build.id}`).click()

    // A Deploy button beside one build in one slot names both, so the dialog narrows to that one
    // choice rather than asking again which build - it still goes through the dialog, because the
    // cancellation warning and the refusal reason are the whole reason it exists.
    const dialog = new DeployDialog(page)
    await dialog.expectOpen()
    await dialog.expectOnlySlot(slot)
    await dialog.deployToSlot(slot)
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await expect.poll(async () => {
        const pipeline = await ontrack.environments.getCurrentPipeline({slot})
        return pipeline?.build?.name
    }).toBe(build.name)
})

test('the drawer opens from a matrix cell and survives a reload through its address', async ({page, ontrack}) => {
    const {environment, project, slot} = await createSlot(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await slot.createPipeline({build})

    await login(page, ontrack)
    const environments = new EnvironmentsPage(page, ontrack)
    // Filtered on this project: the matrix pages twenty projects at a time and the stack is shared,
    // so an unfiltered first page is no guarantee that this one's row is on it.
    await environments.goTo({query: `scope=all&project=${project.name}`})

    const drawer = new SlotDrawerPanel(page, slot)
    await drawer.expectClosed()

    await page.getByTestId(`slot-cell-${slot.id}`).click()
    await drawer.expectOpen()
    await drawer.expectTitle(`${environment.name} · ${project.name}`)
    // Nothing has ever reached this slot; the candidate above is only on its way.
    await drawer.expectNeverDeployed()
    await drawer.expectInFlight(build.name)
    await drawer.expectFreshness()

    // The whole point of `?slot=`: the address is the drawer, so a reload brings it back and a
    // pasted link opens on it.
    await expect(page).toHaveURL(new RegExp(`slot=${slot.id}`))
    await page.reload()
    await drawer.expectOpen()
    await drawer.expectInFlight(build.name)
})
