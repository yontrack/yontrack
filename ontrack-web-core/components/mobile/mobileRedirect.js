/**
 * The decision the proxy takes on every page request: leave this request
 * on the desktop UI, or send it to the mobile one.
 *
 * Kept apart from `proxy.js` as a pure function of the four facts that
 * matter, so the rules can be tested without building a `NextRequest` and
 * without a server runtime. `proxy.js` is the thin adapter over it.
 */
import {isPhoneUserAgent} from "@components/mobile/userAgent"
import {
    DESKTOP_HOME,
    INTERSTITIAL_TARGET_PARAM,
    isRedirectExempt,
    MOBILE_HOME,
    MOBILE_INTERSTITIAL,
    mobileEquivalent,
} from "@components/mobile/mobileRoutes"

/**
 * A path on this instance and nothing else: a leading `/` not followed by a
 * second `/` or by a backslash.
 *
 * The backslash matters. Browsers normalise `\` to `/` while parsing a URL, so
 * `/\evil.example.com` is another spelling of `//evil.example.com` - a host,
 * not a path.
 */
const LOCAL_PATH = /^\/(?![/\\])/

/**
 * Anything a browser strips while parsing a URL: C0 controls, space and DEL.
 *
 * Stripping is what makes them dangerous - `/\u0009/evil.example.com` becomes
 * `//evil.example.com` after the browser is done with it, so a value carrying
 * one cannot be checked as written. Rejected outright rather than cleaned: a
 * legitimate path has no reason to hold one.
 */
const STRIPPED_BY_BROWSERS = /[\u0000-\u0020\u007f]/

/**
 * Keeps a destination a path on this instance.
 *
 * It ends up in a `window.location.assign` on the interstitial, so a host- or
 * scheme-carrying value would turn that page into an open redirect. The
 * proxy builds the target itself, but the interstitial reads it back off a
 * query string anyone can type.
 *
 * @param {unknown} url
 * @param {string} fallback Where to go when the value cannot be trusted.
 * @returns {string}
 */
const asLocalPath = (url, fallback) =>
    typeof url === 'string' && LOCAL_PATH.test(url) && !STRIPPED_BY_BROWSERS.test(url) ? url : fallback

/**
 * The destination the interstitial should offer, from its `target` parameter.
 *
 * Falls back to the desktop home rather than the mobile one: a user who reached
 * the interstitial wants a desktop page, and sending them back to `/mobile`
 * would be the silent drop the interstitial exists to avoid.
 *
 * @param {unknown} raw The raw `target` query parameter.
 * @returns {string}
 */
export function resolveInterstitialTarget(raw) {
    return asLocalPath(raw, DESKTOP_HOME)
}

/**
 * Where should this request go?
 *
 * @param {object} request
 * @param {string} request.pathname The requested path.
 * @param {string} [request.search] The query string, `?` included.
 * @param {string|null} [request.userAgent] The raw `User-Agent` header.
 * @param {boolean} [request.desktopOptOut] Whether this device has opted out of
 *   the mobile UI.
 * @returns {{pathname: string, search: string}|null} Where to redirect to, or
 *   `null` to serve the request as asked.
 */
export function decideMobileRedirect({pathname, search = '', userAgent = null, desktopOptOut = false}) {
    if (desktopOptOut) return null
    if (!isPhoneUserAgent(userAgent)) return null
    if (isRedirectExempt(pathname)) return null

    const mobile = mobileEquivalent(pathname)
    if (mobile) {
        // The query string is the user's, not ours - a deep link from a
        // notification carries its context there.
        return {pathname: mobile, search}
    }

    const target = asLocalPath(`${pathname}${search}`, MOBILE_HOME)
    return {
        pathname: MOBILE_INTERSTITIAL,
        search: `?${new URLSearchParams({[INTERSTITIAL_TARGET_PARAM]: target})}`,
    }
}
