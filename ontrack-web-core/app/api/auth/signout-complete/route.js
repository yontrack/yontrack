import {NextResponse} from "next/server"
import {
    publicBaseUrl,
    SIGNOUT_COMPLETE_PATH,
    SIGNOUT_RETURN_COOKIE,
    signOutReturnPath,
} from "@/app/api/auth/providerSignOut"

/**
 * Where the identity provider sends the browser back after a front-channel sign-out (#1734) - the
 * one `post_logout_redirect_uri` an operator registers, whichever UI the user signed out of.
 *
 * Forwards to the desktop or the mobile home, as remembered by the cookie the sign-out set, and
 * to nothing else. Under `/api/`, so the mobile redirect of the proxy leaves it alone.
 */
export async function GET(request) {
    const path = signOutReturnPath(request.cookies.get(SIGNOUT_RETURN_COOKIE)?.value)
    const base = publicBaseUrl(process.env, request.nextUrl.origin)

    // 303: the browser follows with a GET, and remembers nothing
    const response = NextResponse.redirect(`${base}${path}`, 303)
    response.headers.set("Cache-Control", "no-store")
    response.cookies.set(SIGNOUT_RETURN_COOKIE, "", {path: SIGNOUT_COMPLETE_PATH, maxAge: 0})
    return response
}
