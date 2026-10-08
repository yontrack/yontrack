const {login} = require("../login");
const {BranchPage} = require("./branch");
const {test} = require("../../fixtures/connection");
const {registerAgent} = require("@ontrack/agents");
const {setAssistedChange} = require("@ontrack/extensions/scm/assistedChange");

/**
 * The agent criteria of the standard build filter (#2036): whether the build was assisted, and
 * which actor created it.
 */
test('filtering builds on whether they were assisted', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    // An assisted build
    const assisted = await branch.createBuild("assisted-build")
    await setAssistedChange(assisted, {assistants: ["Claude Code"], assistedCommits: 1, totalCommits: 2})
    // A build without any assistant
    const notAssisted = await branch.createBuild("not-assisted-build")
    await setAssistedChange(notAssisted, {assistants: [], assistedCommits: 0, totalCommits: 2})
    // A build whose assisted change is unknown
    await branch.createBuild("unknown-build")

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()
    await branchPage.checkBuildPresent("not-assisted-build")

    const dialog = await branchPage.newStandardBuildFilter()
    await dialog.selectTab("Agents")
    await dialog.selectAssisted("Assisted")
    await dialog.ok()

    await branchPage.checkBuildPresent("assisted-build")
    await branchPage.checkBuildNotPresent("not-assisted-build")
    await branchPage.checkBuildNotPresent("unknown-build")
})

test('filtering builds on the agents which created them', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()

    // A build created by a person
    await branch.createBuild("person-build")
    // A build created by an agent
    const agent = await registerAgent(ontrack, {displayName: "Claude", tool: "Claude Code"})
    const agentBranch = await agent.client().getBranchById(branch.id)
    await agentBranch.createBuild("agent-build")

    await login(page, ontrack)

    const branchPage = new BranchPage(page, branch)
    await branchPage.goTo()
    await branchPage.checkBuildPresent("person-build")

    const dialog = await branchPage.newStandardBuildFilter()
    await dialog.selectTab("Agents")
    await dialog.selectActor("Agents")
    await dialog.ok()

    await branchPage.checkBuildPresent("agent-build")
    await branchPage.checkBuildNotPresent("person-build")
})
