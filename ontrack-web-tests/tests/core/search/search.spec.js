const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {ProjectPage} = require("../projects/project");
const {createMockSCMContext} = require("@ontrack/extensions/scm/scm");
const {SCMCommitPage} = require("./SCMCommitPage");
const {generate} = require("@ontrack/utils");
const {SCMIssuePage} = require("./SCMIssuePage");
const {CommandPalette} = require("./CommandPalette");
const {BranchPage} = require("../branches/branch");

/**
 * The ⌘K command palette (#1884): opened by its shortcut, searching, opened by Enter.
 */

test('finding a project', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    await login(page, ontrack)

    const palette = new CommandPalette(page)
    await palette.openByShortcut()
    await palette.type(project.name)

    const option = palette.groupOption(/^Project \(\d+\)$/, `${project.name}, Project`)
    await palette.expectOption(option, project.name)
    await palette.openWithEnter(option)

    await new ProjectPage(page, ontrack, project).expectOnPage()
})

test('finding a commit', async ({page, ontrack}) => {
    const mockSCMContext = createMockSCMContext(ontrack)
    const project = await ontrack.createProject()
    await mockSCMContext.configureProjectForMockSCM(project)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    // Mock commit IDs only depend on the SCM branch and the position on it: on "main", the
    // commit would share its ID with the first commit of every other mock repository, and its
    // result could be crowded out of the best results of its type. A release branch, so that the
    // default branching model shows it in the branch info of the commit.
    const scmBranch = `release/${generate("search-")}`
    await mockSCMContext.configureBranchForMockSCM(branch, scmBranch)

    const commitMessage = "Build commit message"

    const commitId = await mockSCMContext.repositoryCommit({branch: scmBranch, message: commitMessage})
    console.log(`Commit ID: ${commitId}`)
    await mockSCMContext.configureBuildForMockSCM(build, commitId)

    await ontrack.search.forceIndexation({type: "scm-commit"})

    await login(page, ontrack)

    const palette = new CommandPalette(page)
    await palette.openByShortcut()
    await palette.type(commitId)

    const option = palette.resultOptionTo(`/extension/scm/${project.name}/commit-info/${commitId}`)
    await palette.expectOption(option, commitId)
    await palette.openWithEnter(option)

    const scmCommitPage = new SCMCommitPage(page, ontrack, commitId, project)
    await scmCommitPage.expectOnPage(commitMessage)
    await scmCommitPage.expectBranchInfo({scmBranch, build: build.name})
})

test('finding an issue', async ({page, ontrack}) => {
    const mockSCMContext = createMockSCMContext(ontrack)
    const project = await ontrack.createProject()
    await mockSCMContext.configureProjectForMockSCM(project)
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    const scmBranch = "main"
    await mockSCMContext.configureBranchForMockSCM(branch, scmBranch)

    const issueKey = generate("ISS-")
    console.log(`Issue key: ${issueKey}`)
    const issueTitle = "Sample issue"
    const commitMessage = `${issueKey} Build commit message`

    await mockSCMContext.repositoryIssue({key: issueKey, summary: issueTitle, type: "defect"})
    const commitId = await mockSCMContext.repositoryCommit({branch: scmBranch, message: commitMessage})
    await mockSCMContext.configureBuildForMockSCM(build, commitId)

    await ontrack.search.forceIndexation({type: "scm-commit"}) // Includes the indexation of issues

    await login(page, ontrack)

    const palette = new CommandPalette(page)
    await palette.openByShortcut()
    await palette.type(issueKey)

    const option = palette.option(`${issueKey}, SCM Issue, in ${project.name}`)
    await palette.expectOption(option, issueKey)
    await palette.openWithEnter(option)

    const scmIssuePage = new SCMIssuePage(page, ontrack, issueKey, project)
    await scmIssuePage.expectOnPage({issueTitle, commitId})
    await scmIssuePage.expectBranchInfo({scmBranch: "main", build: build.name})
})

test('the palette lists the recently visited entities before anything is typed', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    await login(page, ontrack)

    // Visiting the project, then the branch
    await new ProjectPage(page, ontrack, project).goTo()
    await new BranchPage(page, branch).goTo()

    const palette = new CommandPalette(page)
    await palette.openBySlash()

    // The last visit first
    const options = palette.dialog().getByRole('group', {name: 'Recently visited'}).getByRole('option')
    await expect(options.nth(0)).toHaveAccessibleName(`${branch.name}, ${project.name}`)
    await expect(options.nth(1)).toHaveAccessibleName(project.name)

    await palette.openWithEnter(options.nth(1))
    await new ProjectPage(page, ontrack, project).expectOnPage()
})

test('the palette lists the pages of the user menu', async ({page, ontrack}) => {
    await login(page, ontrack)

    const palette = new CommandPalette(page)
    await palette.openByButton()
    await palette.type('settings')

    const option = palette.groupOption('Menu', /^Settings, /)
    await palette.openWithEnter(option)

    await expect(page).toHaveURL(/\/core\/admin\/settings$/)
})

test('the palette closes on Esc and ends on all the results', async ({page, ontrack}) => {
    await login(page, ontrack)

    const palette = new CommandPalette(page)
    await palette.openByShortcut()
    await palette.close()

    await palette.openByShortcut()
    await palette.type('some text')
    await palette.openWithEnter(palette.option('See all results for "some text"'))

    await expect(page).toHaveURL(/\/search\?q=some%20text$/)
})
