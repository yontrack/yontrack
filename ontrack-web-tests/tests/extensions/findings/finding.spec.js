import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {HomePage} from "../../core/home/home";
import {ProjectFindingsPage} from "./ProjectFindingsPage";
import {FindingPage, ValidationRunFindingsPage} from "./FindingPage";
import {generate} from "@ontrack/utils";
import {
    createFindingsValidationStamp,
    finding,
    scanWithFindings,
    scanWithFindingsRun,
} from "@ontrack/extensions/findings/findings";

/**
 * A future day, as an ISO date, for an acceptance which holds.
 */
const inTenDays = () => {
    const date = new Date()
    date.setDate(date.getDate() + 10)
    return date.toISOString().substring(0, 10)
}

const URL = "https://avd.aquasec.com/nvd/cve-2021-44228"

/**
 * A finding seen by three scans: twice on `main`, then once on `release`, where it is accepted.
 * Its most recent observation is the one of `release`, which carries the acceptance.
 */
const provisionFinding = async (ontrack, externalId) => {
    const project = await ontrack.createProject()
    const main = await project.createBranch("main")
    const release = await project.createBranch("release")
    const acceptedUntil = inTenDays()

    const mainImage = await createFindingsValidationStamp(main, "SECURITY.IMAGE")
    await scanWithFindings(main, mainImage, [
        finding({externalId, severity: "HIGH", url: URL, installedVersion: "2.14.0", fixedVersion: "2.17.1"}),
        finding({externalId: "CVE-OTHER", severity: "LOW"}),
    ])
    const {validationRun} = await scanWithFindingsRun(main, mainImage, [
        finding({externalId, severity: "CRITICAL", url: URL, installedVersion: "2.14.0", fixedVersion: "2.17.1"}),
    ])

    const releaseImage = await createFindingsValidationStamp(release, "SECURITY.IMAGE")
    await scanWithFindings(release, releaseImage, [
        finding({externalId, severity: "HIGH", url: URL, acceptedUntil}),
    ])

    return {project, acceptedUntil, mainRun: validationRun}
}

test('finding page shows the exposure per branch, the acceptance and the timeline of the observations', async ({page, ontrack}) => {
    const externalId = "CVE-2021-44228"
    const {project, acceptedUntil} = await provisionFinding(ontrack, externalId)
    await login(page, ontrack)

    // From the findings of the project
    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.goTo()
    await findingsPage.table().getByRole('link', {name: externalId, exact: true}).click()

    const findingPage = new FindingPage(page, ontrack)
    await findingPage.expectOnPage(externalId)

    // What the finding is, and the link to more information
    await expect(findingPage.summary().getByTestId('finding-severity-CRITICAL')).toBeVisible()
    const link = findingPage.summary().getByTestId('finding-url')
    await expect(link).toHaveAttribute('href', URL)
    await expect(link).toHaveAttribute('target', '_blank')

    // The acceptance, from the most recent observation
    await expect(page.getByTestId('finding-acceptance-summary')).toHaveText(`Accepted until ${acceptedUntil}`)
    const acceptance = page.getByTestId('finding-acceptance')
    await expect(acceptance.getByText('Not reachable')).toBeVisible()
    await expect(acceptance.getByText('.trivyignore.yaml')).toBeVisible()

    // The exposure per branch, with its start
    await expect(findingPage.exposureRows()).toHaveCount(2)
    await expect(findingPage.exposure('main', 'SECURITY.IMAGE')).toContainText('Exposed')
    await expect(findingPage.exposure('release', 'SECURITY.IMAGE')).toContainText('Accepted')
    await expect(findingPage.exposure('release', 'SECURITY.IMAGE')).toContainText(`until ${acceptedUntil}`)
    await expect(page.getByRole('columnheader', {name: 'Exposed since'})).toBeVisible()

    // The timeline of the observations, the most recent first
    const observations = findingPage.observations()
    await expect(observations).toHaveCount(3)
    await expect(observations.nth(0)).toContainText('on release')
    await expect(observations.nth(0)).toContainText(`Accepted until ${acceptedUntil}`)
    await expect(observations.nth(1)).toContainText('on main')
    await expect(observations.nth(1).getByTestId('finding-severity-CRITICAL')).toBeVisible()
    await expect(observations.nth(2).getByTestId('finding-severity-HIGH')).toBeVisible()
    await expect(observations.nth(2)).toContainText('2.14.0')
    await expect(observations.nth(2)).toContainText('2.17.1')
})

test('a search result lands on the finding page', async ({page, ontrack}) => {
    const externalId = generate("CVE-9999-")
    await provisionFinding(ontrack, externalId)
    await login(page, ontrack)

    const homePage = new HomePage(page, ontrack)
    const palette = await homePage.search(externalId)

    const option = palette.option(new RegExp(`^${externalId}, Security finding`))
    await palette.expectOption(option, externalId)
    await palette.openWithEnter(option)

    const findingPage = new FindingPage(page, ontrack)
    await findingPage.expectOnPage(externalId)
    await expect(findingPage.exposureRows()).toHaveCount(2)
})

test('validation run detail shows the findings of the scan', async ({page, ontrack}) => {
    const externalId = "CVE-2021-44228"
    const {mainRun} = await provisionFinding(ontrack, externalId)
    await login(page, ontrack)

    const runPage = new ValidationRunFindingsPage(page, ontrack)
    await runPage.goTo(mainRun.id)

    // Only the findings of this scan: CVE-OTHER was reported by the previous one
    await expect(runPage.section()).toBeVisible()
    await expect(runPage.rows()).toHaveCount(1)
    const row = runPage.rows().first()
    await expect(row.getByTestId('finding-severity-CRITICAL')).toBeVisible()
    await expect(row).toContainText('2.14.0')
    await expect(row).toContainText('2.17.1')

    // Leading to the page of the finding
    await row.getByRole('link', {name: externalId, exact: true}).click()
    const findingPage = new FindingPage(page, ontrack)
    await findingPage.expectOnPage(externalId)
})

test('validation run detail has no findings for a run which is not a security scan', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    const run = await build.validate(validationStamp, {status: "PASSED"})
    await login(page, ontrack)

    const runPage = new ValidationRunFindingsPage(page, ontrack)
    await runPage.goTo(run.id)
    await expect(page.getByTestId('section-run-data')).toBeVisible()
    await expect(runPage.section()).toHaveCount(0)
})

test('finding page says when the finding cannot be seen', async ({page, ontrack}) => {
    await login(page, ontrack)
    const findingPage = new FindingPage(page, ontrack)
    await findingPage.goTo(2147483647)
    await expect(page.getByText('This finding does not exist, or you are not allowed to see the security findings of its project.'))
        .toBeVisible()
})
