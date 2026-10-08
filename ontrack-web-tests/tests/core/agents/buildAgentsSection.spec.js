const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {BuildPage} = require("../builds/BuildPage");
const {registerAgent} = require("@ontrack/agents");
const {setAssistedChange} = require("@ontrack/extensions/scm/assistedChange");
const {createValidationRun} = require("@ontrack/validationRun");

/**
 * The Agents section of a build page (#2033): *Assisted by*, from git, and *Actions by agents*, from
 * the event log - two lists, never merged.
 */
test('a build assisted in git and validated by an agent shows both lists in its Agents section', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const agentStamp = await branch.createValidationStamp()
    const personStamp = await branch.createValidationStamp()
    const previous = await branch.createBuild()

    const agent = await registerAgent(ontrack, {displayName: "Claude", tool: "Claude Code"})
    const session = {id: `session-${agent.id}`, link: `https://claude.ai/code/session-${agent.id}`}
    const asAgent = agent.client(session)

    // The agent creates the build and validates it
    const agentBranch = await asAgent.getBranchById(branch.id)
    const build = await agentBranch.createBuild()
    await build.validate(agentStamp)
    // A person validates it too
    await createValidationRun({...build, ontrack}, personStamp)
    // Its commits are assisted
    await setAssistedChange(build, {
        assistants: ["Claude Code"],
        assistedCommits: 2,
        totalCommits: 3,
        sessionLinks: [session.link],
        previousBuildId: previous.id,
    })

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
    await expect(page.getByTestId('agents').getByText('Agents', {exact: true})).toBeVisible()

    // Assisted by, from git
    const assisted = page.getByTestId('build-agents-assisted')
    await expect(assisted.getByRole('heading', {name: 'Assisted by'})).toBeVisible()
    await expect(assisted.getByTestId('build-agents-assistants')).toHaveText('Claude Code')
    const commits = assisted.getByRole('link', {name: `2 of 3 commits since ${previous.name}`})
    await expect(commits).toHaveAttribute('href', `/extension/scm/changelog?from=${previous.id}&to=${build.id}`)
    await expect(assisted.getByRole('link', {name: 'Agent session'})).toHaveAttribute('href', session.link)
    await expect(assisted.getByTestId('build-agents-basis')).toHaveText('Set by the CI')

    // Actions by agents, from the events: the agent's validation, not the person's
    const actions = page.getByTestId('build-agents-actions')
    await expect(actions.getByRole('heading', {name: 'Actions by agents'})).toBeVisible()
    const validation = actions.locator('tr', {hasText: agentStamp.name})
    await expect(validation).toHaveCount(1)
    await expect(validation).toContainText(`Claude, owned by`)
    await expect(validation.getByRole('link', {name: 'Agent session'})).toHaveAttribute('href', session.link)
    await expect(actions.locator('tr', {hasText: personStamp.name})).toHaveCount(0)
})

test('a build of persons only has no Agents section', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const stamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    await build.validate(stamp)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    await expect(page.getByTestId('agents')).toHaveCount(0)
})
