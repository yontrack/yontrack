import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {ProjectPage} from "../../core/projects/project";
import {ProjectScorecardPage} from "./ProjectScorecardPage";
import {
    createEstate,
    createTestsValidationStamp,
    deleteEstate,
    isScorecardLicensed,
    recomputeScorecardAndWait,
    validateWithTests,
} from "@ontrack/extensions/scorecard/scorecard";
import {generate} from "@ontrack/utils";

/**
 * A project read up to GOLD on its only branch, with test runs:
 *
 * - three builds, all promoted to GOLD: lead time and frequency measured on 3 samples, no
 *   failure to restore from;
 * - a test stamp, the second build failing then passing on it: a pass rate of 100% and a
 *   flakiness of 33.3%.
 *
 * The project has no labels: it is in no estate, and has only the "Project" set.
 */
const provisionScorecard = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch("main")
    const gold = await branch.createPromotionLevel("GOLD")
    const unitTests = await createTestsValidationStamp(branch, "unit-tests")

    const first = await branch.createBuild()
    await validateWithTests(first, unitTests, {passed: 10})
    await first.promote(gold)

    const second = await branch.createBuild()
    await validateWithTests(second, unitTests, {passed: 9, failed: 1})
    await validateWithTests(second, unitTests, {passed: 10})
    await second.promote(gold)

    const third = await branch.createBuild()
    await validateWithTests(third, unitTests, {passed: 10})
    await third.promote(gold)

    return project
}

test('project page Scorecard section recomputes the readings and shows them', async ({page, ontrack}) => {
    const project = await provisionScorecard(ontrack)
    await login(page, ontrack)

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    const section = page.getByTestId('project-scorecard')
    await expect(section).toBeVisible()
    await expect(section.getByText('The readings of this project have not been computed yet')).toBeVisible()

    // The recompute is queued, and the section waits for its readings
    await section.getByTestId('scorecard-recompute').click()
    await expect(section.getByTestId('scorecard-sets')).toBeVisible({timeout: 60000})
    // No table any more
    await expect(section.getByRole('table')).toHaveCount(0)

    // One card for the project, no estate: it is selected, and never judged
    const projectCard = section.getByTestId('scorecard-set-card-Project')
    await expect(projectCard).toHaveAttribute('data-selected', 'true')
    await expect(projectCard).toContainText('Readings only, never judged')

    // Measured
    await expect(section.getByTestId('scorecard-Project-delivery.leadTime-value')).toBeVisible()
    await expect(section.getByTestId('scorecard-Project-delivery.leadTime-judgement')).toHaveText('No target')
    await expect(section.getByTestId('scorecard-Project-delivery.leadTime-target')).toHaveText('no target in this set')
    await expect(section.getByTestId('scorecard-Project-quality.testPassRate-value')).toHaveText('100%')
    await expect(section.getByTestId('scorecard-Project-quality.testFlakiness-value')).toHaveText('33.3%')

    // No failure: neutral, not unknown
    await expect(section.getByTestId('scorecard-Project-delivery.mttr-no-failure')).toHaveText('No failure in window')
    await expect(section.getByTestId('scorecard-Project-delivery.mttr-unknown')).toHaveCount(0)

    // The set and the readings explain themselves
    await section.getByRole('button', {name: 'About the Project set'}).hover()
    await expect(page.getByTestId('scorecard-set-info-Project')).toContainText('This project read on its own')
    await section.getByRole('button', {name: 'About Lead time'}).hover()
    await expect(page.getByTestId('scorecard-reading-info-delivery.leadTime')).toContainText('first promotion at the marker level')
    await expect(section.getByTestId('scorecard-legend')).toContainText('daily readings over the last 90 days')

    // The details of each number are on the scorecard page, on the same set
    await section.getByRole('link', {name: 'Details'}).click()
    const scorecardPage = new ProjectScorecardPage(page, project)
    await scorecardPage.expectOnPage()
    await expect(page).toHaveURL(/\?set=project$/)

    await expect(scorecardPage.setExplanation('Project')).toContainText('Marker: Last promotion level of each branch')

    const leadTime = scorecardPage.reading('Project', 'delivery.leadTime')
    await expect(leadTime).toBeVisible()
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-description')).toContainText('first promotion at the marker level')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('90 days')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('main (all branches, no branch model)')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('Promotion: GOLD on main')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('Samples')

    const flakiness = scorecardPage.reading('Project', 'quality.testFlakiness')
    await expect(flakiness.getByTestId('reading-Project-quality.testFlakiness-details')).toContainText('1 of 3')
    await expect(flakiness.getByTestId('reading-Project-quality.testFlakiness-details')).toContainText('unit-tests')
    // A test reading reads the branches, not the marker
    await expect(flakiness.getByTestId('reading-Project-quality.testFlakiness-details')).not.toContainText('Marker')
})

