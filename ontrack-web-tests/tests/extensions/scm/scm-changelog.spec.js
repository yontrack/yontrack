// @ts-check
const {login} = require("../../core/login");
const {BranchPage} = require("../../core/branches/branch");
const {provisionChangeLog, commits, issues} = require("./scm");
const {generate, trimIndent} = require("@ontrack/utils");
const {ontrack} = require("@ontrack/ontrack");
const {test} = require("../../fixtures/connection");


const doTestSCMChangeLog = async (
    page,
    ontrack,
    issueServiceId = undefined,
    issueServiceIdentifier = undefined
) => {
    // Provisioning
    const {from, to, mockSCMContext, builds} = await provisionChangeLog(
        ontrack,
        issueServiceId,
        issueServiceIdentifier,
    )

    // Login & going to the branch page
    await login(page, ontrack)
    const branchPage = new BranchPage(page, from.branch)
    await branchPage.goTo()

    // Making sure the change log button does exist and is disabled
    await branchPage.checkChangeLogButtonPresent({disabled: true})

    // Selects the builds
    await branchPage.selectBuild(from)
    await branchPage.selectBuild(to)

    // Making sure the change log button does exist and is NOT disabled
    await branchPage.checkChangeLogButtonPresent({disabled: false})

    // Going to the change log page
    const changeLogPage = await branchPage.goToChangeLog()

    // Expecting the build sections to be visible
    await changeLogPage.checkBuildFrom(from)
    await changeLogPage.checkBuildTo(to)

    /**
     * Commits
     */

    // Expecting the build diff to be there
    await changeLogPage.checkCommitDiffLink()

    // Expecting some commits to show
    await changeLogPage.checkCommitMessage(commits[4], {present: true})
    await changeLogPage.checkCommitMessage(commits[3], {present: true})
    await changeLogPage.checkCommitMessage(commits[2], {present: true})
    await changeLogPage.checkCommitMessage(commits[1], {present: true})

    // ... some not
    await changeLogPage.checkCommitMessage(commits[0], {present: false})

    // Checks build attached to commits
    await changeLogPage.checkCommitBuild(commits[4], mockSCMContext, to, {expected: true})
    await changeLogPage.checkCommitBuild(commits[3], mockSCMContext, builds[2], {expected: true})
    await changeLogPage.checkCommitBuild(commits[2], mockSCMContext, builds[1], {expected: true})
    await changeLogPage.checkCommitBuild(commits[1], mockSCMContext, builds[1], {expected: false})

    /**
     * Issues
     */

    // The issues are loaded asynchronously, separately from the commits
    await changeLogPage.waitForIssuesLoaded()

    // ISS-20 is before the change log boundary, so it must NOT be displayed
    const absentKey = "ISS-20"

    // Expecting the issues of the change log to be displayed. Checking these first
    // makes sure the issues are really there before checking for the absent one.
    for (const key of Object.keys(issues).filter(it => it !== absentKey)) {
        const {summary} = issues[key]
        await changeLogPage.checkIssue({key, summary, visible: true})
    }

    // ... and only then, that the issue outside of the change log is not displayed
    await changeLogPage.checkIssue({key: absentKey, summary: issues[absentKey].summary, visible: false})

    // Returning the change log page for more tests
    return changeLogPage
}


test("SCM change log", async ({page, ontrack}) => {
    await doTestSCMChangeLog(page, ontrack)
})

/**
 * Goes straight to the change log page for a project using the JIRA mock issue service.
 */
const goToJiraChangeLog = async (page, ontrack) => {
    const configName = generate("mock-")
    await ontrack.configurations.jira.createConfig({
        name: configName,
        url: "mock://jira",
        user: "",
        password: "",
    })

    const {from, to} = await provisionChangeLog(ontrack, 'jira', `jira//${configName}`)

    await login(page, ontrack)
    const branchPage = new BranchPage(page, from.branch)
    await branchPage.goTo()
    await branchPage.selectBuild(from)
    await branchPage.selectBuild(to)

    const changeLogPage = await branchPage.goToChangeLog()
    await changeLogPage.waitForIssuesLoaded()
    return changeLogPage
}

test('SCM change log export format is restored after a reload', async ({page, ontrack}) => {
    const changeLogPage = await goToJiraChangeLog(page, ontrack)

    // Selecting the Markdown format, which is stored in the local storage
    await changeLogPage.selectExportFormat('Markdown')

    // Reloading the page: the format must be restored from the local storage
    await changeLogPage.reload()

    // Exporting without selecting the format again: it must still be Markdown
    await changeLogPage.launchExport()
    await changeLogPage.checkExportedContent(
        trimIndent(
            `
                * [ISS-21](mock://jira/ISS/ISS-21) Some new feature
                * [ISS-22](mock://jira/ISS/ISS-22) Some fixes are needed
                * [ISS-23](mock://jira/ISS/ISS-23) Some nicer UI
            `
        )
    )
})

test('JIRA SCM change log', async ({page, ontrack, context}) => {
    await context.grantPermissions(['clipboard-read'])
    // Creates the JIRA mock configuration
    const configName = generate("mock-")
    await ontrack.configurations.jira.createConfig({
        name: configName,
        url: "mock://jira",
        user: "",
        password: "",
    })

    // Running the test
    const changeLogPage = await doTestSCMChangeLog(
        page,
        ontrack,
        'jira',
        `jira//${configName}`
    )

    /**
     * Exporting the change log with default parameters
     */

    await changeLogPage.launchExport()
    await changeLogPage.checkExportedContent(
        trimIndent(
            `
                * ISS-21 Some new feature
                * ISS-22 Some fixes are needed
                * ISS-23 Some nicer UI
            `
        )
    )

    /**
     * Exporting the change log for Markdown and default parameters
     */

    await changeLogPage.selectExportFormat('Markdown')
    await changeLogPage.launchExport()
    await changeLogPage.checkExportedContent(
        trimIndent(
            `
                * [ISS-21](mock://jira/ISS/ISS-21) Some new feature
                * [ISS-22](mock://jira/ISS/ISS-22) Some fixes are needed
                * [ISS-23](mock://jira/ISS/ISS-23) Some nicer UI
            `
        )
    )

    /**
     * Exporting the change log for Markdown and grouping parameters
     */

    await changeLogPage.selectExportOptions({
        format: 'Markdown',
        groups: [
            {
                group: "Features",
                types: [
                    "feature",
                    "enhancement",
                ]
            },
            {
                group: "Fixes",
                types: [
                    "defect",
                ]
            },
        ]
    })
    await changeLogPage.launchExport()
    await changeLogPage.checkExportedContent(
        trimIndent(
            `
                ## Features
                
                * [ISS-21](mock://jira/ISS/ISS-21) Some new feature
                * [ISS-23](mock://jira/ISS/ISS-23) Some nicer UI
                
                ## Fixes
                
                * [ISS-22](mock://jira/ISS/ISS-22) Some fixes are needed
            `
        )
    )
})
