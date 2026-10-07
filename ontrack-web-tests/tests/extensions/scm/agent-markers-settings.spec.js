const {expect} = require('@playwright/test')
const {test} = require("../../fixtures/connection")
const {login} = require("../../core/login");
const {SettingsPage} = require("../../core/settings/SettingsPage");

test('adding an agent marker pattern', async ({page, ontrack}) => {
    // Starting from the default settings
    await ontrack.settings.agentMarkers.saveSettings()
    try {
        await login(page, ontrack)
        const settingsPage = new SettingsPage(page, ontrack)
        await settingsPage.goTo()
        await settingsPage.selectSettings("Agent markers")

        // Built-in conventions are on by default
        await expect(page.getByRole("switch", {name: "Built-in conventions"})).toBeChecked()

        // Adding a pattern
        await page.getByRole("button", {name: "Add a pattern"}).click()
        await page.getByLabel("Assistant").fill("Acme Bot")
        await page.getByRole("combobox", {name: "Type"}).click()
        await page.getByTitle("Login").click()
        await page.getByLabel("Value").fill("acme-bot[bot]")

        const submit = page.getByRole("button", {name: "Submit"})
        await submit.click()
        await expect(submit).toBeEnabled()

        // The pattern has been saved
        await expect.poll(async () => (await ontrack.settings.agentMarkers.getSettings()).patterns).toEqual([
            {name: "Acme Bot", type: "LOGIN", value: "acme-bot[bot]"},
        ])
    } finally {
        await ontrack.settings.agentMarkers.saveSettings()
    }
})
