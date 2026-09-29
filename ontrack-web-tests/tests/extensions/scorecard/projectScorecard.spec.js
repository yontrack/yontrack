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
    await expect(section.getByTestId('scorecard-table')).toBeVisible({timeout: 60000})

    // One column for the project, no estate
    await expect(section.getByRole('columnheader', {name: 'Project'})).toBeVisible()

    // Measured, with the sample count
    await expect(section.getByTestId('scorecard-Project-delivery.leadTime-value')).toBeVisible()
    await expect(section.getByTestId('scorecard-Project-delivery.leadTime-count')).toHaveText('3 samples')
    await expect(section.getByTestId('scorecard-Project-delivery.frequency-count')).toHaveText('3 samples')
    await expect(section.getByTestId('scorecard-Project-quality.testPassRate-value')).toHaveText('100%')
    await expect(section.getByTestId('scorecard-Project-quality.testFlakiness-value')).toHaveText('33.3%')

    // No failure: neutral, not unknown
    await expect(section.getByTestId('scorecard-Project-delivery.mttr-no-failure')).toHaveText('No failure in window')
    await expect(section.getByTestId('scorecard-Project-delivery.mttr-unknown')).toHaveCount(0)

    // The column and the readings explain themselves
    await section.getByRole('button', {name: 'About the Project column'}).hover()
    await expect(page.getByTestId('scorecard-set-info-Project')).toContainText('This project read on its own')
    await section.getByRole('button', {name: 'About Lead time'}).hover()
    await expect(page.getByTestId('scorecard-reading-info-delivery.leadTime')).toContainText('first promotion at the marker level')
    // No estate, no legend for the estate columns
    await expect(section.getByTestId('scorecard-legend')).toHaveCount(0)

    // The details of each number are on the scorecard page
    await section.getByRole('link', {name: 'Details'}).click()
    const scorecardPage = new ProjectScorecardPage(page, project)
    await scorecardPage.expectOnPage()

    await expect(scorecardPage.setExplanation('Project')).toContainText('Marker: Last promotion level of each branch')

    const leadTime = scorecardPage.reading('Project', 'delivery.leadTime')
    await expect(leadTime).toBeVisible()
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-description')).toContainText('first promotion at the marker level')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('90 days')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('main (all branches, no branch model)')
    await expect(leadTime.getByTestId('reading-Project-delivery.leadTime-details')).toContainText('Promotion: GOLD on main')

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
        await expect(section.getByRole('columnheader', {name: 'Project'})).toBeVisible()
        await expect(section.getByRole('columnheader', {name: `Estate: ${estate.name}`})).toBeVisible()
        await expect(section.getByTestId('scorecard-legend')).toContainText('One column per estate')

        // The estate column says what reads the project in it
        await section.getByRole('button', {name: `About the Estate: ${estate.name} column`}).hover()
        const estateInfo = page.getByTestId(`scorecard-set-info-${estate.name}`)
        await expect(estateInfo).toContainText('Marker: Promotion: GOLD')
        await expect(estateInfo.getByTestId(`label-${label.category}:${label.name}`)).toBeVisible()

        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.leadTime`)).toContainText('Met ≤ 1d')
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.frequency`)).toContainText('Missed ≥ 5 / week')
        // No target, not judged
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.successRate`)).not.toContainText('Met')
        await expect(section.getByTestId(`scorecard-${estate.name}-delivery.successRate`)).not.toContainText('Missed')
        // The set with no estate is never judged
        await expect(section.getByTestId('scorecard-Project-delivery.leadTime')).not.toContainText('Met')

        // The estate has its own section on the scorecard page
        await section.getByRole('link', {name: 'Details'}).click()
        const scorecardPage = new ProjectScorecardPage(page, project)
        await scorecardPage.expectOnPage()
        await expect(scorecardPage.set(estate.name)).toBeVisible()
        await expect(scorecardPage.setExplanation(estate.name)).toContainText('Marker: Promotion: GOLD')
        await expect(scorecardPage.setExplanation(estate.name).getByTestId(`label-${label.category}:${label.name}`)).toBeVisible()
        await expect(scorecardPage.reading(estate.name, 'delivery.leadTime')
            .getByTestId(`reading-${estate.name}-delivery.leadTime-details`)).toContainText('≤ 1d')
    } finally {
        await deleteEstate(ontrack, estate)
    }
})
