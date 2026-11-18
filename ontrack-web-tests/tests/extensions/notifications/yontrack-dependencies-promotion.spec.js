const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../../core/login");
const {ProjectPage} = require("../../core/projects/project");
const {SubscriptionsPage} = require("./subscriptions");
const {graphQLCall} = require("@ontrack/graphql");
const {gql} = require("graphql-request");

// Required fields are announced with a leading "*"
const field = (label) => new RegExp(`^\\*?\\s*${label}$`)

const pickOption = async (page, combobox, text) => {
    // Forced click: the AntD Select displays its current value in a span
    // which intercepts the pointer events aimed at the combobox itself.
    await combobox.click({force: true})
    const listId = await combobox.getAttribute('aria-controls')
    expect(listId, `No list associated with the select`).toBeTruthy()
    const dropdown = page.locator(`.ant-select-dropdown:has(#${listId})`)
    await dropdown.waitFor({state: 'visible'})
    const option = dropdown
        .locator('.ant-select-item-option')
        .filter({hasText: new RegExp(`^${text}$`)})
    // The list is virtual: an option is only rendered once the keyboard has moved down to it
    await expect(async () => {
        if (await option.count() === 0) {
            await page.keyboard.press('ArrowDown')
        }
        await expect(option).toHaveCount(1, {timeout: 50})
    }).toPass({intervals: [50], timeout: 15_000})
    await option.dispatchEvent('click')
}

const getChannelConfig = async (ontrack, project) => {
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
                        channel
                        channelConfig
                    }
                }
            }
        `,
        {id: Number(project.id)}
    )
    expect(data.eventSubscriptions.pageItems).toHaveLength(1)
    expect(data.eventSubscriptions.pageItems[0].channel).toBe("yontrack-dependencies-promotion")
    return data.eventSubscriptions.pageItems[0].channelConfig
}

const openForm = async (page, ontrack, project) => {
    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()
    await new SubscriptionsPage(page).selectToolsSubscriptions()

    await page.getByRole("button", {name: "Create subscription"}).click()
    const dialog = page.getByRole("dialog")
    await dialog.getByLabel("Name", {exact: true}).fill("dependencies")
    const events = dialog.getByRole("combobox", {name: field("Events")})
    await events.fill("new_promotion_run")
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
        .filter({hasText: "new_promotion_run"})
        .first()
        .click()
    await page.keyboard.press('Escape')
    await pickOption(page, dialog.getByRole("combobox", {name: field("Channel")}), "yontrack-dependencies-promotion")
    return dialog
}

test('creating a dependencies promotion subscription from its form', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const dialog = await openForm(page, ontrack, project)

    await dialog.getByLabel("Project", {exact: true}).fill("B|C")
    await dialog.getByLabel("Qualifier", {exact: true}).fill("module.*")
    await dialog.getByLabel("Promotion", {exact: true}).fill("GOLD")
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    const config = await getChannelConfig(ontrack, project)
    expect(config.project).toBe("B|C")
    expect(config.qualifier).toBe("module.*")
    expect(config.promotion).toBe("GOLD")
})

test('clearing the qualifier of a dependencies promotion removes the filter', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const dialog = await openForm(page, ontrack, project)

    const qualifier = dialog.getByLabel("Qualifier", {exact: true})
    await qualifier.fill("module.*")
    await qualifier.fill("")
    await dialog.getByLabel("Promotion", {exact: true}).fill("GOLD")
    await dialog.getByRole("button", {name: "OK"}).click()
    await expect(dialog).not.toBeVisible()

    const config = await getChannelConfig(ontrack, project)
    expect(config.qualifier ?? null).toBeNull()
})
