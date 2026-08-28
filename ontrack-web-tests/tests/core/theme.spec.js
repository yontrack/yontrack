const {expect} = require('@playwright/test');
const {login} = require("./login");
const {test} = require("../fixtures/connection");
const {graphQLCallMutation} = require("@ontrack/graphql");

/**
 * The resolved theme is published as `data-theme` on <html>: it is what every
 * colour token keys off, and it is set before the first paint. Asserting on it
 * is therefore asserting on what the user actually sees, without depending on
 * any one component's colours.
 *
 * Every test pins `colorScheme` explicitly: the default mode is `system`, so
 * otherwise the expectations would depend on the machine running the tests.
 */

const themeSwitch = (page) => page.locator('#theme-switch')

const selectTheme = async (page, label) => {
    // The switch deliberately does not close the drawer, so the user can compare
    // themes without reopening the menu - only open it when it is not already up.
    if (!await themeSwitch(page).isVisible()) {
        await page.locator('#user-menu').click()
        await expect(themeSwitch(page)).toBeVisible()
    }
    await themeSwitch(page).getByText(label, {exact: true}).click()
}

const closeUserMenu = async (page) => {
    await page.keyboard.press('Escape')
    await expect(themeSwitch(page)).toBeHidden()
}

const expectTheme = async (page, theme) =>
    expect(page.locator('html')).toHaveAttribute('data-theme', theme)

/**
 * The theme is a *server-side* preference on the shared account, so these tests
 * are not self-contained the way a normal spec is - they mutate state that
 * outlives the browser context.
 *
 * Reset on both sides of every test, and deliberately so:
 *
 *   - `beforeEach` makes each test independent of whatever ran before it,
 *     including a previous run that crashed or was interrupted part-way. Without
 *     it, a leftover `DARK` makes the very first assertion below fail with no
 *     hint that the cause is stale state rather than the code under test.
 *   - `afterEach` keeps the rest of the suite clean: the runner is single-worker
 *     and non-parallel, so every spec file scheduled after this one would
 *     otherwise drive the UI in whatever theme the last test left behind.
 *
 * The management token is issued for the same account the UI logs in as, so
 * resetting through the API puts back exactly what the UI changed.
 */
const resetThemeMode = async (ontrack) => {
    await graphQLCallMutation(
        ontrack.connection,
        'setPreferences',
        `mutation ResetThemeMode {
            setPreferences(input: {themeMode: SYSTEM}) {
                errors { message }
            }
        }`,
    )
}

test.beforeEach(async ({ontrack}) => resetThemeMode(ontrack))
test.afterEach(async ({ontrack}) => resetThemeMode(ontrack))

test('Switching to dark mode takes effect immediately and survives a reload', async ({page, ontrack}) => {
    await page.emulateMedia({colorScheme: 'light'})
    await login(page, ontrack)
    await expectTheme(page, 'light')

    await selectTheme(page, "Dark")
    // Immediately - no reload.
    await expectTheme(page, 'dark')

    await closeUserMenu(page)
    await page.reload()
    await expect(page.getByText("Dashboard", {exact: true})).toBeVisible()
    await expectTheme(page, 'dark')
})

test('The theme choice follows the user into a fresh browser context', async ({page, ontrack, browser}) => {
    await page.emulateMedia({colorScheme: 'light'})
    await login(page, ontrack)
    await selectTheme(page, "Dark")
    await expectTheme(page, 'dark')
    await closeUserMenu(page)

    // A brand new context: no cookies, no local storage. Only the server-side
    // preference can carry the choice across.
    const context = await browser.newContext({colorScheme: 'light'})
    try {
        const otherPage = await context.newPage()
        await login(otherPage, ontrack)
        await expectTheme(otherPage, 'dark')
    } finally {
        await context.close()
    }
})

test('Switching back to light mode restores the light theme', async ({page, ontrack}) => {
    await page.emulateMedia({colorScheme: 'light'})
    await login(page, ontrack)

    await selectTheme(page, "Dark")
    await expectTheme(page, 'dark')
    await selectTheme(page, "Light")
    await expectTheme(page, 'light')

    await closeUserMenu(page)
    await page.reload()
    await expect(page.getByText("Dashboard", {exact: true})).toBeVisible()
    await expectTheme(page, 'light')
})

test('Auto mode follows the operating system preference', async ({page, ontrack}) => {
    await page.emulateMedia({colorScheme: 'dark'})
    await login(page, ontrack)

    await selectTheme(page, "Auto")
    await expectTheme(page, 'dark')

    // The OS flipping while the page is open must be picked up live.
    await page.emulateMedia({colorScheme: 'light'})
    await expectTheme(page, 'light')

    await page.emulateMedia({colorScheme: 'dark'})
    await expectTheme(page, 'dark')
})

test('The custom sign-in page respects the theme, before any user is known', async ({page, ontrack}) => {
    await page.emulateMedia({colorScheme: 'light'})
    await login(page, ontrack)
    await selectTheme(page, "Dark")
    await expectTheme(page, 'dark')
    await closeUserMenu(page)

    // Navigated to directly, rather than by signing out.
    //
    // The custom page is opt-in - `YONTRACK_UI_AUTH_SIGNIN_CUSTOM=true` - and
    // without it `signOut()` lands on next-auth's own built-in page at
    // /api/auth/signin, whose HTML next-auth renders outside our layouts. That
    // page cannot carry `data-theme` at all (it follows the OS via
    // `theme.colorScheme: 'auto'` in authOptions instead), so asserting on it
    // here would be asserting the wrong thing.
    //
    // This is the page §7 of the ticket is about: a separate App Router root with
    // its own <html>, outside the provider stack, with no user preference to read
    // - so the cookie mirror is the only source, which is exactly what is checked.
    await page.goto(`${ontrack.connection.ui}/auth/signin`)
    await expectTheme(page, 'dark')
})
