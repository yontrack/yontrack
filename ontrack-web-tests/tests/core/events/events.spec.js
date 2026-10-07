const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {openUserMenuGroup, selectUserMenuItem} = require("../userMenu");
const {EventsPage} = require("./events");

test('events page lists and filters the events of the instance', async ({page, ontrack}) => {
    // A project, two branches, one of which is deleted: a `new_branch` event for the branch which
    // is kept (the one of the deleted branch went with it) and a `delete_branch` event, whose
    // values hold the name and the ID of the deleted branch
    const project = await ontrack.createProject()
    const kept = await project.createBranch()
    const deleted = await project.createBranch()
    await deleted.delete()

    // Name of the user who posted the events, as the events know it
    const [lastEvent] = await ontrack.findEvents({project: project.name}, 1)
    const user = lastEvent.user

    await login(page, ontrack)

    // The admin holds EventsAudit, so the menu item is there and leads to the page
    await selectUserMenuItem(page, "Information", "Events")
    const eventsPage = new EventsPage(page, ontrack)
    await eventsPage.expectOnPage()

    // Filtering by event type: the newest branch creations are listed, with their type
    await eventsPage.selectEventTypes(["new_branch"])
    await eventsPage.filter()
    await expect(eventsPage.row(kept.name)).toBeVisible()
    await expect(eventsPage.row(kept.name).getByText("new_branch", {exact: true})).toBeVisible()
    await expect(eventsPage.rows().filter({hasText: "delete_branch"})).toHaveCount(0)

    // Filtering by user as well: a prefix of the user's name keeps the events...
    await eventsPage.setUser(user.substring(0, 3).toUpperCase())
    await eventsPage.filter()
    await expect(eventsPage.row(kept.name)).toBeVisible()
    // ... while an unknown user leaves none
    await eventsPage.setUser(generate("nobody-"))
    await eventsPage.filter()
    await expect(page.getByTestId("events").locator(".ant-empty-description")).toHaveText("No data")
    await expect(eventsPage.rows()).toHaveCount(0)

    // Filtering by project, the user back to the one who posted the events: the branch creation
    // of the project, and nothing else, linked to the project
    await eventsPage.setUser(user)
    await eventsPage.selectProject(project.name)
    await eventsPage.filter()
    await expect(eventsPage.rows()).toHaveCount(1)
    await expect(eventsPage.rows().nth(0)).toContainText(kept.name)
    const table = await eventsPage.table()
    const projectCell = await (await table.getRowByIndex(1)).getCell("Project")
    await expect(projectCell.getByRole("link", {name: project.name, exact: true}))
        .toHaveAttribute("href", `/project/${project.id}`)

    // The deletion of the branch, and its values in the expanded row
    await eventsPage.selectEventTypes(["delete_branch"])
    await eventsPage.filter()
    await expect(eventsPage.rows()).toHaveCount(1)
    await expect(eventsPage.rows().nth(0)).toContainText(deleted.name)
    const row = await table.getRowByIndex(1)
    const details = await row.expand()
    await expect(details.getByText("delete_branch", {exact: true})).toBeVisible()
    await expect(details.getByRole("link", {name: `Project ${project.name}`})).toBeVisible()
    const values = details.locator('[data-testid^="event-values-"]')
    await expect(values.getByRole("row", {name: `BRANCH ${deleted.name}`, exact: true})).toBeVisible()
    await expect(values.getByRole("row", {name: `BRANCH_ID ${deleted.id}`, exact: true})).toBeVisible()

    // Filtering by time as well: a window in the future leaves no event
    await eventsPage.setRange("2100-01-01 00:00", "2100-12-31 00:00")
    await eventsPage.filter()
    await expect(page.getByTestId("events").locator(".ant-empty-description")).toHaveText("No data")
})

test('events page downloads the filtered events as CSV', async ({page, ontrack}) => {
    // A project with two builds
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const first = await branch.createBuild()
    const second = await branch.createBuild()

    await login(page, ontrack)
    const eventsPage = new EventsPage(page, ontrack)
    await eventsPage.goTo()

    // Filtering on the builds of the project
    await eventsPage.selectEventTypes(["new_build"])
    await eventsPage.selectProject(project.name)
    await eventsPage.filter()
    await expect(eventsPage.rows()).toHaveCount(2)

    // The download holds the same events, newest first
    const [header, ...rows] = await eventsPage.downloadCsv()
    expect(header).toEqual([
        "id", "time", "user", "eventType", "message",
        "project", "branch", "build", "promotionLevel", "validationStamp", "promotionRun", "validationRun",
        "xProject", "xBranch", "xBuild", "xPromotionLevel", "xValidationStamp", "xPromotionRun", "xValidationRun",
        "ref", "values",
    ])
    const items = rows.map(row => Object.fromEntries(header.map((name, index) => [name, row[index]])))
    expect(items.map(item => [item.eventType, item.project, item.branch, item.build])).toEqual([
        ["new_build", project.name, branch.name, second.name],
        ["new_build", project.name, branch.name, first.name],
    ])
    // The values, as JSON
    items.forEach(item => expect(() => JSON.parse(item.values)).not.toThrow())
    // No warning, the export holding all the matching events
    await expect(page.getByTestId('events-export-truncated')).toHaveCount(0)
})

test('events menu item hidden for a user without the events audit', async ({page, ontrack}) => {
    // The demo user holds no global role, and therefore no EventsAudit
    await login(page, ontrack, "demo@ontrack.local", "demo")

    const drawer = await openUserMenuGroup(page, "Information")
    // The group is expanded...
    await expect(drawer.getByText("User information", {exact: true})).toBeVisible()
    // ... and has no Events item
    await expect(drawer.getByText("Events", {exact: true})).not.toBeVisible()
})
