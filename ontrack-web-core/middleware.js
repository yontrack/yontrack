/**
 * Sends phones to the mobile UI.
 *
 * The middleware is the only place this decision can be taken before anything is
 * rendered, which is what makes it a redirect rather than a flash of the desktop
 * UI followed by a jump. A **redirect** and not a rewrite, deliberately: the PWA
 * is scoped to `/mobile`, and a scope only works if that path actually appears
 * in the URL.
 *
 * All of the reasoning lives in `@components/mobile/mobileRedirect`, as a pure
 * function of four facts. This file is the adapter: it pulls those facts off the
 * request and turns an answer into a response.
 */
import {NextResponse} from "next/server"
import {decideMobileRedirect} from "@components/mobile/mobileRedirect"
import {DESKTOP_UI_COOKIE_NAME, DESKTOP_UI_COOKIE_VALUE} from "@components/mobile/desktopUiCookie"

export function middleware(request) {

    const decision = decideMobileRedirect({
        pathname: request.nextUrl.pathname,
        search: request.nextUrl.search,
        userAgent: request.headers.get('user-agent'),
        desktopOptOut: request.cookies.get(DESKTOP_UI_COOKIE_NAME)?.value === DESKTOP_UI_COOKIE_VALUE,
    })

    if (!decision) return NextResponse.next()

    const url = request.nextUrl.clone()
    url.pathname = decision.pathname
    url.search = decision.search

    // 307, so the browser keeps the method and - more to the point - does not
    // remember the answer. Which UI a path resolves to depends on the device and
    // on a cookie, both of which can change.
    const response = NextResponse.redirect(url)
    // Belt and braces for anything caching in front of the instance: the same
    // path legitimately answers differently per device and per cookie.
    response.headers.set('Cache-Control', 'no-store')
    response.headers.set('Vary', 'User-Agent, Cookie')
    return response
}

export const config = {
    /*
     * Pages only.
     *
     * `api` and `_next` are data and build output. The final `.*\.[A-Za-z0-9]+$`
     * excludes anything ending in a file extension - `favicon.ico`, the SVGs,
     * and (once the PWA lands) the service worker, the manifest and the icons,
     * which the redirect must not touch or installation breaks.
     *
     * It asks for an **extension** rather than for a dot anywhere, which is what
     * it used to do. A workflow instance id is `ISO_LOCAL_DATE_TIME-UUID` and
     * the timestamp is not truncated, so the id carries fractional seconds and
     * therefore a dot - `/extension/workflows/instances/2026-09-12T14:27:57.595125-<uuid>`
     * would have been read as a file and the middleware would never have run on
     * it. `mobileRoutes.js` makes the same distinction in `LOOKS_LIKE_A_FILE`,
     * and the two have to agree.
     */
    matcher: ['/((?!api|_next/static|_next/image|.*\\.[A-Za-z0-9]+$).*)'],
}
