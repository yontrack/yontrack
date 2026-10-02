import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {selectUserMenuItem} from "../../core/userMenu";
import {EstateScorecardPage, ScorecardsPage} from "./EstateScorecardPage";
import {ProjectScorecardPage} from "./ProjectScorecardPage";
import {
    createEstate,
    deleteEstate,
    isScorecardLicensed,
    recomputeScorecardAndWait,
} from "@ontrack/extensions/scorecard/scorecard";
import {generate} from "@ontrack/utils";
import {
    createFindingsValidationStamp,
    finding,
    scanWithFindings,
} from "@ontrack/extensions/findings/findings";

test('estate view: from the user menu to the estate, its readings against its targets, and a project', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    const label = await ontrack.labels().createLabel()

    // Promoted to GOLD three times: lead times of seconds, three promotions in the window
    const promoted = await ontrack.createProject(generate('a-promoted-'))
    await ontrack.labels().setProjectLabels(promoted.id, [label.id])
    const branch = await promoted.createBranch("main")
    const gold = await branch.createPromotionLevel("GOLD")
    for (let i = 0; i < 3; i++) {
        const build = await branch.createBuild()
        await build.promote(gold)
    }

    // No promotion level: nothing to read up to
    const unread = await ontrack.createProject(generate('b-unread-'))
    await ontrack.labels().setProjectLabels(unread.id, [label.id])
    await unread.createBranch("main")

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
        await recomputeScorecardAndWait(promoted)
        await recomputeScorecardAndWait(unread)

        await login(page, ontrack)

        // The scorecards of the estates, in the information group of the user menu
        await selectUserMenuItem(page, "Information", "Scorecards")
        const scorecardsPage = new ScorecardsPage(page, ontrack)
        await scorecardsPage.expectOnPage()
        await expect(page.getByTestId(`scorecards-projects-${estate.name}`)).toHaveText('2 projects')
        await scorecardsPage.estate(estate.name).click()

        // The estate: its projects × its readings
        const estatePage = new EstateScorecardPage(page, ontrack, estate.name)
        await estatePage.expectOnPage()
        await expect(page.getByTestId('estate-explanation')).toContainText('Marker: Promotion: GOLD')
        expect(await estatePage.projectNames()).toEqual([promoted.name, unread.name])
        await expect(estatePage.column('delivery.leadTime')).toContainText('≤ 1d')

        // Judged against the targets of the estate
        await expect(estatePage.cell(promoted.name, 'delivery.leadTime')).toHaveAttribute('data-judgement', 'MET')
        await expect(estatePage.cell(promoted.name, 'delivery.frequency')).toHaveAttribute('data-judgement', 'MISSED')
        await expect(estatePage.cell(promoted.name, 'delivery.frequency')).toContainText('/ week')
        // No target: shown, not judged
        await expect(estatePage.cell(promoted.name, 'delivery.successRate')).toHaveAttribute('data-judgement', 'SHOWN')
        // No failure: neutral, not unknown
        await expect(estatePage.cell(promoted.name, 'delivery.mttr')).toHaveAttribute('data-judgement', 'NO_FAILURE')
        await expect(estatePage.cell(promoted.name, 'delivery.mttr')).toHaveText('No failure')
        // Unknown, with its reason
        const unknown = estatePage.cell(unread.name, 'delivery.leadTime')
        await expect(unknown).toHaveAttribute('data-judgement', 'UNKNOWN')
        await expect(unknown.getByLabel(/^Unknown: No marker/)).toBeVisible()

        // The roll-up row
        await expect(estatePage.rollUp('delivery.leadTime')).toContainText('1 unknown')
        await expect(estatePage.rollUp('delivery.leadTime')).toContainText('0 missed')
        await expect(estatePage.rollUp('delivery.frequency')).toContainText(/Median [0-9.]+ \/ week/)
        await expect(estatePage.rollUp('delivery.frequency')).toContainText('1 missed')

        // Nothing estimated in 6.x: no measured-only toggle
        await expect(page.getByTestId('estate-measured-only')).toHaveCount(0)

        // Sorted by a reading, the projects with no value last whatever the order
        await estatePage.column('delivery.leadTime').click()
        expect(await estatePage.projectNames()).toEqual([promoted.name, unread.name])
        await estatePage.column('delivery.leadTime').click()
        expect(await estatePage.projectNames()).toEqual([promoted.name, unread.name])
        // Sorted by name, the other way
        await estatePage.column('project').click()
        await estatePage.column('project').click()
        expect(await estatePage.projectNames()).toEqual([unread.name, promoted.name])

        // Each project links to its scorecard page, on the set of the estate
        await estatePage.project(promoted.name).click()
        const scorecardPage = new ProjectScorecardPage(page, promoted)
        await scorecardPage.expectOnPage()
        await expect(page).toHaveURL(new RegExp(`\\?set=${encodeURIComponent(estate.name)}$`))
        await expect(scorecardPage.set(estate.name)).toBeVisible()
    } finally {
        await deleteEstate(ontrack, estate)
    }
})

