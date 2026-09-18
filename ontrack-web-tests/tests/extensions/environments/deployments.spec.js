import {test} from "../../fixtures/connection";
import {createSlot} from "./slotFixtures";
import {login} from "../../core/login";
import {BuildPage} from "../../core/builds/BuildPage";
import {getBuildEnvironmentSection} from "./BuildEnvironmentSection";

test('starting a deployment from the build page', async ({page, ontrack}) => {
    const {environment, project, slot} = await createSlot(ontrack)

    const branch = await project.createBranch()
    const build = await branch.createBuild()

    // Going to the build page
    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // Gets the build environment section
    const buildEnvironmentSection = await getBuildEnvironmentSection(page, build)

    // Expecting a deployment button for this build & environment
    await buildEnvironmentSection.expectBuildDeployButton({environment})

    // Launching the deployment. Since #1797 the cell's button opens the shared deploy dialog, where
    // the slot is chosen.
    await buildEnvironmentSection.buildDeploy({environment, slot})
})
