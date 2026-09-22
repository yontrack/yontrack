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

/**
 * Commit subjects carrying a conventional-commit type, which is the only kind the semantic
 * change log shows: `SemanticChangelogRenderingServiceImpl` drops every untyped commit. The
 * `commits` above are deliberately untyped and are what the empty state is provisioned from.
 */
export const semanticCommits = [
    // Before the change log's first boundary, so it must not appear in it
    "chore(deps): bump the shared library",
    "feat(api): search owners by their phone number, closes ISS-31",
    "test: cover the owner search endpoint",
    "fix(export): write the CSV in UTF-8, closes ISS-32",
    "docs: describe the export format",
]

export const semanticIssues = {
    "ISS-31": {
        summary: "Search owners by phone number",
        type: "feature",
    },
    "ISS-32": {
        summary: "CSV export mangles accented names",
        type: "defect",
    },
}

/**
 * A change log the semantic view has something to render: two builds, the commits between them
 * carrying `feat`, `test`, `fix` and `docs` subjects, and two issues for the `issues` option to
 * put in a section of its own.
 */
export async function provisionSemanticChangeLog(ontrack) {
    const mockSCMContext = createMockSCMContext(ontrack)
    const project = await ontrack.createProject()
    await mockSCMContext.configureProjectForMockSCM(project)

    const branch = await project.createBranch()
    await mockSCMContext.configureBranchForMockSCM(branch)

    for (const key of Object.keys(semanticIssues)) {
        const {summary, type} = semanticIssues[key]
        await mockSCMContext.repositoryIssue({key, summary, type})
    }

    const from = await mockSCMContext.setBuildWithCommits(
        branch.createBuild(),
        semanticCommits.slice(0, 1),
    )
    const middle = await mockSCMContext.setBuildWithCommits(
        branch.createBuild(),
        semanticCommits.slice(1, 3),
    )
    const to = await mockSCMContext.setBuildWithCommits(
        branch.createBuild(),
        semanticCommits.slice(3),
    )

    return {from, middle, to, mockSCMContext}
}

export class SCMChangeLogPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async checkDisplayed() {
        await expect(this.page.getByText("Change log from")).toBeVisible()
    }

    /**
     * Goes to the change log page using the names (or display names) of the builds
     * instead of their IDs.
     */
    async goToByName({project, from, to, fromBranch, toBranch}) {
        const params = new URLSearchParams({from, to})
        if (fromBranch) params.set('fromBranch', fromBranch)
        if (toBranch) params.set('toBranch', toBranch)
        await this.page.goto(
            `${this.ontrack.connection.ui}/extension/scm/${encodeURIComponent(project)}/changelog?${params.toString()}`
        )
    }

    /**
     * Checks that the change log could not be displayed, and that the reason is shown.
     */
    async checkError(message) {
        await expect(this.page.getByText("This change log cannot be displayed.")).toBeVisible()
        await expect(this.page.getByText(message, {exact: true})).toBeVisible()
    }

    /**
     * Reloads the change log page, waiting for it to be fully loaded again.
     *
     * Used to check that the preferences stored in the local storage are restored.
     */
    async reload() {
        await this.page.reload()
        await this.checkDisplayed()
        await this.waitForIssuesLoaded()
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

    /**
     * Goes to the change log page using the IDs of the builds, with any extra query parameters
     * — which is how a change log link carrying a reading is shared.
     */
    async goToById({from, to, params = {}}) {
        const query = new URLSearchParams({from: String(from.id), to: String(to.id), ...params})
        await this.page.goto(`${this.ontrack.connection.ui}/extension/scm/changelog?${query.toString()}`)
        await this.checkDisplayed()
    }

    /**
     * Picks a way to read the change log from the command bar.
     *
     * @param name Label of the view — "Classic" or "Semantic"
     */
    async selectView(name) {
        await this.page.getByRole('button', {name: 'View'}).click()
        await this.page.getByRole('menuitem', {name, exact: true}).click()
    }

    /**
     * The rendered semantic change log.
     *
     * Scoped to its test id, and never matched with a bare `getByText`: the option controls do
     * a shallow `router.replace` without remounting, and Next's route announcer then holds a
     * second copy of the page title, which an unscoped matcher double-matches.
     */
    semanticContent() {
        return this.page.getByTestId('semantic-content')
    }

    async checkSemanticContent(text, {present = true} = {}) {
        const locator = this.semanticContent()
        if (present) {
            await expect(locator).toContainText(text)
        } else {
            // Changing an option puts the cell back into loading, where `PageSection` swaps a
            // Skeleton in and the content element is detached - and `not.toContainText` is
            // satisfied by a locator matching nothing at all. Asserted together, and retried
            // as a pair, so the absence is only ever read off a rendering which is there.
            await expect(async () => {
                await expect(locator).toBeVisible({timeout: 1000})
                await expect(locator).not.toContainText(text, {timeout: 1000})
            }).toPass()
        }
    }

    /**
     * The semantic view says the commits carry no conventional-commit types, rather than
     * rendering a blank panel.
     */
    async checkSemanticEmpty() {
        await expect(this.page.getByTestId('semantic-empty')).toBeVisible()
        await expect(this.semanticContent()).not.toBeVisible()
    }

    /**
     * Sets one of the semantic view's switches, clicking only when it is not already there —
     * so a test says what it wants rather than what it toggles.
     *
     * The `?<option>=` parameter is written by the switch's own handler, so asking for the value
     * the page already shows writes nothing: a `checkUrlParameter` after such a call would wait
     * for a parameter nobody is going to write.
     */
    async setSemanticOption(name, value) {
        const toggle = this.page.getByTestId(`semantic-option-${name}`)
        await expect(toggle).toBeVisible()
        const checked = await toggle.getAttribute('aria-checked') === 'true'
        if (checked !== value) {
            await toggle.click()
        }
        await expect(toggle).toHaveAttribute('aria-checked', String(value))
    }

    async selectSemanticFormat(label) {
        await this.page.getByTestId('semantic-option-format').click()
        // Scoped to the dropdown: the selected value carries the same title, so an unscoped
        // match resolves to two elements.
        await this.page.locator(`.ant-select-item-option[title="${label}"]`).click()
    }

    /**
     * The reading is in the URL, which is what makes a change log link reproduce it for
     * somebody else.
     */
    async checkUrlParameter(name, value) {
        await expect(this.page).toHaveURL(new RegExp(`[?&]${name}=${value}(&|$)`))
    }

    async copySemanticContent() {
        await this.page.getByTestId('semantic-copy').click()
    }

    async selectExportFormat(format) {
        await this.page.getByRole('button', {name: 'Export options'}).click()
        await this.page.getByRole('menuitem', {name: format}).click()
    }

    async launchExport() {
        await this.page.getByRole('button', {name: 'Export', exact: true}).click()
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
        await this.page.getByRole('button', {name: 'Export options'}).click()
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
