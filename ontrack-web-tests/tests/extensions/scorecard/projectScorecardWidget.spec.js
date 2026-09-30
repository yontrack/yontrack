import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {ProjectScorecardPage} from "./ProjectScorecardPage";
import {
    createEstate,
    deleteEstate,
    isScorecardLicensed,
    recomputeScorecardAndWait,
} from "@ontrack/extensions/scorecard/scorecard";
import {generate} from "@ontrack/utils";

/**
 * The "Project scorecard" dashboard widget, on a project in two estates.
 *
 * Three builds promoted to GOLD in seconds: a lead time meeting a target of one day, and three
 * promotions in the window, missing a frequency of 5 a week. The estate sorting first has both
 * targets, the other one the lead time only.
 */
test('project scorecard widget shows the targets met in each estate of its project', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    const prefix = generate('psw')
    const project = await ontrack.createProject(`${prefix}-app`)
    const branch = await project.createBranch("main")
    const gold = await branch.createPromotionLevel("GOLD")
    for (let i = 0; i < 3; i++) {
        const build = await branch.createBuild()
        await build.promote(gold)
    }
    const label = await ontrack.labels().createLabel()
    await ontrack.labels().setProjectLabels(project.id, [label.id])
    const labels = [`${label.category}:${label.name}`]
    const marker = {kind: 'PROMOTION', levelName: 'GOLD'}
    const first = await createEstate(ontrack, {
        name: `${prefix}-a`,
        labels,
        marker,
        readings: [
            {key: 'delivery.leadTime', target: 86400},
            {key: 'delivery.frequency', target: 5},
        ],
    })
    const second = await createEstate(ontrack, {
        name: `${prefix}-b`,
        labels,
        marker,
        readings: [
            {key: 'delivery.leadTime', target: 86400},
        ],
    })
    try {
        await recomputeScorecardAndWait(project)

        // No set: the widget opens on its default one
        const dashboardName = `${prefix}-dashboard`
        const yaml = [
            `- name: "${dashboardName}"`,
            `  widgets:`,
            `    - key: "extension/scorecard/ProjectScorecard"`,
            `      layout: {x: 0, y: 0, w: 4, h: 24}`,
            `      config:`,
            `        project: "${project.name}"`,
        ].join('\n')

        await login(page, ontrack)
        await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
        await page.getByText('Import dashboards as YAML').click()
        const modal = page.getByRole('dialog')
        await expect(modal).toBeVisible()
        await modal.locator('textarea').fill(yaml)
        await modal.getByRole('button', {name: 'Import'}).click()
        await expect(page.getByRole('dialog')).not.toBeVisible()
        await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
        await page.getByText(dashboardName).click()

        await expect(page.getByText(`Scorecard · ${project.name}`)).toBeVisible()

        // A tab per set, the first estate by name selected
        const tabs = page.getByRole('tab')
        await expect(tabs).toHaveText(['Project', `${first.name} · 1/2`, `${second.name} · 1/1`])
        await expect(page.getByRole('tab', {selected: true})).toHaveText(`${first.name} · 1/2`)
        await expect(page.getByRole('img', {name: `1 of 2 targets met in ${first.name}`})).toBeVisible()
        const judged = page.getByTestId('scorecard-widget-judged')
        await expect(judged.getByRole('listitem')).toHaveCount(2)
        await expect(judged.getByRole('listitem').first()).toContainText('Met')
        await expect(judged.getByRole('listitem').first()).toContainText('Lead time')
        await expect(judged.getByRole('listitem').last()).toContainText('Missed')
        await expect(judged.getByRole('listitem').last()).toContainText('Frequency')
        await expect(page.getByTestId('scorecard-widget-footer')).toContainText('Up to promotion GOLD')

        // The other estate
        await page.getByRole('tab', {name: `${second.name} · 1/1`}).click()
        await expect(page.getByRole('img', {name: `1 of 1 target met in ${second.name}`})).toBeVisible()

        // The Project set, never judged
        await page.getByRole('tab', {name: 'Project'}).click()
        const projectView = page.getByTestId('scorecard-widget-project')
        await expect(projectView).toContainText('No targets')
        await expect(projectView.getByTestId('scorecard-widget-tile-delivery.leadTime')).toBeVisible()
        await expect(page.getByTestId('scorecard-widget-footer')).toContainText("Up to each branch's last promotion")

        // Opens the scorecard page on the set it shows
        await page.getByRole('tab', {name: `${second.name} · 1/1`}).click()
        await page.getByRole('link', {name: 'Open scorecard'}).click()
        const scorecardPage = new ProjectScorecardPage(page, project)
        await scorecardPage.expectOnPage()
        await expect(scorecardPage.setCard(second.name)).toHaveAttribute('data-selected', 'true')
        await expect(scorecardPage.set(second.name)).toBeVisible()
    } finally {
        await deleteEstate(ontrack, first)
        await deleteEstate(ontrack, second)
    }
})
