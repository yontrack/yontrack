import {expect} from "@playwright/test";
import {createSlot} from "./slotFixtures";
import {createPipeline} from "./pipelineFixtures";
import {login} from "../../core/login";
import {BranchPipelinePage} from "../../core/branches/branchPipeline";
import {test} from "../../fixtures/connection";

/**
 * A build's environments on the pipeline view's timeline.
 *
 * This lives on the environments side rather than next to the other pipeline view specs on purpose:
 * the view itself is core and names no extension, so what is checked here is the extension's end of
 * the decoration seam. Core stays testable without environments installed.
 *
 * It is also the only test that runs the seam whole. The Jest suite mocks `Decoration` away, so
 * nothing else exercises the dynamic import which resolves
 * `net.nemerosa.ontrack.extension.environments.ui.BuildEnvironmentsDecorations` to its renderer -
 * a resolution by naming convention, which fails silently into an error boundary when it breaks.
 */

test('a deployed build shows its environment on the pipeline timeline', async ({page, ontrack}) => {
    const {project, slot} = await createSlot(ontrack)
    // `forceDone` lands the pipeline on DONE, which is what makes the build *deployed* - the
    // decoration reads the highest deployed pipeline, so a merely started one shows nothing
    const {branch, build} = await createPipeline({project, slot, forceDone: true})

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    const decorations = pipelinePage.buildDecorations(build)
    await expect(decorations).toBeVisible()

    // The environment icon is a link to the slot, so following it is how a user gets from "this
    // build is somewhere" to "here is where". The slot id names the environment AND the project,
    // which is why the href is the assertion and the icon's tooltip - an antd overlay rendered in a
    // portal on hover, not a `title` attribute - is not.
    const link = decorations.getByRole('link')
    await expect(link).toHaveCount(1)
    await expect(link).toHaveAttribute('href', `/extension/environments/slot/${slot.id}`)
})

test('a build deployed nowhere shows no environment', async ({page, ontrack}) => {
    // The same branch, so this is the decoration being absent rather than the row being absent for
    // want of any decoration at all
    const {project, slot} = await createSlot(ontrack)
    const {branch, build: deployed} = await createPipeline({project, slot, forceDone: true})
    const undeployed = await branch.createBuild()

    await login(page, ontrack)
    const pipelinePage = new BranchPipelinePage(page, branch)
    await pipelinePage.goTo()

    await pipelinePage.checkBuildPresent(undeployed)
    await expect(pipelinePage.buildDecorations(deployed).getByRole('link')).toBeVisible()
    await expect(pipelinePage.buildDecorations(undeployed).getByRole('link')).toBeHidden()
})
