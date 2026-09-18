import {expect} from "@playwright/test";
import {login} from "../../core/login";
import {generate} from "@ontrack/utils";
import {createSlot} from "./slotFixtures";
import {SetupPage} from "./SetupPage";
import {EnvironmentsPage} from "./Environments";
import {test} from "../../fixtures/connection";

/**
 * The Setup page (#1793).
 *
 * Configuring environments and slots moved out of the operational screens into one page of its own,
 * reached from the matrix's **Setup** command. It is also where the "still under experiment" note
 * lives now - it used to be on every page of the feature, which is how a warning stops being read.
 */

test('the matrix Setup command opens the Setup page', async ({page, ontrack}) => {
    const {environment, slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const environments = new EnvironmentsPage(page, ontrack)
    await environments.goTo()

    const setup = await environments.setup()
    await setup.expectExperimentalNote()
    await setup.expectEnvironmentRow(environment)
    await setup.expectSlotRow(slot)
})

test('the Environments tab counts the slots of each environment', async ({page, ontrack}) => {
    const {environment, slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const setup = new SetupPage(page, ontrack)
    await setup.goTo()

    await setup.expectEnvironmentRow(environment)
    await setup.expectEnvironmentSlotCount(environment, 1)
    await setup.expectSlotRow(slot)
})

test('the Slots tab counts the rules and workflows of each slot', async ({page, ontrack}) => {
    const {slot} = await createSlot(ontrack)
    await ontrack.environments.addPromotionRule({slot, promotion: "GOLD"})
    await ontrack.environments.addManualApproval({slot})

    await login(page, ontrack)
    const setup = new SetupPage(page, ontrack)
    await setup.goTo()

    await setup.expectSlotCounts(slot, {rules: 2, workflows: 0})
})

test('a Slots row links to the slot own Setup tab', async ({page, ontrack}) => {
    const {slot} = await createSlot(ontrack)

    await login(page, ontrack)
    const setup = new SetupPage(page, ontrack)
    await setup.goTo()
    await setup.openSlotSetup(slot)

    // Landed on the slot page, on its Setup tab - not on Deployments with the tab to find.
    await expect(page.getByTestId('slot-setup')).toBeVisible()
})

test('creating an environment and a slot from the Setup page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()

    await login(page, ontrack)
    const setup = new SetupPage(page, ontrack)
    await setup.goTo()

    const name = generate("env-")
    await setup.createEnvironment({
        name,
        description: `Description for ${name}`,
        order: 100,
        tags: ['test'],
    })

    const environment = await ontrack.environments.findEnvironmentByName(name)
    await setup.expectEnvironmentRow(environment)
    await setup.expectEnvironmentSlotCount(environment, 0)

    await setup.createSlot({
        projectName: project.name,
        qualifier: '',
        description: "Slot",
        environmentNames: [name],
    })

    await setup.expectEnvironmentSlotCount(environment, 1)
})
