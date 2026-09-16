/**
 * The security headers the UI sends on every response (#1770).
 *
 * CommonJS on purpose: `next.config.js` is loaded by Node without any
 * transpilation and has to `require` this file, while `middleware.js` imports
 * it like any other module.
 *
 * Two layers, because of *when* each one is evaluated:
 *
 * - `securityHeaders()` feeds `headers()` in `next.config.js`. Next evaluates
 *   that at **build** time and writes the result into the routes manifest, so
 *   it covers every route - pages, `/mobile`, `/api/*`, static assets - but
 *   cannot read anything from the environment of a running container.
 * - `frameAncestorsOverride()` is applied by `middleware.js` at **run** time,
 *   from `YONTRACK_UI_FRAME_ANCESTORS`, for the customers who embed Yontrack
 *   pages in another site. A header set by the middleware replaces the one of
 *   the same name coming from the config.
 *
 * No `Strict-Transport-Security`: HSTS belongs to whatever terminates TLS in
 * front of Yontrack - it is the only component which knows whether the host is
 * HTTPS-only.
 */

/** The environment variable overriding who may frame the UI's pages. */
const FRAME_ANCESTORS_ENV = 'YONTRACK_UI_FRAME_ANCESTORS'

/** Who may frame the UI when nothing is configured: the UI itself. */
const DEFAULT_FRAME_ANCESTORS = "'self'"

/**
 * Browser features the UI never uses, denied to the UI and to anything it
 * frames. The clipboard and full-screen are deliberately absent: the copy
 * buttons and the editors use them.
 */
const DENIED_FEATURES = [
    'accelerometer',
    'autoplay',
    'camera',
    'display-capture',
    'geolocation',
    'gyroscope',
    'magnetometer',
    'microphone',
    'midi',
    'payment',
    'usb',
    'xr-spatial-tracking',
]

/**
 * The Content-Security-Policy the UI is *meant* to satisfy, sent as
 * report-only: violations show up in the browser console and nothing is
 * blocked. Enforcing it is a later decision, taken once the reports say it
 * breaks nothing.
 *
 * - `'unsafe-inline'` in `style-src`: Ant Design injects its CSS-in-JS styles
 *   at run time.
 * - `'unsafe-inline'` in `script-src`: Next's own bootstrap scripts and the
 *   theme script painted before the first render are inline. Nonces would need
 *   every page to be rendered per request.
 * - `'unsafe-eval'` in development only: the Next dev runtime evaluates its
 *   modules.
 * - `https:` images: issue trackers' status icons are served by the trackers.
 * - `blob:` workers: the graph layout and the editors run in workers.
 * - no `frame-ancestors`: browsers ignore it in a report-only policy; framing
 *   is enforced by its own header.
 * - no `form-action`: sign-in posts to the UI, which redirects to an identity
 *   provider of any origin, and browsers apply `form-action` to that redirect.
 *
 * @param {object} [options]
 * @param {boolean} [options.development] Whether the UI runs with `next dev`.
 * @returns {string}
 */
function contentSecurityPolicyReportOnly({development = false} = {}) {
    const directives = [
        ["default-src", "'self'"],
        ["script-src", "'self'", "'unsafe-inline'", ...(development ? ["'unsafe-eval'"] : [])],
        ["style-src", "'self'", "'unsafe-inline'"],
        ["img-src", "'self'", "data:", "blob:", "https:"],
        ["font-src", "'self'", "data:"],
        ["connect-src", "'self'"],
        ["worker-src", "'self'", "blob:"],
        ["frame-src", "'self'"],
        ["object-src", "'none'"],
        ["base-uri", "'self'"],
    ]
    return directives.map(directive => directive.join(' ')).join('; ')
}

/**
 * The headers sent on every route.
 *
 * @param {object} [options]
 * @param {boolean} [options.development] Whether the UI runs with `next dev`.
 * @returns {{key: string, value: string}[]}
 */
function securityHeaders({development = false} = {}) {
    return [
        {key: 'X-Content-Type-Options', value: 'nosniff'},
        {key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin'},
        {key: 'Permissions-Policy', value: DENIED_FEATURES.map(feature => `${feature}=()`).join(', ')},
        // Both: frame-ancestors for every current browser, X-Frame-Options for
        // the ones which predate it. A browser which knows frame-ancestors
        // ignores X-Frame-Options, which is what lets the override below work.
        {key: 'Content-Security-Policy', value: `frame-ancestors ${DEFAULT_FRAME_ANCESTORS}`},
        {key: 'X-Frame-Options', value: 'SAMEORIGIN'},
        {key: 'Content-Security-Policy-Report-Only', value: contentSecurityPolicyReportOnly({development})},
    ]
}

/**
 * A source list and nothing else: no `;` starting another directive, no `,`
 * starting another policy, no line break starting another header.
 */
const NOT_A_SOURCE_LIST = /[;,\r\n]/

/** The middleware asks on every page request; a wrong value is said once. */
let refusalLogged = false

/**
 * The framing header replacing the default one, from the raw value of
 * `YONTRACK_UI_FRAME_ANCESTORS` - a CSP source list such as
 * `'self' https://portal.example.com`.
 *
 * @param {string|null|undefined} raw
 * @returns {{key: string, value: string}|null} `null` to keep the default -
 *   when the variable is unset or blank, or when it holds more than a source
 *   list, which is refused rather than passed through.
 */
function frameAncestorsOverride(raw) {
    if (typeof raw !== 'string') return null
    const sources = raw.trim()
    if (sources.length === 0) return null
    if (NOT_A_SOURCE_LIST.test(sources)) {
        if (!refusalLogged) {
            refusalLogged = true
            console.warn(`${FRAME_ANCESTORS_ENV} must be a CSP source list; ignored, framing stays ${DEFAULT_FRAME_ANCESTORS}.`)
        }
        return null
    }
    return {key: 'Content-Security-Policy', value: `frame-ancestors ${sources}`}
}

module.exports = {
    FRAME_ANCESTORS_ENV,
    DEFAULT_FRAME_ANCESTORS,
    contentSecurityPolicyReportOnly,
    frameAncestorsOverride,
    securityHeaders,
}
