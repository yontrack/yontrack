const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {registerAgent} = require("@ontrack/agents");
const {selectUserMenuItem} = require("../userMenu");

const importDashboard = async (page, yaml) => {
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText('Import dashboards as YAML').click()
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()
    await modal.locator('textarea').fill(yaml)
    await modal.getByRole('button', {name: 'Import'}).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
}

/**
 * The agent activity widget and the latest agent actions page (#2035).
 */
test('the agent activity widget counts what an agent did, and its tiles open the latest agent actions', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const stamp = await branch.createValidationStamp()

    // An agent creates a build and validates it
    const agent = await registerAgent(ontrack, {displayName: "Claude", tool: "Claude Code"})
    const session = {id: `session-${agent.id}`, link: `https://claude.ai/code/session-${agent.id}`}
    const asAgent = agent.client(session)
    const agentBranch = await asAgent.getBranchById(branch.id)
    const build = await agentBranch.createBuild()
    await build.validate(stamp)

    // A dashboard with the widget, narrowed to the project
    const dashboardName = generate('agents-dashboard-')
    const yaml = [
        `- name: "${dashboardName}"`,
        `  widgets:`,
        `    - key: "extension/agents/AgentActivity"`,
        `      layout: {x: 0, y: 0, w: 12, h: 12}`,
        `      config:`,
        `        window: 7`,
        `        projects:`,
        `          - "${project.name}"`,
    ].join('\n')

    await login(page, ontrack)
    await importDashboard(page, yaml)
    await page.getByRole('button', {name: 'Dashboard', exact: true}).click()
    await page.getByText(dashboardName).click()

    // The build by the agent is counted, nothing else
    await expect(page.getByTestId('agent-activity-builds-value')).toHaveText('1')
    await expect(page.getByTestId('agent-activity-promotions-value')).toHaveText('0')
    await expect(page.getByTestId('agent-activity-deployments-value')).toHaveText('0')
    await expect(page.getByTestId('agent-activity-assisted')).toBeVisible()

    // The tile opens the latest agent actions, filtered on the builds of the project
    await page.getByTestId('agent-activity-builds-value').click()
    await expect(page).toHaveURL(/\/extension\/agents\/actions\?/)
    await expect(page).toHaveURL(/window=7/)
    await expect(page).toHaveURL(/eventTypes=new_build/)
    const table = page.getByTestId('agent-actions')
    await expect(table).toBeVisible()
    const row = table.locator('tr', {hasText: build.name})
    await expect(row).toHaveCount(1)
    // The badge of the agent, the project and the session
    await expect(row.getByText('Claude, owned by')).toBeVisible()
    // Time, agent, action, project, session
    await expect(row.getByRole('cell').nth(3).getByRole('link', {name: project.name, exact: true})).toBeVisible()
    await expect(row.getByRole('link', {name: 'Agent session'})).toHaveAttribute('href', session.link)
    // Only the creation of the build, not its validation
    await expect(table.locator('tr', {hasText: stamp.name})).toHaveCount(0)
})

test('the latest agent actions are in the information menu', async ({page, ontrack}) => {
    await login(page, ontrack)
    await selectUserMenuItem(page, "Information", "Latest agent actions")
    await expect(page.getByTestId('agent-actions')).toBeVisible()
    await expect(page.getByTestId('agent-actions-window').getByText('7 days')).toBeVisible()
})
