import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {openUserMenu, selectUserMenuItem} from "../../core/userMenu";
import {EstatesPage} from "./EstatesPage";
import {createEstate, deleteEstate, findEstate, isScorecardLicensed} from "@ontrack/extensions/scorecard/scorecard";
import {generate} from "@ontrack/utils";

/**
 * Deletes an estate if it is still there — a test failing half-way must not leave an estate
 * behind, since it would refuse the deletion of its labels.
 */
/**
 * Window and target of each reading of an estate, by reading key: the API does not say in which order.
 */
const readingConfigsByKey = (estate) =>
    Object.fromEntries(estate.readingConfigs.map(({key, windowDays, target}) => [key, {windowDays, target}]))

const cleanEstate = async (ontrack, name) => {
    const estate = await findEstate(ontrack, name)
    if (estate) await deleteEstate(ontrack, estate)
}

test('estates admin page creates, edits, recomputes and deletes an estate', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    // A project carrying the label of the estate, promoted to GOLD
    const label = await ontrack.labels().createLabel()
    const project = await ontrack.createProject()
    await ontrack.labels().setProjectLabels(project.id, [label.id])
    const branch = await project.createBranch("main")
    const gold = await branch.createPromotionLevel("GOLD")
    const build = await branch.createBuild()
    await build.promote(gold)

    const name = generate('estate-')
    try {
        await login(page, ontrack)
        // The admin manages the estates: the page is in the configurations group
        await selectUserMenuItem(page, "Configurations", "Estates")
        const estatesPage = new EstatesPage(page, ontrack)
        await estatesPage.expectOnPage()

        // Creation: labels, a promotion marker, a window and targets
        await estatesPage.create({
            name,
            description: 'Estate of the UI test',
            labels: [label],
            marker: {kind: 'Promotion level', levelName: 'GOLD'},
            readings: {
                'Lead time': {window: 30, target: 1, unit: 'days'},
                'Success rate': {target: 95},
            },
        })
        await expect(estatesPage.row(name)).toBeVisible()
        await expect(estatesPage.marker(name)).toHaveText('Promotion: GOLD')
        await expect(estatesPage.reading(name, 'delivery.leadTime')).toHaveText('Lead time: ≤ 1d, over 30 days')
        await expect(estatesPage.reading(name, 'delivery.successRate')).toHaveText('Success rate: ≥ 95%')
        await expect(estatesPage.projects(name)).toHaveText('1 project')
        await expect(estatesPage.computed(name)).toHaveText('Never')

        // Saved in the units of the API: the duration target in seconds
        const created = await findEstate(ontrack, name)
        expect(created.description).toBe('Estate of the UI test')
        expect(created.labels).toEqual([{category: label.category, name: label.name}])
        expect(created.marker).toEqual({kind: 'PROMOTION', levelName: 'GOLD', environment: null, qualifier: null})
        expect(readingConfigsByKey(created)).toEqual({
            'delivery.leadTime': {windowDays: 30, target: 86400},
            'delivery.successRate': {windowDays: null, target: 95},
        })

        // Edition: the form starts from the estate, and replaces its definition
        await page.getByTestId(`estate-edit-${name}`).click()
        const dialog = estatesPage.dialog()
        await expect(dialog.getByLabel('Name', {exact: true})).toHaveValue(name)
        await expect(dialog.getByLabel('Level name', {exact: true})).toHaveValue('GOLD')
        await expect(dialog.getByLabel('Window of Lead time', {exact: true})).toHaveValue('30')
        await expect(dialog.getByLabel('Target of Lead time', {exact: true})).toHaveValue('1')
        await expect(dialog.getByLabel('Target of Success rate', {exact: true})).toHaveValue('95')
        await estatesPage.fill({
            marker: {kind: 'Environment', environment: 'production', qualifier: 'eu'},
            readings: {
                'Lead time': {target: 12, unit: 'hours'},
                'Success rate': {target: null},
                'Frequency': {target: 5},
            },
        })
        await estatesPage.submit()
        await expect(estatesPage.marker(name)).toHaveText('Environment: production [eu]')
        await expect(estatesPage.reading(name, 'delivery.leadTime')).toHaveText('Lead time: ≤ 12h, over 30 days')
        await expect(estatesPage.reading(name, 'delivery.frequency')).toHaveText('Frequency: ≥ 5 / week')
        await expect(estatesPage.reading(name, 'delivery.successRate')).toHaveCount(0)

        const updated = await findEstate(ontrack, name)
        expect(updated.marker).toEqual({kind: 'ENVIRONMENT', levelName: null, environment: 'production', qualifier: 'eu'})
        expect(readingConfigsByKey(updated)).toEqual({
            'delivery.leadTime': {windowDays: 30, target: 43200},
            'delivery.frequency': {windowDays: null, target: 5},
        })

        // Recompute: queued, and the page waits for the readings of the estate
        await estatesPage.recompute(name)
        await expect(estatesPage.computed(name)).not.toHaveText('Never', {timeout: 60000})

        // Deletion, after a confirmation
        await estatesPage.delete(name)
        await expect(estatesPage.row(name)).toHaveCount(0)
        expect(await findEstate(ontrack, name)).toBeNull()
    } finally {
        await cleanEstate(ontrack, name)
    }
})

