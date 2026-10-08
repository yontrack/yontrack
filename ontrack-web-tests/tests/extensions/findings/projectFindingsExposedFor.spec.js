import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {ProjectFindingsPage} from "./ProjectFindingsPage";
import {FindingPage} from "./FindingPage";
import {ProjectScorecardPage} from "../scorecard/ProjectScorecardPage";
import {createFindingsValidationStamp, finding, scanWithFindings} from "@ontrack/extensions/findings/findings";
import {isScorecardLicensed, recomputeScorecardAndWait} from "@ontrack/extensions/scorecard/scorecard";

/**
 * A project with three findings on `main`, scanned twice:
 *
 * - CVE-OLD (LOW), reported by both scans: the longest exposed;
 * - CVE-NEW (CRITICAL), reported by the second scan only;
 * - CVE-FIXED (CRITICAL), reported by the first scan only: fixed by the second one.
 *
 * By default, the most severe first, then the most recently seen: CVE-NEW, CVE-FIXED, CVE-OLD.
 * By exposure: CVE-OLD, CVE-NEW, then the fixed CVE-FIXED.
 */
const provisionFindings = async (ontrack) => {
    const project = await ontrack.createProject()
    const main = await project.createBranch("main")
    const image = await createFindingsValidationStamp(main, "SECURITY.IMAGE")
    await scanWithFindings(main, image, [
        finding({externalId: "CVE-OLD", severity: "LOW"}),
        finding({externalId: "CVE-FIXED", severity: "CRITICAL"}),
    ])
    await scanWithFindings(main, image, [
        finding({externalId: "CVE-OLD", severity: "LOW"}),
        finding({externalId: "CVE-NEW", severity: "CRITICAL"}),
    ])
    return project
}

test('project findings sorted by how long they have been exposed', async ({page, ontrack}) => {
    const project = await provisionFindings(ontrack)
    await login(page, ontrack)

    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.goTo()
    await findingsPage.expectFindings(["CVE-NEW", "CVE-FIXED", "CVE-OLD"])
    expect(await findingsPage.externalIds()).toEqual(["CVE-NEW", "CVE-FIXED", "CVE-OLD"])

    // Sorted by exposure, the order in the URL
    await findingsPage.exposedForHeader().click()
    await expect(page).toHaveURL(/[?&]sort=EXPOSED_FOR/)
    await expect(findingsPage.exposedForHeader()).toHaveAttribute('aria-sort', 'descending')
    await expect.poll(() => findingsPage.externalIds()).toEqual(["CVE-OLD", "CVE-NEW", "CVE-FIXED"])

    // The fixed finding says how long its fix took
    const fixedRow = findingsPage.table().locator('tbody tr.ant-table-row', {hasText: 'CVE-FIXED'})
    await expect(fixedRow).toContainText('fixed after')

    // Kept on reload
    await page.reload()
    await expect.poll(() => findingsPage.externalIds()).toEqual(["CVE-OLD", "CVE-NEW", "CVE-FIXED"])

    // Back to the default order
    await findingsPage.exposedForHeader().click()
    await expect(page).not.toHaveURL(/sort=/)
    await expect.poll(() => findingsPage.externalIds()).toEqual(["CVE-NEW", "CVE-FIXED", "CVE-OLD"])
})

test('sorting the project findings goes back to the first page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const main = await project.createBranch("main")
    const image = await createFindingsValidationStamp(main, "SECURITY.IMAGE")
    await scanWithFindings(main, image, Array.from({length: 21}, (_, index) =>
        finding({externalId: `CVE-${String(index).padStart(2, '0')}`, severity: "LOW"})
    ))
    await login(page, ontrack)

    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.goTo()
    const pagination = page.locator('.ant-pagination')
    await pagination.locator('.ant-pagination-item-2').click()
    await expect(findingsPage.table().locator('tbody tr.ant-table-row')).toHaveCount(1)

    await findingsPage.exposedForHeader().click()
    await expect(pagination.locator('.ant-pagination-item-active')).toHaveText('1')
    await expect(findingsPage.table().locator('tbody tr.ant-table-row')).toHaveCount(20)
})

test('finding page links to the remediation time on the scorecard of the project', async ({page, ontrack}) => {
    test.skip(!(await isScorecardLicensed(ontrack)), 'The licence does not allow the scorecard')
    const project = await provisionFindings(ontrack)
    await recomputeScorecardAndWait(project)
    await login(page, ontrack)

    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.goTo()
    await findingsPage.table().getByRole('link', {name: "CVE-NEW", exact: true}).click()
    const findingPage = new FindingPage(page, ontrack)
    await findingPage.expectOnPage("CVE-NEW")

    await findingPage.scorecardRemediationLink().click()
    await expect(page).toHaveURL(
        new RegExp(`/extension/scorecard/project/${project.id}\\?set=project#reading-project-security\\.remediationTime$`)
    )
    const scorecardPage = new ProjectScorecardPage(page, project)
    await scorecardPage.expectOnPage()
    const tile = scorecardPage.reading('Project', 'security.remediationTime')
    await expect(tile).toHaveAttribute('id', 'reading-project-security.remediationTime')
    await expect(tile).toBeInViewport()
})
