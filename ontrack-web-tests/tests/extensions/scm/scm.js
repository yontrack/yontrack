import {expect} from "@playwright/test";

const {createMockSCMContext} = require("@ontrack/extensions/scm/scm");
const {ontrack} = require("@ontrack/ontrack");

export const commits = [
    "ISS-20 Last commit before the change log",
    "ISS-21 Some commits for a feature",
    "ISS-21 Some fixes for a feature",
    "ISS-22 Fixing some bugs",
    "ISS-23 Fixing some CSS",
]

export const issues = {
    "ISS-20": {
        summary: "Last issue before the change log",
        type: "defect"
    },
    "ISS-21": {
        summary: "Some new feature",
        type: "feature"
    },
    "ISS-22": {
        summary: "Some fixes are needed",
        type: "defect"
    },
    "ISS-23": {
        summary: "Some nicer UI",
        type: "enhancement"
    },
}

export async function provisionChangeLog(
    ontrack,
    issueServiceId = undefined,
    issueServiceIdentifier = undefined,
) {
    const mockSCMContext = createMockSCMContext(ontrack)
    const project = await ontrack.createProject()
    await mockSCMContext.configureProjectForMockSCM(project, issueServiceIdentifier)

    const branch = await project.createBranch()
    await mockSCMContext.configureBranchForMockSCM(branch)

    for (const key of Object.keys(issues)) {
        const {summary, type} = issues[key]
        if (key === 'ISS-23') {
            await mockSCMContext.repositoryIssue({key, summary, type, issueServiceId: issueServiceId, linkedKey: 'ISS-10'})
        } else {
            await mockSCMContext.repositoryIssue({key, summary, type, issueServiceId: issueServiceId})
        }
    }

    const dependency = await ontrack.createProject()
    const depBranch = await dependency.createBranch()
    const depFrom = await depBranch.createBuild("3.0.1")
    const depTo = await depBranch.createBuild("3.0.4")

    const builds = []

    const from = await branch.createBuild()
    await from.linkTo(depFrom)
    await mockSCMContext.setBuildWithCommits(
        from,
        commits.slice(0, 1)
    )
    builds.push(from)

    builds.push(
        await mockSCMContext.setBuildWithCommits(
            branch.createBuild(),
            commits.slice(1, 3)
        )
    )

    builds.push(
        await mockSCMContext.setBuildWithCommits(
            branch.createBuild(),
            commits.slice(3, 4)
        )
    )

    const to = await branch.createBuild()
    await to.linkTo(depTo)
    await mockSCMContext.setBuildWithCommits(
        to,
        commits.slice(4)
    )
    builds.push(to)

    return {
        from,
        to,
        mockSCMContext,
        builds,
    }
}

export class SCMChangeLogPage {

    constructor(page) {
        this.page = page
    }

    async checkDisplayed() {
        await expect(this.page.getByText("Change log from")).toBeVisible()
    }

    async checkBuildFrom({name}) {
        const container = this.page.locator('#from')
        await expect(container.getByText(`From ${name}`, {exact: true})).toBeVisible()
        await expect(container.getByRole('link', {name: name})).toBeVisible()
    }

    async checkBuildTo({name}) {
        const container = this.page.locator('#to')
        await expect(container.getByText(`To ${name}`, {exact: true})).toBeVisible()
        await expect(container.getByRole('link', {name: name})).toBeVisible()
    }

    async checkCommitDiffLink() {
        const container = this.page.locator('#commits')
        await expect(container.getByRole('link', {name: 'diff'})).toBeVisible()
    }

    async checkCommitMessage(message, {present = true}) {
        const container = this.page.locator('#commits')
        const locator = container.getByText(message, {exact: false})
        if (present) {
            await expect(locator).toBeVisible()
        } else {
            await expect(locator).not.toBeVisible()
        }
    }

    async checkCommitBuild(message, mockSCMContext, build, {expected = true}) {
        const commitId = mockSCMContext.commitIdsPerMessage[message]
        const commitRow = this.page.locator(`tr[data-row-key="commit-${commitId}"]`)
        // await expect(commitRow.getByText(message)).toBeVisible()
        const buildLinkLocator = commitRow.getByRole('link', {name: build.name});
        if (expected) {
            await expect(buildLinkLocator).toBeVisible()
        } else {
            await expect(buildLinkLocator).not.toBeVisible()
        }
    }

    /**
     * The issues are loaded separately from the rest of the change log, so we must
     * wait for their loading to be complete before asserting on their content
     * (in particular before asserting that an issue is NOT displayed).
     */
    async waitForIssuesLoaded() {
        // While the issues are being loaded, the cell title is "Loading..."
        await expect(this.page.locator('#issues .ant-card-head-title')).toHaveText('Issues')
    }

    async checkIssue({key, summary, visible}) {
        const container = this.page.locator('#issues')
        const link = container.getByRole('link', {name: key});
        const text = container.getByText(summary, {exact: true});
        if (visible) {
            await expect(link).toBeVisible()
            await expect(text).toBeVisible()
        } else {
            await expect(link).not.toBeVisible()
            await expect(text).not.toBeVisible()
        }
    }

    async selectExportFormat(format) {
        await this.page.getByRole('button', {name: 'ellipsis'}).click()
        await this.page.getByRole('menuitem', {name: format}).click()
    }

    async launchExport() {
        await this.page.getByRole('button', {name: 'Export'}).click()
    }

    async checkExportedContent(expectedValue, close = true) {
        // Waits for the text area to be visible
        const input = await this.page.getByPlaceholder('Exported content')
        await expect(input).toBeVisible()

        // Checks its content
        await expect(input).toHaveValue(expectedValue)

        // Closing the dialog
        if (close) {
            await this.page.locator('#close-exported-content').click()
        }
    }

    async selectExportOptions({format, groups}) {
        console.log("Filling export config: ", {format, groups})
        await this.page.getByRole('button', {name: 'ellipsis'}).click()
        await this.page.getByRole('menuitem', {name: 'Options'}).click()

        const addGroupButton = this.page.getByRole('button', {name: "Add group"})
        await expect(addGroupButton).toBeVisible()

        // Selecting the format
        await this.page.getByTestId('format').click()
        await this.page.getByTitle(format).locator('div').click()

        // Adding all groups
        for (const groupNumber in groups) {
            const {group, types} = groups[groupNumber]
            console.log("Filling group mappings: ", {group, types})
            await addGroupButton.click()
            const containerRegex = new RegExp(`^Group ${Number(groupNumber) + 1}`)
            const container = this.page.locator('div').filter({hasText: containerRegex})

            await this.page.locator(`#groups_${groupNumber}_name`).fill(group)

            for (const typeNumber in types) {
                const type = types[typeNumber]
                await container.getByRole('button', {name: 'Add mapping'}).click()
                await container.locator(`#groups_${groupNumber}_list_${typeNumber}_mapping`).fill(type)
            }
        }

        await this.page.getByRole('button', {name: 'OK'}).click()
    }

}
