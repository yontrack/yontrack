/**
 * @jest-environment node
 */
/*
 * The server side of federated sign-out (#1734): ending the session at the identity provider, not only Yontrack's.
 *
 * The `keycloak` provider ends it from the server (back-channel), the generic `oidc` provider
 * sends the browser to the provider's `end_session_endpoint` (front-channel). Whatever fails,
 * the answer is "sign out locally" - the local session always ends.
 */
import {
    endProviderSession,
    federatedSignOutEnabled,
    federatedSignOutStartupNotice,
    signOutCompleteUrl,
    signOutReturnPath,
} from "../../../app/api/auth/providerSignOut"

const keycloakEnv = {
    NEXTAUTH_ISSUER: "https://sso.example.com/realms/ontrack",
    NEXTAUTH_CLIENT_ID: "ontrack-client",
    NEXTAUTH_CLIENT_SECRET: "ontrack-secret",
    NEXTAUTH_URL: "https://yontrack.example.com",
}

const oidcEnv = {
    NEXTAUTH_PROVIDER: "oidc",
    NEXTAUTH_ISSUER: "https://login.example.com/tenant/v2.0",
    NEXTAUTH_CLIENT_ID: "oidc-client",
    NEXTAUTH_CLIENT_SECRET: "oidc-secret",
    NEXTAUTH_URL: "https://yontrack.example.com",
}

const END_SESSION = "https://login.example.com/tenant/oauth2/v2.0/logout"

