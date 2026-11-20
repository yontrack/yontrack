const {expect} = require('@playwright/test');
const {test} = require("../../fixtures/connection");
const {login} = require("../login");
const {generate} = require("@ontrack/utils");

/**
 * Global roles add up (#2162): the global permissions page shows all the global roles of a group,
 * adding a role keeps the others, and each role is removed on its own.
 *
 * The group is created for the test and holds no role the signed-in user relies on, so the
 * change stays out of the way of the other tests sharing the instance.
 */
test('a group with two global roles, one added and one removed on the global permissions page', async ({page, ontrack}) => {
    const group = await ontrack.admin().createGroup({name: generate("g-"), description: "Release engineers"})
    await ontrack.admin().grantGlobalRoleToGroup(group, "PARTICIPANT")

    await login(page, ontrack)
    await page.goto(`${ontrack.connection.ui}/core/admin/global-permissions`)

    const key = `GROUP-${group.id}`
    const roles = page.getByTestId(`global-roles-${key}`)
    const participant = page.getByTestId(`global-role-${key}-PARTICIPANT`)
    const readOnly = page.getByTestId(`global-role-${key}-READ_ONLY`)
    await expect(participant).toBeVisible()

    // Adding a second role to the group keeps the first one
    await page.getByTestId('target').click()
    await page.keyboard.type(group.name)
    await page.locator('.ant-select-item-option').filter({hasText: group.name}).click()
    await page.getByTestId('role').click()
    await page.locator('.ant-select-item-option').filter({hasText: 'Read Only'}).click()
    await page.getByRole('button', {name: 'Add permission'}).click()

    await expect(readOnly).toBeVisible()
    await expect(participant).toBeVisible()
    await expect(roles).toContainText('Participant')
    await expect(roles).toContainText('Read Only')

    // Removing one role keeps the other
    await page.getByRole('button', {name: `Remove the Participant role from ${group.name}`}).click()
    await page.getByRole('button', {name: 'OK', exact: true}).click()

    await expect(participant).toHaveCount(0)
    await expect(readOnly).toBeVisible()
})
