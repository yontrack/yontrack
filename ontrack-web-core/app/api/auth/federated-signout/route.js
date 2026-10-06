import {NextResponse} from "next/server"
import {getToken} from "next-auth/jwt"
import {
    endProviderSession,
    publicBaseUrl,
    SIGNOUT_COMPLETE_PATH,
    SIGNOUT_RETURN_COOKIE,
    signOutReturnPath,
} from "@/app/api/auth/providerSignOut"

/**
 * Ends the session at the identity provider (#1734), called by `federatedSignOut()` *before* it
 * ends the local session - afterwards, the tokens this needs are gone.
 *
 * Answers `{url}`: where to send the browser once signed out locally, or `null` to stay in
 * Yontrack. When the browser does leave for the provider, a short-lived cookie scoped to the
 * landing route remembers which home it comes back to.
 *
 * The tokens are read from the JWT itself (`getToken`), not from the session, which carries
 * neither the refresh token nor the id token. Without the session cookie - which next-auth sets
 * `SameSite=Lax`, so a cross-site POST does not carry it - there is nothing to federate.
 */
export async function POST(request) {
    const {returnTo} = await request.json().catch(() => ({}))
    const token = await getToken({req: request})
    const requestOrigin = request.nextUrl.origin

    const {url} = await endProviderSession({token, requestOrigin})

    const response = NextResponse.json({url})
    response.headers.set("Cache-Control", "no-store")
    if (url) {
        response.cookies.set(SIGNOUT_RETURN_COOKIE, signOutReturnPath(returnTo), {
            httpOnly: true,
            sameSite: "lax",
            secure: publicBaseUrl(process.env, requestOrigin).startsWith("https://"),
            path: SIGNOUT_COMPLETE_PATH,
            // Long enough for a round trip through the provider, and no longer
            maxAge: 300,
        })
    }
    return response
}
