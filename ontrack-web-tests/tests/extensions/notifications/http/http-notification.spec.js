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
    await expect(page.getByText(`https://${configName}.example.com/api/deployments?project=\${project}`, {exact: true})).toBeVisible()
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
        intervalSeconds: 10,
        timeoutSeconds: 300,
    })
})

test('creating an HTTP call subscription waiting for a condition', async ({page, ontrack}) => {
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
    await pickOption(page, dialog.getByRole("combobox", {name: field("HTTP configuration")}), configName)

    // The status URL is only offered for POST and PUT
    await expect(dialog.getByLabel("Status URL", {exact: true})).not.toBeVisible()
    await pickOption(page, dialog.getByRole("combobox", {name: field("Method")}), "POST")
    await expect(dialog.getByLabel("Status URL", {exact: true})).toBeVisible()
    await dialog.getByLabel("Path", {exact: true}).fill("/deployments")

    // An invalid condition is refused, naming the expression
    const success = dialog.getByLabel("Success condition", {exact: true})
    await success.fill("response.body.status ==")
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog.getByText("Success condition `response.body.status ==` is not valid", {exact: false})).toBeVisible()

    await success.fill("response.body.status == 'SUCCEEDED'")
    await dialog.getByLabel("Failure condition", {exact: true}).fill("response.body.status == 'FAILED'")
    await dialog.getByLabel("Status URL", {exact: true}).fill("response.headers['location']")
    await dialog.getByLabel(field("Interval \\(s\\)")).fill("15")
    await dialog.getByLabel(field("Timeout \\(s\\)")).fill("600")
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    // The conditions are displayed (the closed dialog keeps the same examples in its help texts)
    const displayed = (text) => page.getByRole("cell").getByText(text, {exact: true})
    await expect(displayed("response.body.status == 'SUCCEEDED'")).toBeVisible()
    await expect(displayed("response.body.status == 'FAILED'")).toBeVisible()
    await expect(displayed("response.headers['location']")).toBeVisible()
    await expect(displayed("Every 15 s, for at most 600 s")).toBeVisible()

    const subscriptions = await getSubscriptions(ontrack, project)
    expect(subscriptions).toHaveLength(1)
    expect(subscriptions[0].channelConfig).toEqual({
        config: configName,
        method: "POST",
        path: "/deployments",
        headers: [],
        successCondition: "response.body.status == 'SUCCEEDED'",
        failureCondition: "response.body.status == 'FAILED'",
        statusUrl: "response.headers['location']",
        intervalSeconds: 15,
        timeoutSeconds: 600,
    })
})
