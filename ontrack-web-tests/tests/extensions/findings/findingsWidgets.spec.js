import {expect} from "@playwright/test";
import {generate} from "@ontrack/utils";
import {login} from "../../core/login";
import {test} from "../../fixtures/connection";
import {ProjectFindingsPage} from "./ProjectFindingsPage";
import {createFindingsValidationStamp, finding, scanWithFindings} from "@ontrack/extensions/findings/findings";

/**
 * A project with findings on two branches:
 *
 * - `main`: CVE-A (CRITICAL) open
 * - `release`: CVE-B (HIGH) open, CVE-C (MEDIUM) resolved
 * - `old`: CVE-D (LOW) open, but the branch is disabled and does not count for the project, which
 *   reads it as resolved
 */
const provisionFindings = async (ontrack) => {
    const project = await ontrack.createProject()
    const main = await project.createBranch("main")
    const release = await project.createBranch("release")
    const old = await project.createBranch("old")

    const mainImage = await createFindingsValidationStamp(main, "SECURITY.IMAGE")
    await scanWithFindings(main, mainImage, [finding({externalId: "CVE-A", severity: "CRITICAL"})])

    const releaseImage = await createFindingsValidationStamp(release, "SECURITY.IMAGE")
    await scanWithFindings(release, releaseImage, [
        finding({externalId: "CVE-B", severity: "HIGH"}),
        finding({externalId: "CVE-C", severity: "MEDIUM"}),
    ])
    await scanWithFindings(release, releaseImage, [finding({externalId: "CVE-B", severity: "HIGH"})])

    const oldImage = await createFindingsValidationStamp(old, "SECURITY.IMAGE")
    await scanWithFindings(old, oldImage, [finding({externalId: "CVE-D", severity: "LOW"})])
    await old.disableBranch()

    return project
}

const importDashboard = async (page, yaml) => {
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Import dashboards as YAML').click()
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()
    await modal.locator('textarea').fill(yaml)
    await modal.getByRole('button', {name: 'Import'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
}

test('findings widgets show the findings of a project and of a branch by severity', async ({page, ontrack}) => {
    const project = await provisionFindings(ontrack)

    const dashboardName = generate('findings-dashboard-')
    const yaml = [
        `- name: "${dashboardName}"`,
        `  widgets:`,
        `    - key: "extension/findings/ProjectFindings"`,
        `      layout: {x: 0, y: 0, w: 6, h: 20}`,
        `      config:`,
        `        project: "${project.name}"`,
        `        showBranches: true`,
        `    - key: "extension/findings/BranchFindings"`,
        `      layout: {x: 6, y: 0, w: 6, h: 20}`,
        `      config:`,
        `        project: "${project.name}"`,
        `        branch: "release"`,
    ].join('\n')

    await login(page, ontrack)
    await importDashboard(page, yaml)
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(dashboardName).click()

    // Project: the disabled branch does not count
    await expect(page.getByTestId('project-findings-open-CRITICAL')).toHaveText('Critical 1')
    await expect(page.getByTestId('project-findings-open-HIGH')).toHaveText('High 1')
    await expect(page.getByTestId('project-findings-open-LOW')).toHaveText('Low 0')
    // CVE-C, and CVE-D which is exposed on no branch that counts
    await expect(page.getByTestId('project-findings-resolved')).toHaveText('2')
    const rows = page.getByTestId('project-findings-branches').locator('tbody tr.ant-table-row')
    await expect(rows).toHaveCount(2)
    await expect(page.getByTestId('project-findings-branches')).not.toContainText('old')

    // Branch
    await expect(page.getByTestId('branch-findings-open-HIGH')).toHaveText('High 1')
    await expect(page.getByTestId('branch-findings-open-CRITICAL')).toHaveText('Critical 0')
    await expect(page.getByTestId('branch-findings-resolved')).toHaveText('1')

    // A count leads to the findings it counts, on the branch
    await page.getByTestId('branch-findings-open-HIGH').click()
    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.expectOnPage()
    await expect(page).toHaveURL(/branch=release/)
    await expect(page).toHaveURL(/severity=HIGH/)
    await findingsPage.expectFindings(["CVE-B"])
})
