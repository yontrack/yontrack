// @ts-check
const {login} = require("../../core/login");
const {test} = require("../../fixtures/connection");
const {assistedCommits, provisionAssistedChangeLog, SCMChangeLogPage} = require("./scm");

test("assisted commits are marked in the change log and counted in its header", async ({page, ontrack}) => {
    const {from, to, mockSCMContext} = await provisionAssistedChangeLog(ontrack)

    await login(page, ontrack)
    const changeLogPage = new SCMChangeLogPage(page, ontrack)
    await changeLogPage.goToById({from, to})

    // One commit out of two carries a Co-Authored-By trailer of Claude Code
    await changeLogPage.checkAssistedCount("1 of 2 commits assisted")

    // The assisted commit has a marker, linking to its agent session
    await changeLogPage.checkCommitAssisted(
        mockSCMContext.commitIdsPerMessage[assistedCommits.assisted],
        {
            name: "Assisted by Claude Code",
            sessionLink: "https://claude.ai/code/session_ui_test",
        }
    )

    // The commit written by hand has none
    await changeLogPage.checkCommitAssisted(
        mockSCMContext.commitIdsPerMessage[assistedCommits.human],
        {name: null}
    )
})
