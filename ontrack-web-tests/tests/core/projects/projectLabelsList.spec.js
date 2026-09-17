const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {AllProjectsWidget} = require("../home/allProjectsWidget");
const {ProjectLabelPage} = require("./projectLabelPage");
const {labelDisplay} = require("../../support/labels");

test('label chips in the project list, leading to the label page', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const label = await ontrack.labels().createLabel({category: generate("team-"), name: generate("lbl-")})
    await ontrack.labels().setProjectLabels(project.id, [label.id])

    const widget = new AllProjectsWidget(page, ontrack)
    await widget.createDashboard()

    await login(page, ontrack)
    await widget.select()

    // Filtering on the name, so that the project is on the page whatever the instance contains
    await widget.filterByName(project.name)
    await widget.expectProject(project)

    // The label is displayed as a chip beside the project...
    await expect(widget.chip(label)).toBeVisible()

    // ... and it leads to the label page
    await widget.chip(label).click()
    const labelPage = new ProjectLabelPage(page, ontrack, label)
    await labelPage.expectOnPage()
    await labelPage.expectProject(project)
})

test('filtering the project list by one and by two labels', async ({page, ontrack}) => {
    const team = await ontrack.labels().createLabel({category: generate("team-"), name: generate("lbl-")})
    const language = await ontrack.labels().createLabel({category: generate("lang-"), name: generate("lbl-")})

    const teamOnly = await ontrack.createProject()
    await ontrack.labels().setProjectLabels(teamOnly.id, [team.id])
    const both = await ontrack.createProject()
    await ontrack.labels().setProjectLabels(both.id, [team.id, language.id])
    const noLabel = await ontrack.createProject()

    const widget = new AllProjectsWidget(page, ontrack)
    await widget.createDashboard()

    await login(page, ontrack)
    await widget.select()

    // One label: the two projects carrying it, and no other
    await widget.filterByLabel(team)
    await widget.expectProject(teamOnly)
    await widget.expectProject(both)
    await widget.expectNoProject(noLabel)

    // Two labels: only the project carrying both
    await widget.filterByLabel(language)
    await widget.expectProject(both)
    await widget.expectNoProject(teamOnly)
    await widget.expectNoProject(noLabel)
})

test('the label page lists the projects carrying the label', async ({page, ontrack}) => {
    const label = await ontrack.labels().createLabel({
        category: generate("team-"),
        name: generate("lbl-"),
        description: "Projects of the platform team",
    })
    const first = await ontrack.createProject()
    const second = await ontrack.createProject()
    const other = await ontrack.createProject()
    await ontrack.labels().setProjectLabels(first.id, [label.id])
    await ontrack.labels().setProjectLabels(second.id, [label.id])

    await login(page, ontrack)

    const labelPage = new ProjectLabelPage(page, ontrack, label)
    await labelPage.goTo()

    // The description is displayed beside the chip
    await expect(page.getByText("Projects of the platform team")).toBeVisible()

    // Only the projects carrying the label
    await labelPage.expectProject(first)
    await labelPage.expectProject(second)
    await labelPage.expectNoProject(other)

    // The chip of the label is displayed for each of them, and links back to this page
    await expect(page.getByTestId(`label-${labelDisplay(label)}`)).toHaveCount(3)
})
