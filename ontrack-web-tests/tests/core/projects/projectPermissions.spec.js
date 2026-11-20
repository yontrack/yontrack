const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");
const {ProjectPage} = require("./project");

/**
 * The permissions page of a project (#2164): reached from the Permissions command of the project
 * page, it grants several roles at once to a group, and removes them one by one.
 *
 * The project and the group are created for the test, so the change stays out of the way of the
 * other tests sharing the instance.
 */
test('granting two roles to a group on the permissions page of a project, then removing one', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const group = await ontrack.admin().createGroup({name: generate("g-"), description: "Release engineers"})

    await login(page, ontrack)
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    // Going to the permissions page from the project page
    await page.getByTestId('project-permissions-command').click()
    await expect(page).toHaveURL(`${ontrack.connection.ui}/project/${project.id}/permissions`)

    const key = `GROUP-${group.id}`
    const roles = page.getByTestId(`project-roles-${key}`)
    const participant = page.getByTestId(`project-role-${key}-PARTICIPANT`)
    const promoter = page.getByTestId(`project-role-${key}-PROMOTER`)
    await expect(page.getByRole('button', {name: 'Add permission'})).toBeVisible()
    await expect(roles).toHaveCount(0)

    // Granting two roles at once to the group
    await page.getByTestId('target').click()
    await page.keyboard.type(group.name)
    await page.locator('.ant-select-item-option').filter({hasText: group.name}).click()
    await page.getByTestId('roles').click()
    await page.locator('.ant-select-item-option').filter({hasText: 'Participant'}).click()
    await page.locator('.ant-select-item-option').filter({hasText: 'Promoter'}).click()
    await page.keyboard.press('Escape')
    await page.getByRole('button', {name: 'Add permission'}).click()

    await expect(participant).toBeVisible()
    await expect(promoter).toBeVisible()
    await expect(roles).toContainText('Participant')
    await expect(roles).toContainText('Promoter')

    // Removing one role keeps the other
    await page.getByRole('button', {name: `Remove the Participant role from ${group.name}`}).click()
    await page.getByRole('button', {name: 'OK', exact: true}).click()

    await expect(participant).toHaveCount(0)
    await expect(promoter).toBeVisible()
})

test('permissions command hidden for a user who may not manage the permissions of the project', async ({page, ontrack}) => {
    const project = await ontrack.createProject()

    // The demo user gets the READ_ONLY role on this project only: it may see the project,
    // but holds no ProjectAuthorisationMgt
    const groupName = generate("grp-")
    const group = await ontrack.admin().createGroup({name: groupName, description: ""})
    await ontrack.admin().grantProjectRoleToGroup(group, project.id, "READ_ONLY")
    await ontrack.admin().mapGroup("/ReadOnly", groupName)

    await login(page, ontrack, "demo@ontrack.local", "demo")

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    // The commands are there...
    await expect(page.getByRole('link', {name: 'Search builds'})).toBeVisible()
    // ... but not the one leading to the permissions
    await expect(page.getByTestId('project-permissions-command')).not.toBeVisible()
})

test('a project owner manages the permissions of the project', async ({page, ontrack}) => {
    const project = await ontrack.createProject()

    // The demo user owns this project only: it manages its permissions without managing the
    // accounts and the groups globally
    const ownersName = generate("grp-")
    const owners = await ontrack.admin().createGroup({name: ownersName, description: ""})
    await ontrack.admin().grantProjectRoleToGroup(owners, project.id, "OWNER")
    await ontrack.admin().mapGroup("/ReadOnly", ownersName)
    const group = await ontrack.admin().createGroup({name: generate("g-"), description: "Release engineers"})

    await login(page, ontrack, "demo@ontrack.local", "demo")
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    await page.getByTestId('project-permissions-command').click()

    // The owners are listed...
    await expect(page.getByTestId(`project-role-GROUP-${owners.id}-OWNER`)).toBeVisible()

    // ... and the owner grants a role to another group
    await page.getByTestId('target').click()
    await page.keyboard.type(group.name)
    await page.locator('.ant-select-item-option').filter({hasText: group.name}).click()
    await page.getByTestId('roles').click()
    await page.locator('.ant-select-item-option').filter({hasText: 'Participant'}).click()
    await page.keyboard.press('Escape')
    await page.getByRole('button', {name: 'Add permission'}).click()

    await expect(page.getByTestId(`project-role-GROUP-${group.id}-PARTICIPANT`)).toBeVisible()
})
