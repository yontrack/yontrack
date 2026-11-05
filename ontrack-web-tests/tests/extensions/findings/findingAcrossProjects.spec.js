import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {selectUserMenuItem} from "../../core/userMenu";
import {SearchResultsPage} from "../../core/search/SearchResultsPage";
import {FindingPage} from "./FindingPage";
import {FindingAcrossProjectsPage} from "./FindingAcrossProjectsPage";
import {generate} from "@ontrack/utils";
import {createFindingsValidationStamp, finding, scanWithFindings} from "@ontrack/extensions/findings/findings";

/**
 * A branch of the project, with a security stamp, and a scan reporting the findings.
 */
const scannedBranch = async (project, name, findings) => {
    const branch = await project.createBranch(name)
    const stamp = await createFindingsValidationStamp(branch, "SECURITY.IMAGE")
    await scanWithFindings(branch, stamp, findings)
    return {branch, stamp}
}

test('a finding across projects: from the user menu, by a search, its resolved branches last', async ({page, ontrack}) => {
    const prefix = generate('CVE-')
    const externalId = `${prefix}-1`
    const other = `${prefix}-2`

    // Fixed on main, still exposed on the release branch
    const exposed = await ontrack.createProject(generate('b-exposed-'))
    const {branch: exposedMain, stamp: exposedStamp} = await scannedBranch(exposed, "main", [
        finding({externalId, severity: "HIGH", title: "Path traversal"}),
        finding({externalId: other, severity: "LOW"}),
    ])
    await scanWithFindings(exposedMain, exposedStamp, [finding({externalId: other, severity: "LOW"})])
    await scannedBranch(exposed, "release", [finding({externalId, severity: "HIGH", title: "Path traversal"})])

    // Reported, then fixed
    const fixed = await ontrack.createProject(generate('a-fixed-'))
    const {branch: fixedMain, stamp: fixedStamp} = await scannedBranch(fixed, "main", [finding({externalId, severity: "HIGH"})])
    await scanWithFindings(fixedMain, fixedStamp, [finding({externalId: generate('CVE-OTHER-')})])

    await login(page, ontrack)

    // The landing: a search box, an explanation, nothing listed
    await selectUserMenuItem(page, 'Information', 'Finding across projects')
    await expect(page).toHaveURL(/\/extension\/findings\/fan-out$/)
    const fanOut = new FindingAcrossProjectsPage(page, ontrack)
    await expect(fanOut.explanation()).toBeVisible()
    await expect(page.getByRole('table')).toHaveCount(0)

    // Several matches, ignoring case: their counts, and the search in the URL
    await fanOut.search(prefix.toLowerCase())
    await expect(page).toHaveURL(new RegExp(`\\?search=${prefix.toLowerCase()}$`))
    await expect(fanOut.searchedProjects(externalId)).toHaveText('Open in 1 · accepted in 0 · resolved in 1')
    await expect(fanOut.searchedProjects(other)).toHaveText('Open in 1 · accepted in 0 · resolved in 0')

    // One of them: its fan-out, the most exposed projects first, the resolved branches last
    await fanOut.searchedRow(externalId).click()
    await expect(page).toHaveURL(new RegExp(`\\?finding=${externalId}&search=${prefix.toLowerCase()}$`))
    expect(await fanOut.projectNames()).toEqual([exposed.name, fixed.name])
    await expect(fanOut.summary()).toHaveText(
        `2 projects report ${externalId}: exposed in 1, accepted in 0, resolved in 1`
    )
    await expect(page.getByRole('columnheader', {name: 'Branches'})).toBeVisible()
    await expect(fanOut.state(exposed.name)).toHaveText('Open')
    await expect(fanOut.branch(exposed.name, 'release')).toContainText('since')
    await expect(fanOut.branch(exposed.name, 'main')).toContainText('Resolved')
    await expect(fanOut.branches(exposed.name).getByTestId(/^finding-fanout-branch-/)).toHaveText([
        /^release/,
        /^main/,
    ])
    await expect(fanOut.state(fixed.name)).toHaveText('Resolved')
    await expect(fanOut.branch(fixed.name, 'main')).toContainText('Resolved')

    // Back to the results of the search
    await fanOut.backToSearch().click()
    await expect(fanOut.searchedRow(externalId)).toBeVisible()

    // One match opens its fan-out right away, and the page can be shared by its link
    await fanOut.search(other)
    await expect(page).toHaveURL(new RegExp(`\\?finding=${other}$`))
    expect(await fanOut.projectNames()).toEqual([exposed.name])
    await page.reload()
    expect(await fanOut.projectNames()).toEqual([exposed.name])
    await expect(fanOut.searchBox()).toHaveValue(other)

    // Nothing found
    const unknown = generate('CVE-NONE-')
    await fanOut.search(unknown)
    await expect(page.getByText(`No finding has an external ID containing "${unknown}".`)).toBeVisible()
})

test('a finding across projects, from the finding page and from the search results', async ({page, ontrack}) => {
    const externalId = generate('CVE-')
    const project = await ontrack.createProject()
    await scannedBranch(project, "main", [finding({externalId, severity: "HIGH"})])

    await login(page, ontrack)

    // The search results
    await new SearchResultsPage(page, ontrack).goTo({q: externalId})
    const result = page.getByTestId('finding-in-all-projects')
    await expect(result).toHaveText('In all projects →')
    await result.click()
    await expect(page).toHaveURL(new RegExp(`/extension/findings/fan-out\\?finding=${externalId}$`))
    const fanOut = new FindingAcrossProjectsPage(page, ontrack)
    expect(await fanOut.projectNames()).toEqual([project.name])

    // The finding page, opened from the fan-out
    await page.getByTestId('finding-fanout-table').locator('a[href*="/extension/findings/finding/"]').click()
    const findingPage = new FindingPage(page, ontrack)
    await findingPage.expectOnPage(externalId)
    const link = findingPage.summary().getByTestId('finding-in-all-projects')
    await expect(link).toHaveText('In all projects →')
    await link.click()
    await expect(page).toHaveURL(new RegExp(`/extension/findings/fan-out\\?finding=${externalId}$`))
    expect(await fanOut.projectNames()).toEqual([project.name])
})
