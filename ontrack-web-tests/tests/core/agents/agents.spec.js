const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {graphQLCall} = require("@ontrack/graphql");
const {AgentsAdminPage, MyAgentsPage} = require("./agents");

test('Registering an agent from My agents, generating a token and seeing it in the agents administration', async ({page, ontrack}) => {
    await login(page, ontrack)

    // Registering an agent from My agents
    const myAgents = new MyAgentsPage(page, ontrack)
    await myAgents.goToThroughUserMenu()
    const slug = generate("ui-agent-")
    const displayName = `Agent ${slug}`
    const agentPage = await myAgents.registerAgent({slug, displayName, tool: "Claude Code"})

    // Generating a token, shown once
    const token = await agentPage.generateToken("ci")
    expect(token).toBeTruthy()

    // The token authenticates the agent
    const data = await graphQLCall(
        ontrack.withToken(token).connection,
        `{ user { account { email kind } } }`
    )
    expect(data.user.account.email).toBe(`${slug}[agent]`)
    expect(data.user.account.kind).toBe("AGENT")

    // The agent is listed in the agents administration, with its token
    const admin = new AgentsAdminPage(page, ontrack)
    await admin.goToThroughUserMenu()
    await admin.checkAgent({displayName, slug, tokens: 1})
})
