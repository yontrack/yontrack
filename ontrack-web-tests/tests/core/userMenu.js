export const selectUserMenu = async (page, text) => {
    // Opens the menu
    await page.locator('#user-menu').click()
    // Waits for the next and click
    await page.getByText(text, {exact: true}).click()
}

/**
 * Opens the user menu drawer and returns it.
 *
 * The groups of the menu are collapsed submenus: an entry inside one of them is
 * not in the page until its group has been expanded, which is what
 * `openUserMenuGroup` is for.
 */
export const openUserMenu = async (page) => {
    await page.locator('#user-menu').click()
    return page.locator('.ant-drawer')
}

/**
 * Opens the user menu and expands one of its groups ("Configurations",
 * "System", ...), so that its items can be clicked or asserted on.
 */
export const openUserMenuGroup = async (page, group) => {
    const drawer = await openUserMenu(page)
    await drawer.getByRole('menuitem', {name: group, exact: true}).click()
    return drawer
}

/**
 * Selects an item inside a group of the user menu.
 */
export const selectUserMenuItem = async (page, group, text) => {
    const drawer = await openUserMenuGroup(page, group)
    await drawer.getByText(text, {exact: true}).click()
}