/**
 * A future day, as an ISO date, for an acceptance which holds.
 */
const inTenDays = () => {
    const date = new Date()
    date.setDate(date.getDate() + 10)
    return date.toISOString().substring(0, 10)
}

test('estate view: the fan-out of one finding over the projects of the estate', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the estates')

    const externalId = generate('CVE-')
    const acceptedUntil = inTenDays()
    const label = await ontrack.labels().createLabel()

    // Exposed on main, accepted on release
    const exposed = await ontrack.createProject(generate('b-exposed-'))
    await ontrack.labels().setProjectLabels(exposed.id, [label.id])
    const exposedMain = await exposed.createBranch("main")
    await scanWithFindings(exposedMain, await createFindingsValidationStamp(exposedMain, "SECURITY.IMAGE"), [
        finding({externalId, severity: "CRITICAL", title: "Remote code execution"}),
    ])
    const exposedRelease = await exposed.createBranch("release")
    await scanWithFindings(exposedRelease, await createFindingsValidationStamp(exposedRelease, "SECURITY.IMAGE"), [
        finding({externalId, severity: "CRITICAL", title: "Remote code execution", acceptedUntil}),
    ])

    // Reported, then fixed
    const fixed = await ontrack.createProject(generate('a-fixed-'))
    await ontrack.labels().setProjectLabels(fixed.id, [label.id])
    const fixedMain = await fixed.createBranch("main")
    const fixedStamp = await createFindingsValidationStamp(fixedMain, "SECURITY.IMAGE")
    await scanWithFindings(fixedMain, fixedStamp, [finding({externalId, severity: "CRITICAL"})])
    await scanWithFindings(fixedMain, fixedStamp, [finding({externalId: generate('CVE-OTHER-')})])

    // In the estate, never reporting it
    const clean = await ontrack.createProject(generate('c-clean-'))
    await ontrack.labels().setProjectLabels(clean.id, [label.id])
    const cleanMain = await clean.createBranch("main")
    await scanWithFindings(cleanMain, await createFindingsValidationStamp(cleanMain, "SECURITY.IMAGE"), [
        finding({externalId: generate('CVE-OTHER-')}),
    ])

    // Reporting it, but out of the estate
    const outside = await ontrack.createProject(generate('d-outside-'))
    const outsideMain = await outside.createBranch("main")
    await scanWithFindings(outsideMain, await createFindingsValidationStamp(outsideMain, "SECURITY.IMAGE"), [
        finding({externalId}),
    ])

    const estate = await createEstate(ontrack, {
        name: generate('estate-'),
        labels: [`${label.category}:${label.name}`],
    })
    try {
        await login(page, ontrack)
        const estatePage = new EstateScorecardPage(page, ontrack, estate.name)
        await estatePage.goTo()
        await estatePage.openFanOut()

        await estatePage.searchFinding(externalId)

        // The projects of the estate reporting it, the exposed ones first, none from outside
        expect(await estatePage.fanOutProjectNames()).toEqual([exposed.name, fixed.name])
        await expect(estatePage.fanOutSummary()).toHaveText(
            `2 projects of this estate report ${externalId}: exposed in 1, accepted in 0, resolved in 1`
        )
        await expect(page.getByTestId('estate-fanout-title')).toContainText('Remote code execution')

        // On which branches, since when
        await expect(estatePage.fanOutState(exposed.name)).toHaveText('Open')
        await expect(estatePage.fanOutBranch(exposed.name, 'main')).toContainText('since')
        await expect(estatePage.fanOutBranch(exposed.name, 'release')).toContainText(`accepted until ${acceptedUntil}`)

        // Fixed: no branch left, resolved
        await expect(estatePage.fanOutState(fixed.name)).toHaveText('Resolved')
        await expect(estatePage.fanOutBranches(fixed.name)).toContainText('Resolved')

        // An external ID no project of the estate reports
        const unknown = generate('CVE-NONE-')
        await estatePage.searchFinding(unknown)
        await expect(page.getByText(`No project of this estate reports ${unknown}.`)).toBeVisible()
    } finally {
        await deleteEstate(ontrack, estate)
    }
})
