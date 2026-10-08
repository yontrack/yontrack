const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {BuildPage} = require("../builds/BuildPage");
const {provisionAgentBuild} = require("./agentBadges");

/**
 * The badges of agents (#2032): a build created, validated and promoted by an agent, with an
 * assisted change, tells an agent from a person wherever its actor shows.
 */
test('a build created and validated by an agent shows the assisted and the agent badges on its page', async ({page, ontrack}) => {
    const {agent, build, run, owner, session} = await provisionAgentBuild(ontrack)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // The assisted badge, its counts and assistants in words for a screen reader
    const assisted = page.getByTestId('build-assisted')
    await expect(assisted).toBeVisible()
    await expect(assisted).toHaveText('Assisted')
    await expect(page.getByRole('img', {name: 'Assisted: 2 of 3 commits, by Claude Code'})).toBeVisible()

    // The agent which created the build, and its owner, linking to the session
    const actor = page.getByTestId('build-actor')
    await expect(actor).toBeVisible()
    await expect(actor).toContainText(`by ${agent.displayName}, owned by ${owner}`)
    const sessionLink = page.getByRole('link', {name: `by agent ${agent.displayName}, owned by ${owner}`})
    await expect(sessionLink).toHaveAttribute('href', session.link)
    await expect(sessionLink).toHaveAttribute('target', '_blank')

    // The row of the build on its branch page
    await page.goto(`${ontrack.connection.ui}/branch/${build.branch.id}`)
    await expect(page.getByTestId(`build-assisted-${build.id}`)).toBeVisible()
    await expect(page.getByTestId(`build-actor-${build.id}`)).toContainText(`by ${agent.displayName}, owned by ${owner}`)

    // The validation run page, whose status was set by the agent
    await page.goto(`${ontrack.connection.ui}/validationRun/${run.id}`)
    const statusActor = page.locator('[data-testid^="validation-run-status-actor-"]')
    await expect(statusActor).toHaveCount(1)
    await expect(statusActor).toContainText(`${agent.displayName}, owned by ${owner}`)
})

test('a build created by a person has no badge on its page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    await expect(page.getByTestId('build-assisted')).toHaveCount(0)
    await expect(page.getByTestId('build-actor')).toHaveCount(0)
})

test('the administration of the accounts and of the agents says which accounts are agents', async ({page, ontrack}) => {
    const {agent} = await provisionAgentBuild(ontrack)

    await login(page, ontrack)

    await page.goto(`${ontrack.connection.ui}/core/admin/account-management`)
    // Agents are hidden from the accounts by default; the agent is looked for among many accounts
    await page.getByTestId('show-agents').click()
    await page.getByPlaceholder('Name, email, group...').fill(agent.email)
    await page.getByPlaceholder('Name, email, group...').press('Enter')
    await expect(page.getByTestId(`account-kind-${agent.id}`)).toHaveText('Agent')

    await page.goto(`${ontrack.connection.ui}/core/admin/agents`)
    await expect(page.getByTestId(`agent-kind-${agent.id}`)).toHaveText('Agent')
})
