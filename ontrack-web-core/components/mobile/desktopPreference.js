/**
 * Switching this device between the two UIs.
 *
 * Both directions are the same pair of steps in the same order: remember the
 * choice, *then* navigate. The order is not cosmetic - the middleware reads that
 * cookie on the very request the navigation makes, so writing it afterwards
 * would have the user bounced straight back and the button looking broken.
 * Keeping both directions here is what keeps that ordering in one place.
 *
 * A real navigation rather than a router push, in both directions: `/mobile` and
 * the desktop UI are separate roots - one App Router, one Pages Router, each
 * with its own provider stack and stylesheets - and a client-side transition
 * cannot cross that boundary.
 */
import {deleteCookie, setCookie} from "cookies-next"
import {DESKTOP_UI_COOKIE_NAME, DESKTOP_UI_COOKIE_VALUE} from "@components/mobile/desktopUiCookie"
import {MOBILE_HOME} from "@components/mobile/mobileRoutes"

/*
 * No `maxAge` and no `expires`: this is a **session** cookie, and deliberately
 * so, even though the choice would be more convenient if it lasted.
 *
 * The way back is the "Mobile version" entry in the desktop user menu, and that
 * trigger *is* reachable at phone width: #1729 fixed the desktop header row so
 * it can no longer overflow its own 64px box and paint over the page bar below,
 * and `ontrack-web-tests/tests/core/navBar.spec.js` pins the hit target at
 * 393px and 375px so it stays that way.
 *
 * The session scoping stays anyway, as a second line rather than the only one.
 * Nothing about one non-responsive page bar was ever the whole risk: the desktop
 * UI is not responsive by design, and the escape hatch is the one thing standing
 * between a phone user and being stranded. Ending the choice with the browser
 * session turns "stranded for good" into "stranded until the browser restarts",
 * whatever else breaks. The cost is that someone who genuinely prefers the
 * desktop UI is redirected again next session; that is the better failure of the
 * two, and making the cookie long-lived is a decision of its own.
 */
const options = {
    path: '/',
    sameSite: 'lax',
}

/**
 * Puts this device on the desktop UI, and goes there.
 *
 * @param {string} href The desktop page to land on - the one the user was
 *   heading for, not the home page.
 */
export function switchToDesktopUI(href) {
    if (typeof document === 'undefined') return
    setCookie(DESKTOP_UI_COOKIE_NAME, DESKTOP_UI_COOKIE_VALUE, options)
    window.location.assign(href)
}

/**
 * Puts this device back on the mobile UI, and goes there.
 *
 * The way back, driven from the "Mobile version" entry in the desktop user menu.
 * Without it a phone that once chose the desktop UI could never return - and
 * once the mobile UI is installed as a PWA there is no address bar to escape
 * with.
 */
export function switchToMobileUI() {
    if (typeof document === 'undefined') return
    deleteCookie(DESKTOP_UI_COOKIE_NAME, options)
    window.location.assign(MOBILE_HOME)
}
