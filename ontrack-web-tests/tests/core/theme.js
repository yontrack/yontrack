const {expect} = require('@playwright/test');
const {graphQLCallMutation} = require("@ontrack/graphql");

/**
 * The theme, for the specs that drive it.
 *
 * The theme is a *server-side* preference on the shared account, so a spec that
 * touches it is not self-contained the way a normal one is: it mutates state
 * that outlives the browser context, and the runner is single-worker and
 * non-parallel. Anything left behind is what the next spec file drives the UI
 * in.
 *
 * There is exactly one `themeMode` and it is shared by both UIs, so this
 * matters to more than one file: `theme.spec.js` drives it from the desktop
 * user menu and `mobile.spec.js` from `/mobile/account`, and either would strand
 * the other - and itself - on `DARK`. Hence the reset living here rather than as
 * a local `const` in whichever spec wrote it first.
 */

/**
 * Puts the shared account's theme mode back to the default.
 *
 * Call it in **both** `beforeEach` and `afterEach`:
 *
 *   - `beforeEach` makes each test independent of whatever ran before it,
 *     including a previous run that crashed or was interrupted part-way.
 *     Without it, a leftover `DARK` fails the first assertion with no hint that
 *     the cause is stale state rather than the code under test.
 *   - `afterEach` keeps the rest of the suite clean.
 *
 * The management token is issued for the same account the UI logs in as, so
 * resetting through the API puts back exactly what the UI changed.
 */
export const resetThemeMode = async (ontrack) => {
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

/**
 * The resolved theme, as published on <html>.
 *
 * `data-theme` is what every colour token keys off and it is set before the
 * first paint, so asserting on it is asserting on what the user actually sees,
 * without depending on any one component's colours.
 */
export const expectTheme = async (page, theme) =>
    expect(page.locator('html')).toHaveAttribute('data-theme', theme)
