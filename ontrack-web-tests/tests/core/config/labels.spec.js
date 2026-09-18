const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {LabelsPage, labelDisplay} = require("./labels");
const {openUserMenu, selectUserMenuItem} = require("../userMenu");

test('label creation from the user menu', async ({page, ontrack}) => {
    await login(page, ontrack)

    // The admin holds LabelManagement, so the menu item is there and leads to the page
    await selectUserMenuItem(page, "Configurations", "Labels")

    const labelsPage = new LabelsPage(page, ontrack)
    await expect(page.getByTestId("labels")).toBeVisible()

    const label = {category: generate("cat-"), name: generate("lbl-")}
    await labelsPage.createLabel({...label, description: "Some label"})

    // The new label is displayed as a chip
    await labelsPage.expectLabel(label)

    // ... and has been saved
    const [saved] = await ontrack.labels().findLabels(label)
    expect(saved).toBeDefined()
    expect(saved.description).toBe("Some label")
    // The picker was never opened, so the label carries LabelDialog's defaultColor - the
    // brand gray. See issue #1814.
    expect(saved.color.toLowerCase()).toBe("#e6e1e9")
})

test('label edition', async ({page, ontrack}) => {
    const label = await ontrack.labels().createLabel({description: "Initial description"})

    await login(page, ontrack)
    const labelsPage = new LabelsPage(page, ontrack)
    await labelsPage.goTo()

    // Filtering on the label, to make sure it is the one being edited
    await labelsPage.filter(label.category)
    await labelsPage.expectLabel(label)

    const newName = generate("lbl-")
    await labelsPage.editLabel(label, {name: newName, description: "New description"})

    // The chip now carries the new name
    const renamed = {category: label.category, name: newName}
    await labelsPage.expectLabel(renamed)
    await labelsPage.expectNoLabel(label)

    // ... and the change has been saved
    const [saved] = await ontrack.labels().findLabels(renamed)
    expect(saved).toBeDefined()
    expect(saved.description).toBe("New description")
})

test('label deletion states how many projects carry the label', async ({page, ontrack}) => {
    const label = await ontrack.labels().createLabel()
    const project = await ontrack.createProject()
    await ontrack.labels().setProjectLabels(project.id, [label.id])

    await login(page, ontrack)
    const labelsPage = new LabelsPage(page, ontrack)
    await labelsPage.goTo()
    await labelsPage.filter(label.category)

    // The number of projects carrying the label is displayed
    expect(await labelsPage.projectCount(label)).toBe("1")

    await labelsPage.deleteLabel(label, {
        confirmationText: "1 project carries this label and will lose it.",
    })

    // The label is gone, from the page and from the API
    await labelsPage.expectNoLabel(label)
    const found = await ontrack.labels().findLabels(label)
    expect(found.length).toBe(0)
})

test('labels menu item hidden for a user without label management', async ({page, ontrack}) => {
    // The demo user holds no global role, and therefore no LabelManagement
    await login(page, ontrack, "demo@ontrack.local", "demo")

    const drawer = await openUserMenu(page)
    // The menu is loaded...
    await expect(drawer.getByRole('menuitem', {name: "User information", exact: true})).toBeVisible()
    // ... and has no Configurations group at all, which is where the labels would be
    const configurations = drawer.getByRole('menuitem', {name: "Configurations", exact: true})
    await expect(configurations).not.toBeVisible()
    await expect(drawer.getByText("Labels", {exact: true})).not.toBeVisible()
})

test('label chip displays the name alone when the label has no category', async ({page, ontrack}) => {
    const label = await ontrack.labels().createLabel({category: null})

    await login(page, ontrack)
    const labelsPage = new LabelsPage(page, ontrack)
    await labelsPage.goTo()
    await labelsPage.filter(label.name)

    expect(labelDisplay(label)).toBe(label.name)
    await labelsPage.expectLabel(label)
})