test('project page Scorecard section renders an unknown reading with its reason', async ({page, ontrack}) => {
    // No promotion level and no test stamp: nothing to read up to
    const project = await ontrack.createProject()
    await project.createBranch("main")
    await recomputeScorecardAndWait(project)

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    const section = page.getByTestId('project-scorecard')
    const leadTime = section.getByTestId('scorecard-Project-delivery.leadTime-unknown')
    await expect(leadTime).toHaveText('Unknown')
    await expect(leadTime).toHaveAttribute('aria-label', /^Unknown: No marker/)
    await leadTime.hover()
    await expect(page.getByRole('tooltip')).toContainText('No marker')

    await expect(section.getByTestId('scorecard-Project-quality.testPassRate-unknown'))
        .toHaveAttribute('aria-label', /^Unknown: No test stamp/)
})

test('project page Scorecard section judges the readings of an estate against its targets', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    const project = await provisionScorecard(ontrack)
    const label = await ontrack.labels().createLabel()
    await ontrack.labels().setProjectLabels(project.id, [label.id])
    // Lead times of seconds meet a target of one day; three promotions in the window miss 5 a week
    const estate = await createEstate(ontrack, {
        name: generate('estate-'),
        labels: [`${label.category}:${label.name}`],
        marker: {kind: 'PROMOTION', levelName: 'GOLD'},
        readings: [
            {key: 'delivery.leadTime', target: 86400},
            {key: 'delivery.frequency', target: 5},
        ],
    })
    try {
        await recomputeScorecardAndWait(project)

        await login(page, ontrack)
        const projectPage = new ProjectPage(page, ontrack, project)
        await projectPage.goTo()

        const section = page.getByTestId('project-scorecard')
        // The estate is selected by default, with the ring of its targets
        const estateCard = section.getByTestId(`scorecard-set-card-${estate.name}`)
        await expect(estateCard).toHaveAttribute('data-selected', 'true')
        await expect(section.getByTestId('scorecard-set-card-Project')).toHaveAttribute('data-selected', 'false')
        await expect(estateCard.getByRole('img', {name: `1 of 2 targets met in ${estate.name}`})).toBeVisible()
        await expect(estateCard).toContainText('Up to promotion GOLD')

        // The estate card says what reads the project in it
        await section.getByRole('button', {name: `About the Estate: ${estate.name} set`}).hover()
        const estateInfo = page.getByTestId(`scorecard-set-info-${estate.name}`)
        await expect(estateInfo).toContainText('Marker: Promotion: GOLD')
        await expect(estateInfo.getByTestId(`label-${label.category}:${label.name}`)).toBeVisible()

        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.leadTime-judgement`)).toHaveText('Met')
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.leadTime-target`)).toHaveText('target ≤ 1d')
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.frequency-judgement`)).toHaveText('Missed')
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.frequency-target`)).toHaveText('target ≥ 5 / week')
        // No target, not judged
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.successRate-judgement`)).toHaveText('No target')

        // The set with no estate is never judged
        await section.getByTestId('scorecard-set-card-Project').locator('button[aria-pressed]').click()
        await expect(section.getByTestId('scorecard-Project-delivery.leadTime-judgement')).toHaveText('No target')

        // The scorecard page opens on the set the section showed, and selects another in its URL
        await section.getByRole('link', {name: 'Details'}).click()
        const scorecardPage = new ProjectScorecardPage(page, project)
        await scorecardPage.expectOnPage()
        await expect(scorecardPage.set('Project')).toBeVisible()
        await scorecardPage.selectSet(estate.name)
        await expect(page).toHaveURL(new RegExp(`\\?set=${encodeURIComponent(estate.name)}$`))
        await expect(scorecardPage.set(estate.name)).toBeVisible()
        await expect(scorecardPage.set('Project')).toHaveCount(0)
        await expect(scorecardPage.setExplanation(estate.name)).toContainText('Marker: Promotion: GOLD')
        await expect(scorecardPage.setExplanation(estate.name).getByTestId(`label-${label.category}:${label.name}`)).toBeVisible()
        await expect(scorecardPage.reading(estate.name, 'delivery.leadTime')
            .getByTestId(`reading-${estate.name}-delivery.leadTime-details`)).toContainText('≤ 1d')

        // Straight to a set by its URL
        await scorecardPage.goTo('project')
        await expect(scorecardPage.setCard('Project')).toHaveAttribute('data-selected', 'true')
    } finally {
        await deleteEstate(ontrack, estate)
    }
})
