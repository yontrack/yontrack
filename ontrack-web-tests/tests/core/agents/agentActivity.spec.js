const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {registerAgent} = require("@ontrack/agents");
const {AgentPage} = require("./agents");

/**
 * The Activity tab of an agent's page (#2034): what the agent did over the last days, for its owner.
 */
test('the owner opens the activity of their agent and sees the build it validated', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const stamp = await branch.createValidationStamp()

    // The current user registers the agent: they are its owner
    const agent = await registerAgent(ontrack, {displayName: "Claude", tool: "Claude Code"})
    const session = {id: `session-${agent.id}`, link: `https://claude.ai/code/session-${agent.id}`}
    const asAgent = agent.client(session)

    // The agent creates a build and validates it
    const agentBranch = await asAgent.getBranchById(branch.id)
    const build = await agentBranch.createBuild()
    await build.validate(stamp)

    await login(page, ontrack)
    const agentPage = new AgentPage(page, ontrack)
    await agentPage.goTo(agent.id)
    const activity = await agentPage.openActivity()

    // The last 7 days by default
    await expect(activity.getByTestId('agent-activity-window').getByText('7 days')).toBeVisible()

    // The validation of the build, with its project and its session
    const row = activity.locator('tr', {hasText: stamp.name})
    await expect(row).toHaveCount(1)
    await expect(row).toContainText(build.name)
    // Time, action, project, session
    await expect(row.getByRole('cell').nth(2).getByRole('link', {name: project.name, exact: true})).toBeVisible()
    await expect(row.getByRole('link', {name: 'Agent session'})).toHaveAttribute('href', session.link)
})
