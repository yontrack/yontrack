import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {ProjectPage} from "../../core/projects/project";
import {ProjectFindingsPage} from "./ProjectFindingsPage";
import {
    createFindingsValidationStamp,
    finding,
    scanWithFindings,
} from "@ontrack/extensions/findings/findings";

/**
 * A future day, as an ISO date, for an acceptance which holds.
 */
const inTenDays = () => {
    const date = new Date()
    date.setDate(date.getDate() + 10)
    return date.toISOString().substring(0, 10)
}

/**
 * A project with findings on two branches:
 *
 * - `main`: CVE-A (CRITICAL) open; CVE-B (HIGH) and CVE-C (MEDIUM) fixed by the second scan.
 * - `release`: CVE-B (HIGH) and CVE-D (LOW) open, CVE-E (HIGH) accepted, and the DAST rule
 *   10038 (MEDIUM) reported by zap.
 *
 * In the project: CRITICAL 1, HIGH 1, MEDIUM 1, LOW 1 open; CVE-E accepted; CVE-C resolved.
 */
const provisionFindings = async (ontrack) => {
    const project = await ontrack.createProject()
    const main = await project.createBranch("main")
    const release = await project.createBranch("release")

    const mainImage = await createFindingsValidationStamp(main, "SECURITY.IMAGE")
    await scanWithFindings(main, mainImage, [
        finding({externalId: "CVE-A", severity: "CRITICAL"}),
        finding({externalId: "CVE-B", severity: "HIGH"}),
        finding({externalId: "CVE-C", severity: "MEDIUM"}),
    ])
    await scanWithFindings(main, mainImage, [
        finding({externalId: "CVE-A", severity: "CRITICAL"}),
    ])

    const releaseImage = await createFindingsValidationStamp(release, "SECURITY.IMAGE")
    await scanWithFindings(release, releaseImage, [
        finding({externalId: "CVE-B", severity: "HIGH"}),
        finding({externalId: "CVE-D", severity: "LOW"}),
        finding({externalId: "CVE-E", severity: "HIGH", acceptedUntil: inTenDays()}),
    ])
    const releaseDast = await createFindingsValidationStamp(release, "SECURITY.DAST")
    await scanWithFindings(release, releaseDast, [
        finding({externalId: "10038", severity: "MEDIUM", location: "", title: "CSP header not set"}),
    ], {scanner: "zap", kind: "DAST"})

    return project
}

test('project page Security section shows the open findings by severity and their branch exposure', async ({page, ontrack}) => {
    const project = await provisionFindings(ontrack)
    await login(page, ontrack)

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    const section = page.getByTestId('project-security')
    await expect(section).toBeVisible()
    await expect(section.getByText('Security', {exact: true})).toBeVisible()

    // Open findings by severity
    await expect(section.getByTestId('security-open-CRITICAL')).toHaveText('1')
    await expect(section.getByTestId('security-open-HIGH')).toHaveText('1')
    await expect(section.getByTestId('security-open-MEDIUM')).toHaveText('1')
    await expect(section.getByTestId('security-open-LOW')).toHaveText('1')
    await expect(section.getByTestId('security-open-UNKNOWN')).toHaveText('0')
    await expect(section.getByTestId('security-accepted')).toHaveText('1')
    await expect(section.getByTestId('security-resolved')).toHaveText('1')

    // Exposure per branch, the most exposed first
    const rows = section.getByTestId('security-branches').locator('tbody tr.ant-table-row')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(0)).toContainText('main')
    await expect(rows.nth(1)).toContainText('release')
    await expect(section.getByTestId('security-branch-main-CRITICAL')).toHaveText('1')
    await expect(section.getByTestId('security-branch-main-total')).toHaveText('1')
    await expect(section.getByTestId('security-branch-release-total')).toHaveText('3')

    // A count leads to the findings it counts
    await section.getByTestId('security-open-HIGH').click()
    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.expectOnPage()
    await expect(page).toHaveURL(/severity=HIGH/)
    await expect(page).toHaveURL(/state=OPEN/)
    await findingsPage.expectFindings(["CVE-B"])
})

test('project page Security section leads to the findings open on a branch', async ({page, ontrack}) => {
    const project = await provisionFindings(ontrack)
    await login(page, ontrack)

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    await page.getByTestId('security-branch-release-total').click()
    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.expectOnPage()
    await expect(page).toHaveURL(/branch=release/)
    await findingsPage.expectFindings(["CVE-B", "CVE-D", "10038"])
})

test('project page Security section says when the project has no finding', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    await login(page, ontrack)

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    const section = page.getByTestId('project-security')
    await expect(section.getByText('No security finding has been reported for this project')).toBeVisible()
})

test('project findings page filters the findings', async ({page, ontrack}) => {
    const project = await provisionFindings(ontrack)
    await login(page, ontrack)

    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.goTo()

    // All the findings, whatever their state
    await findingsPage.expectFindings(["CVE-A", "CVE-B", "CVE-C", "CVE-D", "CVE-E", "10038"])

    // The external ID links to the page of the finding
    await expect(findingsPage.table().getByRole('link', {name: 'CVE-A', exact: true}))
        .toHaveAttribute('href', /^\/extension\/findings\/finding\/\d+$/)

    // By scanner, through the filter, which goes to the URL
    await findingsPage.filter('scanner', 'zap')
    await expect(page).toHaveURL(/scanner=zap/)
    await findingsPage.expectFindings(["10038"])

    // By state
    await findingsPage.goTo({query: 'state=ACCEPTED'})
    await findingsPage.expectFindings(["CVE-E"])
    await findingsPage.goTo({query: 'state=RESOLVED'})
    await findingsPage.expectFindings(["CVE-C"])

    // By severity and kind
    await findingsPage.goTo({query: 'severity=HIGH'})
    await findingsPage.expectFindings(["CVE-B", "CVE-E"])
    await findingsPage.goTo({query: 'kind=DAST'})
    await findingsPage.expectFindings(["10038"])

    // By branch, the state being the one on this branch
    await findingsPage.goTo({query: 'branch=main&state=RESOLVED'})
    await findingsPage.expectFindings(["CVE-B", "CVE-C"])
})
