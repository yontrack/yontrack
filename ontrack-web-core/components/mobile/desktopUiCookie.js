/**
 * The cookie that opts a device out of the mobile UI.
 *
 * Its own module because it is read from both sides of a boundary that must not
 * be crossed: the proxy, running on the server with no DOM, and the browser,
 * through `cookies-next`. Neither should have to import the other's module to
 * learn the name.
 *
 * A *device* choice, not a user one: the same account on a laptop and a phone
 * wants different answers, so this is not a server-side preference.
 */

export const DESKTOP_UI_COOKIE_NAME = 'yontrack-ui'

/** The only value of that cookie which means anything. */
export const DESKTOP_UI_COOKIE_VALUE = 'desktop'
