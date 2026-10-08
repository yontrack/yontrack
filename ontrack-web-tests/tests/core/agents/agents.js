import {expect} from "@playwright/test";
import {selectUserMenuItem} from "../userMenu";

/**
 * The "My agents" page of the current user.
 */
export class MyAgentsPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goTo() {
        await this.page.goto(`${this.ontrack.connection.ui}/core/admin/my-agents`)
        await this.checkLoaded()
    }

    async goToThroughUserMenu() {
        await selectUserMenuItem(this.page, "User information", "My agents")
        await this.checkLoaded()
    }

    async checkLoaded() {
        await expect(this.page.getByText("My agents", {exact: true}).first()).toBeVisible()
    }

    /**
     * Registers an agent and lands on its page.
     */
    async registerAgent({slug, displayName, tool}) {
        await this.page.getByRole("button", {name: "Register an agent"}).click()
        const dialog = this.page.getByTestId("register-agent")
        await expect(dialog).toBeVisible()
        await dialog.getByLabel("Slug").fill(slug)
        await dialog.getByLabel("Display name").fill(displayName)
        await dialog.getByLabel("Tool").fill(tool)
        await this.page.getByRole("button", {name: "Register", exact: true}).click()
        const agentPage = new AgentPage(this.page, this.ontrack)
        await agentPage.checkAgent({slug})
        return agentPage
    }
}

/**
 * The page of one agent.
 */
export class AgentPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    /**
     * Goes to the page of an agent, given by its ID.
     */
    async goTo(id) {
        await this.page.goto(`${this.ontrack.connection.ui}/core/admin/agents/${id}`)
        await expect(this.page.getByTestId("agent-tabs")).toBeVisible()
    }

    async checkAgent({slug}) {
        await expect(this.page.getByText(`${slug}[agent]`, {exact: true})).toBeVisible()
    }

    /**
     * Opens the Activity tab and returns it.
     */
    async openActivity() {
        await this.page.getByRole("tab", {name: "Activity"}).click()
        const activity = this.page.getByTestId("agent-activity")
        await expect(activity).toBeVisible()
        return activity
    }

    /**
     * Generates a token and returns its value, shown once.
     */
    async generateToken(name) {
        await this.page.getByLabel("Token name").fill(name)
        await this.page.getByRole("button", {name: "Generate token"}).click()
        const value = this.page.getByTestId("generatedAgentToken")
        await expect(value).toBeVisible()
        await expect(this.page.getByTestId("agent-tokens").getByText(name, {exact: true})).toBeVisible()
        return value.textContent()
    }
}

/**
 * The administration page of every agent.
 */
export class AgentsAdminPage {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
    }

    async goToThroughUserMenu() {
        await selectUserMenuItem(this.page, "System", "Agents")
        await expect(this.page.getByTestId("agents")).toBeVisible()
    }

    /**
     * Checks that an agent is listed, with its number of tokens.
     */
    async checkAgent({displayName, slug, tokens}) {
        const row = this.page.getByTestId("agents").getByRole("row").filter({hasText: `${slug}[agent]`})
        await expect(row).toBeVisible()
        await expect(row.getByRole("link", {name: displayName, exact: true})).toBeVisible()
        if (tokens !== undefined) {
            await expect(row.getByRole("cell", {name: `${tokens}`, exact: true})).toBeVisible()
        }
    }
}
