import {signOut} from "next-auth/react"

/**
 * Signs the user out of Yontrack **and** of the identity provider (#1734) - the one helper behind
 * the desktop user menu's and the mobile account screen's "Sign out".
 *
 * 1. The server ends the provider's session (`/api/auth/federated-signout`). It has to come
 *    first: it needs the tokens of the session, which the local sign-out deletes.
 * 2. The local session ends - **always**, whatever happened in step 1.
 * 3. The browser lands on `returnTo` (`/` or `/mobile`), either directly or, when the provider
 *    has to see the browser itself (front-channel), through the provider and back.
 *
 * Not for the 401 handler of `callGraphQL`, which signs out locally only: a 401 means the backend
 * refused the access token, not that the user asked to leave.
 *
 * @param returnTo   Home of the UI the user signs out of - never the current page, which would
 *                   bring the next person on a shared device straight back to it
 * @param navigate   Sends the browser to the provider - replaced by the tests
 */
export async function federatedSignOut({returnTo}, {navigate = (url) => window.location.assign(url)} = {}) {
    let url = null
    try {
        const response = await fetch("/api/auth/federated-signout", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({returnTo}),
        })
        if (response.ok) {
            url = (await response.json()).url ?? null
        }
    } catch (ignored) {
        // Fail-open: the local session ends anyway, below
    }

    if (url) {
        await signOut({redirect: false})
        navigate(url)
    } else {
        await signOut({callbackUrl: returnTo})
    }
}
