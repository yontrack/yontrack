import {DESKTOP_HOME, MOBILE_HOME} from "@components/mobile/mobileRoutes"

/**
 * The server side of federated sign-out (#1734): ending the session at the identity provider, and not only
 * Yontrack's own.
 *
 * Signing out of next-auth alone leaves the provider's session standing, so the next sign-in is
 * answered silently - a "Sign out" which signs nobody out. How the provider's session is ended
 * depends on the provider `authOptions` builds:
 *
 * - `keycloak` - **back-channel**: the server POSTs the refresh token to Keycloak's logout end
 *   point. Invisible, and no change is needed at the provider.
 * - `oidc` - **front-channel** (RP-initiated logout): the browser is sent to the provider's
 *   `end_session_endpoint`, which sends it back to the one landing URL of
 *   `signOutCompleteUrl()`. Auth0 and Entra have no back-channel equivalent.
 *
 * Fail-open, always: whatever goes wrong at the provider, the answer is `{url: null}` - "sign out
 * locally" - and a warning in the server log naming the cause. Keeping someone signed in because
 * the provider did not answer would be the worse failure.
 *
 * Only for the two deliberate "Sign out" controls. The 401 handler of `callGraphQL` stays
 * local-only: a 401 means the backend refused the access token, not that the user asked to leave.
 */

/** The landing route the provider sends the browser back to, after a front-channel sign-out. */
export const SIGNOUT_COMPLETE_PATH = "/api/auth/signout-complete"

/** Where the landing route forwards to, set at sign-out and read back by the landing route. */
export const SIGNOUT_RETURN_COOKIE = "yontrack-signout-return"

/** `NEXTAUTH_FEDERATED_SIGNOUT`, on unless set to `false`. */
export const federatedSignOutEnabled = (env = process.env) => env.NEXTAUTH_FEDERATED_SIGNOUT !== "false"

const isOidc = (env) => env.NEXTAUTH_PROVIDER === "oidc"

/**
 * Where the browser lands once signed out: one of two homes, and nothing else - the value comes
 * back from a cookie, and anything a cookie carries must not become an open redirect.
 */
export const signOutReturnPath = (value) => value === MOBILE_HOME ? MOBILE_HOME : DESKTOP_HOME

/**
 * The public base URL of the UI: `NEXTAUTH_URL`, which next-auth accepts with or without its own
 * `/api/auth` suffix. The provider redirects the *browser* there, so it must be the public URL;
 * without `NEXTAUTH_URL` - which next-auth itself needs behind a proxy - the origin of the request
 * is the best guess left.
 */
export const publicBaseUrl = (env = process.env, requestOrigin = undefined) => {
    const base = env.NEXTAUTH_URL ?? requestOrigin ?? ""
    return base.replace(/\/+$/, "").replace(/\/api\/auth$/, "")
}

/** The one URL an operator registers at their provider. */
export const signOutCompleteUrl = (env = process.env, requestOrigin = undefined) =>
    `${publicBaseUrl(env, requestOrigin)}${SIGNOUT_COMPLETE_PATH}`

/**
 * The line logged at startup by an `oidc` install which has not set the switch either way: the
 * only warning an operator gets that the provider needs one more URL, since Yontrack cannot check
 * whether it is registered.
 */
export const federatedSignOutStartupNotice = (env = process.env) => {
    if (!isOidc(env) || env.NEXTAUTH_FEDERATED_SIGNOUT !== undefined) return null
    const url = env.NEXTAUTH_URL ? signOutCompleteUrl(env) : `https://<your-yontrack>${SIGNOUT_COMPLETE_PATH}`
    return "Federated sign-out is on: signing out also ends the session at the identity provider. " +
        `Register ${url} as a post-logout redirect URI at the provider, ` +
        "or set NEXTAUTH_FEDERATED_SIGNOUT=false to sign out of Yontrack only."
}

const warn = (message) => console.warn(`Federated sign-out: ${message} - signing out locally only`)

/**
 * Keycloak's logout end point, built on the issuer used for discovery rather than discovered:
 * Keycloak advertises its `end_session_endpoint` on its browser-facing hostname, which a
 * container cannot necessarily reach.
 */
const backChannelSignOut = async (token, env) => {
    if (!token.refreshToken) {
        warn("no refresh token in the session")
        return {url: null}
    }
    const issuer = env.NEXTAUTH_ISSUER_INTERNAL || env.NEXTAUTH_ISSUER
    try {
        const response = await fetch(`${issuer}/protocol/openid-connect/logout`, {
            method: "POST",
            headers: {"Content-Type": "application/x-www-form-urlencoded"},
            body: new URLSearchParams({
                client_id: env.NEXTAUTH_CLIENT_ID,
                client_secret: env.NEXTAUTH_CLIENT_SECRET,
                refresh_token: token.refreshToken,
            }).toString(),
        })
        if (!response.ok) {
            warn(`Keycloak answered ${response.status}`)
        }
    } catch (error) {
        warn(`Keycloak could not be reached (${error.message})`)
    }
    return {url: null}
}

const frontChannelSignOut = async (token, env, requestOrigin) => {
    if (!token.idToken) {
        warn("no id token in the session")
        return {url: null}
    }
    let endSessionEndpoint
    try {
        const response = await fetch(`${env.NEXTAUTH_ISSUER}/.well-known/openid-configuration`)
        if (!response.ok) {
            warn(`the provider's discovery document answered ${response.status}`)
            return {url: null}
        }
        endSessionEndpoint = (await response.json()).end_session_endpoint
    } catch (error) {
        warn(`the provider could not be reached (${error.message})`)
        return {url: null}
    }
    if (!endSessionEndpoint) {
        warn("the provider advertises no end_session_endpoint")
        return {url: null}
    }
    const url = new URL(endSessionEndpoint)
    url.searchParams.set("id_token_hint", token.idToken)
    url.searchParams.set("post_logout_redirect_uri", signOutCompleteUrl(env, requestOrigin))
    url.searchParams.set("client_id", env.NEXTAUTH_CLIENT_ID)
    return {url: url.toString()}
}

/**
 * Ends the provider's session for the next-auth JWT `token`.
 *
 * @return `{url}` - where to send the browser once the local session has ended, or `null` to sign
 * out locally (back-channel done, switch off, or federation failed).
 */
export const endProviderSession = async ({token, env = process.env, requestOrigin = undefined}) => {
    if (!federatedSignOutEnabled(env)) return {url: null}
    if (!token) {
        // A sign-out from a signed-in UI always carries the session cookie: a missing token says
        // the secret or the cookie is misconfigured, which would otherwise turn federation off
        // without a word
        warn("no session token on the request")
        return {url: null}
    }
    return isOidc(env)
        ? frontChannelSignOut(token, env, requestOrigin)
        : backChannelSignOut(token, env)
}
