import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {BranchPage} from "../../core/branches/branch";
import {ProjectFindingsPage} from "./ProjectFindingsPage";
import {createFindingsValidationStamp, finding, scanWithFindings} from "@ontrack/extensions/findings/findings";

test('branch page Findings command gives the number of open findings and leads to them', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch("release")
    const vs = await createFindingsValidationStamp(branch, "SECURITY.IMAGE")
    await scanWithFindings(branch, vs, [
        finding({externalId: "CVE-A", severity: "CRITICAL"}),
        finding({externalId: "CVE-B", severity: "HIGH"}),
        finding({externalId: "CVE-C", severity: "HIGH"}),
    ])

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    const command = page.getByRole('link', {name: '3 open findings: 1 critical, 2 high'})
    await expect(command).toBeVisible()
    await expect(page.getByTestId('branch-findings-badge')).toContainText('3')

    await command.click()
    const findingsPage = new ProjectFindingsPage(page, project)
    await findingsPage.expectOnPage()
    await expect(page).toHaveURL(/branch=release/)
    await expect(page).toHaveURL(/state=OPEN/)
    await findingsPage.expectFindings(["CVE-A", "CVE-B", "CVE-C"])
})

test('branch page has no Findings command when no finding has been reported on the branch', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch("main")
    const vs = await createFindingsValidationStamp(branch, "SECURITY.IMAGE")
    // A clean scan
    await scanWithFindings(branch, vs, [])

    await login(page, ontrack)
    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()

    await expect(page.getByRole('button', {name: 'Validations'})).toBeVisible()
    await expect(page.getByTestId('branch-findings')).toHaveCount(0)
})
