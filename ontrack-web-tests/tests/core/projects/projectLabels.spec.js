const {expect} = require("@playwright/test");
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {ProjectPage} = require("./project");

test('assigning labels to a project', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const team = await ontrack.labels().createLabel({category: generate("team-"), name: generate("lbl-")})
    const language = await ontrack.labels().createLabel({category: generate("lang-"), name: generate("lbl-")})

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    // No label on the project to start with
    await projectPage.expectNoLabelChip(team)
    await projectPage.expectNoLabelChip(language)

    // Assigning both labels
    await projectPage.setLabels({check: [team, language]})

    // The chips are displayed in the title row
    await projectPage.expectLabelChip(team)
    await projectPage.expectLabelChip(language)

    // ... and the assignment has been saved
    const saved = await ontrack.labels().getProjectLabels(project.id)
    expect(saved.map(it => Number(it.id)).sort()).toEqual([Number(team.id), Number(language.id)].sort())
})

test('the assignment dialog filters the labels', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const team = await ontrack.labels().createLabel({category: generate("team-"), name: generate("lbl-")})
    const language = await ontrack.labels().createLabel({category: generate("lang-"), name: generate("lbl-")})

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    await projectPage.openLabelsDialog()
    await projectPage.filterLabels(team.category)

    await expect(projectPage.labelCheckbox(team)).toBeVisible()
    await expect(projectPage.labelCheckbox(language)).not.toBeVisible()
})

test('unassigning a label from a project', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const kept = await ontrack.labels().createLabel({category: generate("kept-"), name: generate("lbl-")})
    const removed = await ontrack.labels().createLabel({category: generate("gone-"), name: generate("lbl-")})
    await ontrack.labels().setProjectLabels(project.id, [kept.id, removed.id])

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    // Both chips are displayed in the title row
    await projectPage.expectLabelChip(kept)
    await projectPage.expectLabelChip(removed)

    // Unassigning one of them
    await projectPage.setLabels({uncheck: [removed]})

    // Only the other chip is left
    await projectPage.expectNoLabelChip(removed)
    await projectPage.expectLabelChip(kept)

    // ... and the change has been saved
    const saved = await ontrack.labels().getProjectLabels(project.id)
    expect(saved.map(it => Number(it.id))).toEqual([Number(kept.id)])
})

test('labels command hidden for a user without the labels authorization', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const label = await ontrack.labels().createLabel({category: generate("cat-"), name: generate("lbl-")})
    await ontrack.labels().setProjectLabels(project.id, [label.id])

    // The demo user gets the READ_ONLY role on this project only: it may see the project
    // and its labels, but holds no ProjectLabelManagement
    const groupName = generate("grp-")
    await ontrack.admin().createGroup({name: groupName, description: ""})
    await ontrack.admin().setGroupProjectRole(groupName, project.id, "READ_ONLY")
    await ontrack.admin().mapGroup("/ReadOnly", groupName)

    await login(page, ontrack, "demo@ontrack.local", "demo")

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    // The chips are still displayed...
    await projectPage.expectLabelChip(label)
    // ... but there is no way to change them
    await expect(projectPage.labelsCommand()).not.toBeVisible()
})
