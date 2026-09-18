import {expect} from "@playwright/test";
import {generate} from "@ontrack/utils";
import {login} from "../../core/login";
import {test} from "../../fixtures/connection";
import {EnvironmentsPage} from "./Environments";
import {SlotDrawerPanel} from "./SlotDrawerPanel";

/**
 * The Environments home, as the matrix (#1791).
 *
 * Everything here is provisioned through the API and then read off the screen, because what the
 * matrix is *for* is the reading: which rows, which columns, which cell, and what the address says
 * about it. The filters are asserted through the URL as well as through the rows, since the URL is
 * where the filter lives - a filter that narrows the table but leaves the address unchanged cannot
 * be shared or reloaded, which is the whole point of putting it there.
 *
 * Every test gives its projects a name sharing a unique prefix and searches on it. The stack is
 * shared and the matrix pages twenty projects at a time, so an unfiltered matrix is whatever every
 * other spec has left behind and this one's rows may not even be on the first page.
 */

/**
 * One project across two environments, plus a second project in one of them - enough to tell a row
 * filter from a column filter apart. Both projects share the prefix, so a search on it holds them
 * both and nothing else.
 */
const twoEnvironments = async (ontrack) => {
    const prefix = generate('mx')
    const staging = await ontrack.environments.createEnvironment({order: 100, tags: ['non-production']})
    const production = await ontrack.environments.createEnvironment({order: 200, tags: [prefix]})
    const project = await ontrack.createProject(`${prefix}-app`)
    const other = await ontrack.createProject(`${prefix}-other`)
    const stagingSlot = await staging.createSlot({project})
    const productionSlot = await production.createSlot({project})
    const otherSlot = await staging.createSlot({project: other})
    return {prefix, staging, production, project, other, stagingSlot, productionSlot, otherSlot}
}

test('the matrix draws a row per project and a column per environment', async ({page, ontrack}) => {
    const {prefix, staging, production, project, other, stagingSlot, productionSlot} =
        await twoEnvironments(ontrack)

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo({query: `scope=all&project=${prefix}`})

    await matrix.checkProjectIsVisible(project)
    await matrix.checkProjectIsVisible(other)
    await matrix.checkEnvironmentIsVisible(staging.name)
    await matrix.checkEnvironmentIsVisible(production.name)
    await matrix.checkSlotIsVisible(stagingSlot)
    await matrix.checkSlotIsVisible(productionSlot)
    await matrix.checkFreshness()
})

test('a project with several qualifiers nests one row per qualifier', async ({page, ontrack}) => {
    const prefix = generate('mx')
    const production = await ontrack.environments.createEnvironment({order: 200})
    const project = await ontrack.createProject(`${prefix}-app`)
    const defaultSlot = await production.createSlot({project})
    const canarySlot = await ontrack.environments.createSlot({
        project,
        qualifier: 'canary',
        environment: production,
    })

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo({query: `scope=all&project=${prefix}`})

    // The empty qualifier IS the project row; the named one is nested under it, expanded by default
    // because there are few enough of them to read at a glance.
    await matrix.checkSlotIsVisible(defaultSlot)
    await matrix.checkQualifierRow(project, 'canary')
    await matrix.checkSlotIsVisible(canarySlot)
})

test('the filters are in the URL, so a filtered matrix can be shared and reloaded', async ({page, ontrack}) => {
    const {prefix, staging, production, project, productionSlot, otherSlot} = await twoEnvironments(ontrack)

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo({query: 'scope=all'})

    await matrix.searchProject(prefix)
    await expect(page).toHaveURL(new RegExp(`project=${prefix}`))
    await matrix.checkProjectIsVisible(project)

    // A tag filter is a *column* filter: the project stays, the staging column goes
    await page.getByTestId('matrix-tags').click()
    await page.getByTitle(prefix, {exact: true}).click()
    await page.keyboard.press('Escape')
    await expect(page).toHaveURL(new RegExp(`tags=${prefix}`))
    await matrix.checkEnvironmentIsVisible(production.name)
    await matrix.checkEnvironmentIsNotVisible(staging)
    await matrix.checkSlotIsVisible(productionSlot)
    await matrix.checkSlotIsNotVisible(otherSlot)

    // ...and the address is the filter, so a reload brings the same screen back
    await page.reload()
    await matrix.expectOnPage()
    await matrix.checkEnvironmentIsNotVisible(staging)
    await matrix.checkSlotIsVisible(productionSlot)
})

test('"Only with activity" keeps the projects with a deployment on the way', async ({page, ontrack}) => {
    const {prefix, project, other, stagingSlot} = await twoEnvironments(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await stagingSlot.createPipeline({build})

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo({query: `scope=all&project=${prefix}`})
    await matrix.checkProjectIsVisible(other)

    await matrix.toggleActivity()
    await expect(page).toHaveURL(/activity=true/)
    await matrix.checkProjectIsVisible(project)
    await matrix.checkProjectIsNotVisible(other)
})

test('favourites, and the way out of an empty one', async ({page, ontrack}) => {
    const {prefix, project, stagingSlot} = await twoEnvironments(ontrack)
    // A favourite with no slot at all, so the Favourites scope is not empty but the matrix is
    const favourite = await ontrack.createProject(`${prefix}-fav`)
    await favourite.favourite()

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    // No scope in the address, so the matrix opens on its own default: Favourites
    await matrix.goTo({query: `project=${prefix}`})

    // The user has a favourite, and nothing they starred has a slot: a hint and a way out, not a
    // blank table with no reason for it
    await expect(page.getByTestId('matrix-empty-favourites')).toBeVisible()
    await page.getByTestId('matrix-show-all').click()
    await expect(page).toHaveURL(/scope=all/)
    await matrix.checkProjectIsVisible(project)
    await matrix.checkSlotIsVisible(stagingSlot)
})

test('a cell opens the drawer, at an address of its own', async ({page, ontrack}) => {
    const {prefix, project, stagingSlot} = await twoEnvironments(ontrack)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    await stagingSlot.createPipeline({build})

    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo({query: `scope=all&project=${prefix}`})

    const drawer = new SlotDrawerPanel(page, stagingSlot)
    await drawer.expectClosed()
    await page.getByTestId(`slot-cell-${stagingSlot.id}`).click()
    await drawer.expectOpen()
    await drawer.expectInFlight(build.name)
    // The drawer's address travels beside the matrix's own filters
    await expect(page).toHaveURL(new RegExp(`slot=${stagingSlot.id}`))
})

test('the Setup command replaces the two creation commands on the header', async ({page, ontrack}) => {
    await login(page, ontrack)
    const matrix = new EnvironmentsPage(page, ontrack)
    await matrix.goTo()

    // Neither creation command is on the header any more...
    await expect(page.getByRole('button', {name: 'New environment'})).toHaveCount(0)
    await expect(page.getByRole('button', {name: 'New slot'})).toHaveCount(0)
    // ...and the experimental banner is gone from this screen
    await expect(page.getByText('still under experiment')).toHaveCount(0)

    // ...they are both on the Setup page, which the command now leads to (#1793). Until then the
    // command was a dropdown holding the two dialogs directly; it is a link now, which is the change
    // of one `href` the stepping stone was built for.
    const setup = await matrix.setup()
    await expect(page.getByTestId('setup-new-environment')).toBeVisible()
    await setup.selectTab("Slots")
    await expect(page.getByTestId('setup-new-slot')).toBeVisible()
    // ...and the experimental banner lives there, once, rather than on every screen of the feature.
    await setup.expectExperimentalNote()
})
