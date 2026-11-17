const {expect} = require("@playwright/test");
const {test} = require("../../../fixtures/connection");
const {login} = require("../../../core/login");
const {generate} = require("@ontrack/utils");
const {ProjectPage} = require("../../../core/projects/project");
const {SubscriptionsPage} = require("../subscriptions");
const {graphQLCall} = require("@ontrack/graphql");
const {gql} = require("graphql-request");

/**
 * Searching an option of a searchable AntD Select, and picking it.
 */
const searchOption = async (page, combobox, search) => {
    await combobox.fill(search)
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
        .filter({hasText: search})
        .first()
        .click()
}

/**
 * Picking an option of a non-searchable AntD Select by its exact text.
 */
const pickOption = async (page, combobox, text) => {
    // Forced click: the AntD Select displays its current value in a span
    // which intercepts the pointer events aimed at the combobox itself.
    await combobox.click({force: true})
    const listId = await combobox.getAttribute('aria-controls')
    expect(listId, `No list associated with the select`).toBeTruthy()
    const dropdown = page.locator(`.ant-select-dropdown:has(#${listId})`)
    await dropdown.waitFor({state: 'visible'})
    await dropdown
        .locator('.ant-select-item-option')
        .filter({hasText: new RegExp(`^${text}$`)})
        .dispatchEvent('click')
}

// Required fields are announced with a leading "*"
const field = (label) => new RegExp(`^\\*?\\s*${label}$`)

const getSubscriptions = async (ontrack, project) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query Subscriptions($id: Int!) {
                eventSubscriptions(
                    offset: 0,
                    size: 10,
                    filter: {entity: {type: PROJECT, id: $id}},
                ) {
                    pageItems {
                        name
                        channel
                        channelConfig
                    }
                }
            }
        `,
        {id: Number(project.id)}
    )
    return data.eventSubscriptions.pageItems
}

test('creating an HTTP call subscription from its form', async ({page, ontrack}) => {
    const configName = generate("http-")
    await ontrack.configurations.http.createConfig({
        name: configName,
        url: `https://${configName}.example.com/api`,
    })
    const project = await ontrack.createProject()

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()
    const subscriptionsPage = new SubscriptionsPage(page)
    await subscriptionsPage.selectToolsSubscriptions()

    await page.getByRole("button", {name: "Create subscription"}).click()
    const dialog = page.getByRole("dialog")
    await dialog.getByLabel("Name", {exact: true}).fill("deployment")
    await searchOption(page, dialog.getByRole("combobox", {name: field("Events")}), "new_branch")
    await page.keyboard.press('Escape')
    await pickOption(page, dialog.getByRole("combobox", {name: field("Channel")}), "http")

    // The body is only offered for POST and PUT
    await pickOption(page, dialog.getByRole("combobox", {name: field("HTTP configuration")}), configName)
    await expect(dialog.getByLabel("Body", {exact: true})).not.toBeVisible()
    await pickOption(page, dialog.getByRole("combobox", {name: field("Method")}), "POST")
    await expect(dialog.getByLabel("Body", {exact: true})).toBeVisible()

    await dialog.getByLabel("Path", {exact: true}).fill("/deployments?project=${project}")
    await dialog.getByRole("button", {name: "Add header"}).click()
    await dialog.getByLabel("Header name").fill("X-Request-Id")
    await dialog.getByLabel("Header value").fill("${branch}")

    // Invalid JSON is refused
    const body = dialog.getByLabel("Body", {exact: true})
    await body.fill('{"version": ')
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog.getByText("The body must be valid JSON.")).toBeVisible()

    await body.fill('{"branch": "${branch}", "replicas": 3, "dryRun": false}')
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    // The subscription is displayed
    await expect(page.getByText("/deployments?project=${project}")).toBeVisible()
    await expect(page.getByText(configName, {exact: true})).toBeVisible()
    await expect(page.getByText("X-Request-Id")).toBeVisible()

    // The body is stored as JSON, not as text
    const subscriptions = await getSubscriptions(ontrack, project)
    expect(subscriptions).toHaveLength(1)
    expect(subscriptions[0].channel).toBe("http")
    expect(subscriptions[0].channelConfig).toEqual({
        config: configName,
        method: "POST",
        path: "/deployments?project=${project}",
        headers: [{name: "X-Request-Id", value: "${branch}"}],
        body: {branch: "${branch}", replicas: 3, dryRun: false},
    })
})