test('estates admin page refuses an estate with no label', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    await login(page, ontrack)
    const estatesPage = new EstatesPage(page, ontrack)
    await estatesPage.goTo()

    const name = generate('estate-')
    await page.getByTestId('estate-create').click()
    await estatesPage.fill({name})
    await estatesPage.dialog().getByRole('button', {name: 'OK', exact: true}).click()
    await expect(estatesPage.dialog().getByText('One label at least is required.')).toBeVisible()
    await estatesPage.dialog().getByRole('button', {name: 'Cancel', exact: true}).click()
    expect(await findEstate(ontrack, name)).toBeNull()
})

test('estates admin page says what each rung of the security maturity means, as a target', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    await login(page, ontrack)
    const estatesPage = new EstatesPage(page, ontrack)
    await estatesPage.goTo()

    await page.getByTestId('estate-create').click()
    await expect(estatesPage.dialog()).toBeVisible()
    await estatesPage.dialog().getByLabel('Target of Security maturity', {exact: true}).click()
    const gating = page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText: '≥ 3 · Gating'})
    await expect(gating).toContainText('A security stamp required by a promotion, or a scan which failed in the window.')
    await page.keyboard.press('Escape')
    await estatesPage.dialog().getByRole('button', {name: 'Cancel', exact: true}).click()
})

test('estates menu item and commands hidden for a user without estate management', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    const label = await ontrack.labels().createLabel()
    const estate = await createEstate(ontrack, {
        name: generate('estate-'),
        labels: [`${label.category}:${label.name}`],
    })
    try {
        // The demo user holds no global role, and therefore no estate management
        await login(page, ontrack, "demo@ontrack.local", "demo")

        const drawer = await openUserMenu(page)
        await expect(drawer.getByRole('menuitem', {name: "User information", exact: true})).toBeVisible()
        await expect(drawer.getByText("Estates", {exact: true})).not.toBeVisible()
        await page.keyboard.press('Escape')

        // The estates are readable by every user, but not manageable
        const estatesPage = new EstatesPage(page, ontrack)
        await estatesPage.goTo()
        await expect(estatesPage.row(estate.name)).toBeVisible()
        await expect(estatesPage.marker(estate.name)).toHaveText('Default')
        await expect(page.getByTestId('estate-create')).toHaveCount(0)
        await expect(page.getByTestId(`estate-edit-${estate.name}`)).toHaveCount(0)
        await expect(page.getByTestId(`estate-delete-${estate.name}`)).toHaveCount(0)
    } finally {
        await deleteEstate(ontrack, estate)
    }
})