describe("federated sign-out", () => {

    const originalFetch = global.fetch

    beforeEach(() => {
        global.fetch = jest.fn()
        jest.spyOn(console, "warn").mockImplementation(() => {})
    })

    afterEach(() => {
        global.fetch = originalFetch
        console.warn.mockRestore()
    })

    describe("the operator switch", () => {

        it("is on when not set", () => {
            expect(federatedSignOutEnabled({})).toBe(true)
        })

        it("is on when set to true", () => {
            expect(federatedSignOutEnabled({NEXTAUTH_FEDERATED_SIGNOUT: "true"})).toBe(true)
        })

        it("is off when set to false", () => {
            expect(federatedSignOutEnabled({NEXTAUTH_FEDERATED_SIGNOUT: "false"})).toBe(false)
        })

        it("signs out locally when off, for both providers", async () => {
            const token = {refreshToken: "rt", idToken: "it"}
            expect(await endProviderSession({token, env: {...keycloakEnv, NEXTAUTH_FEDERATED_SIGNOUT: "false"}}))
                .toEqual({url: null})
            expect(await endProviderSession({token, env: {...oidcEnv, NEXTAUTH_FEDERATED_SIGNOUT: "false"}}))
                .toEqual({url: null})
            expect(global.fetch).not.toHaveBeenCalled()
        })
    })

    describe("the startup notice", () => {

        it("is given for an oidc install which has not set the switch", () => {
            const notice = federatedSignOutStartupNotice(oidcEnv)
            expect(notice).toContain("https://yontrack.example.com/api/auth/signout-complete")
            expect(notice).toContain("NEXTAUTH_FEDERATED_SIGNOUT=false")
        })

        it("is not given once the switch is set, either way", () => {
            expect(federatedSignOutStartupNotice({...oidcEnv, NEXTAUTH_FEDERATED_SIGNOUT: "true"})).toBeNull()
            expect(federatedSignOutStartupNotice({...oidcEnv, NEXTAUTH_FEDERATED_SIGNOUT: "false"})).toBeNull()
        })

        it("names a placeholder rather than a relative URL without NEXTAUTH_URL", () => {
            const {NEXTAUTH_URL, ...withoutUrl} = oidcEnv
            expect(federatedSignOutStartupNotice(withoutUrl))
                .toContain("https://<your-yontrack>/api/auth/signout-complete")
        })

        it("is not given for a keycloak install, which needs no configuration", () => {
            expect(federatedSignOutStartupNotice(keycloakEnv)).toBeNull()
        })
    })

    describe("where the browser comes back", () => {

        it("is the mobile home for the mobile UI", () => {
            expect(signOutReturnPath("/mobile")).toBe("/mobile")
        })

        it("is the desktop home for anything else - never an open redirect", () => {
            expect(signOutReturnPath("/")).toBe("/")
            expect(signOutReturnPath(undefined)).toBe("/")
            expect(signOutReturnPath("/mobile/build/12")).toBe("/")
            expect(signOutReturnPath("https://evil.example.com")).toBe("/")
            expect(signOutReturnPath("//evil.example.com")).toBe("/")
        })

        it("is one landing URL, built on the public base URL", () => {
            expect(signOutCompleteUrl(oidcEnv)).toBe("https://yontrack.example.com/api/auth/signout-complete")
        })

        it("ignores a trailing slash or a /api/auth suffix on NEXTAUTH_URL", () => {
            expect(signOutCompleteUrl({NEXTAUTH_URL: "https://yontrack.example.com/"}))
                .toBe("https://yontrack.example.com/api/auth/signout-complete")
            expect(signOutCompleteUrl({NEXTAUTH_URL: "https://yontrack.example.com/api/auth"}))
                .toBe("https://yontrack.example.com/api/auth/signout-complete")
        })

        it("falls back on the origin of the request without NEXTAUTH_URL", () => {
            expect(signOutCompleteUrl({}, "http://localhost:3000"))
                .toBe("http://localhost:3000/api/auth/signout-complete")
        })
    })

    describe("keycloak: back-channel", () => {

        it("ends the Keycloak session from the server, with the refresh token", async () => {
            global.fetch.mockResolvedValueOnce({ok: true, status: 204})

            const result = await endProviderSession({token: {refreshToken: "rt-1"}, env: keycloakEnv})

            expect(result).toEqual({url: null})
            const [url, opts] = global.fetch.mock.calls[0]
            expect(url).toBe("https://sso.example.com/realms/ontrack/protocol/openid-connect/logout")
            expect(opts.method).toBe("POST")
            expect(opts.headers["Content-Type"]).toBe("application/x-www-form-urlencoded")
            const body = new URLSearchParams(opts.body)
            expect(body.get("client_id")).toBe("ontrack-client")
            expect(body.get("client_secret")).toBe("ontrack-secret")
            expect(body.get("refresh_token")).toBe("rt-1")
            expect(console.warn).not.toHaveBeenCalled()
        })

        it("uses NEXTAUTH_ISSUER_INTERNAL, like the token refresh", async () => {
            global.fetch.mockResolvedValueOnce({ok: true, status: 204})

            await endProviderSession({
                token: {refreshToken: "rt-1"},
                env: {...keycloakEnv, NEXTAUTH_ISSUER_INTERNAL: "http://keycloak:8080/realms/ontrack"},
            })

            expect(global.fetch.mock.calls[0][0])
                .toBe("http://keycloak:8080/realms/ontrack/protocol/openid-connect/logout")
        })

        it("signs out locally, with a warning, when Keycloak refuses", async () => {
            global.fetch.mockResolvedValueOnce({ok: false, status: 400})

            expect(await endProviderSession({token: {refreshToken: "rt-1"}, env: keycloakEnv})).toEqual({url: null})
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("400"))
        })

        it("signs out locally, with a warning, when Keycloak is unreachable", async () => {
            global.fetch.mockRejectedValueOnce(new Error("connect ECONNREFUSED"))

            expect(await endProviderSession({token: {refreshToken: "rt-1"}, env: keycloakEnv})).toEqual({url: null})
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("ECONNREFUSED"))
        })

        it("signs out locally, with a warning, without a refresh token", async () => {
            expect(await endProviderSession({token: {}, env: keycloakEnv})).toEqual({url: null})
            expect(global.fetch).not.toHaveBeenCalled()
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("refresh token"))
        })
    })

    describe("oidc: front-channel", () => {

        const discovery = (document) => ({ok: true, json: async () => document})

        it("sends the browser to the end_session_endpoint, coming back to the one landing URL", async () => {
            global.fetch.mockResolvedValueOnce(discovery({end_session_endpoint: END_SESSION}))

            const {url} = await endProviderSession({token: {idToken: "id-1"}, env: oidcEnv})

            expect(global.fetch).toHaveBeenCalledWith(
                "https://login.example.com/tenant/v2.0/.well-known/openid-configuration"
            )
            const redirect = new URL(url)
            expect(`${redirect.origin}${redirect.pathname}`).toBe(END_SESSION)
            expect(redirect.searchParams.get("id_token_hint")).toBe("id-1")
            expect(redirect.searchParams.get("post_logout_redirect_uri"))
                .toBe("https://yontrack.example.com/api/auth/signout-complete")
            expect(redirect.searchParams.get("client_id")).toBe("oidc-client")
            expect(console.warn).not.toHaveBeenCalled()
        })

        it("keeps the query of an end_session_endpoint which has one", async () => {
            global.fetch.mockResolvedValueOnce(discovery({end_session_endpoint: `${END_SESSION}?p=policy`}))

            const {url} = await endProviderSession({token: {idToken: "id-1"}, env: oidcEnv})

            const redirect = new URL(url)
            expect(redirect.searchParams.get("p")).toBe("policy")
            expect(redirect.searchParams.get("id_token_hint")).toBe("id-1")
        })

        it("signs out locally, with a warning, when discovery advertises no end_session_endpoint", async () => {
            global.fetch.mockResolvedValueOnce(discovery({token_endpoint: "https://login.example.com/token"}))

            expect(await endProviderSession({token: {idToken: "id-1"}, env: oidcEnv})).toEqual({url: null})
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("end_session_endpoint"))
        })

        it("signs out locally, with a warning, when discovery fails", async () => {
            global.fetch.mockResolvedValueOnce({ok: false, status: 503, json: async () => ({})})

            expect(await endProviderSession({token: {idToken: "id-1"}, env: oidcEnv})).toEqual({url: null})
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("503"))
        })

        it("signs out locally, with a warning, when the provider is unreachable", async () => {
            global.fetch.mockRejectedValueOnce(new Error("getaddrinfo ENOTFOUND"))

            expect(await endProviderSession({token: {idToken: "id-1"}, env: oidcEnv})).toEqual({url: null})
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("ENOTFOUND"))
        })

        it("signs out locally, with a warning, without an id token", async () => {
            expect(await endProviderSession({token: {refreshToken: "rt"}, env: oidcEnv})).toEqual({url: null})
            expect(global.fetch).not.toHaveBeenCalled()
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("id token"))
        })
    })

    it("signs out locally, with a warning, without a session token", async () => {
        // The secret or the cookie misconfigured: federation must not go off without a word
        expect(await endProviderSession({token: null, env: keycloakEnv})).toEqual({url: null})
        expect(await endProviderSession({token: null, env: oidcEnv})).toEqual({url: null})
        expect(global.fetch).not.toHaveBeenCalled()
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("no session token"))
    })
})
