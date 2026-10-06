/**
 * @jest-environment node
 */
/*
 * The two routes of federated sign-out (#1734): the one the "Sign out" controls call before
 * ending the local session, and the landing the provider sends the browser back to.
 */
jest.mock("next-auth/jwt", () => ({
    getToken: jest.fn(),
}))
jest.mock("../../../app/api/auth/providerSignOut", () => ({
    ...jest.requireActual("../../../app/api/auth/providerSignOut"),
    endProviderSession: jest.fn(),
}))

import {NextRequest} from "next/server"
import {getToken} from "next-auth/jwt"
import {endProviderSession} from "../../../app/api/auth/providerSignOut"
import {POST} from "../../../app/api/auth/federated-signout/route"
import {GET} from "../../../app/api/auth/signout-complete/route"

const ORIGIN = "http://localhost:3000"

describe("federated sign-out routes", () => {

    const originalUrl = process.env.NEXTAUTH_URL

    beforeEach(() => {
        delete process.env.NEXTAUTH_URL
        jest.resetAllMocks()
    })

    afterAll(() => {
        if (originalUrl === undefined) delete process.env.NEXTAUTH_URL
        else process.env.NEXTAUTH_URL = originalUrl
    })

    const signOutRequest = (body) => new NextRequest(`${ORIGIN}/api/auth/federated-signout`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    })

    describe("POST /api/auth/federated-signout", () => {

        it("federates with the token of the session, read on the server", async () => {
            getToken.mockResolvedValue({refreshToken: "rt"})
            endProviderSession.mockResolvedValue({url: null})

            const response = await POST(signOutRequest({returnTo: "/"}))

            expect(response.status).toBe(200)
            expect(await response.json()).toEqual({url: null})
            expect(endProviderSession).toHaveBeenCalledWith({token: {refreshToken: "rt"}, requestOrigin: ORIGIN})
        })

        it("sets no cookie when the browser does not leave for the provider", async () => {
            getToken.mockResolvedValue({refreshToken: "rt"})
            endProviderSession.mockResolvedValue({url: null})

            const response = await POST(signOutRequest({returnTo: "/mobile"}))

            expect(response.cookies.get("yontrack-signout-return")).toBeUndefined()
        })

        it("remembers where to come back to when the browser leaves for the provider", async () => {
            getToken.mockResolvedValue({idToken: "it"})
            endProviderSession.mockResolvedValue({url: "https://idp.example.com/logout?x=1"})

            const response = await POST(signOutRequest({returnTo: "/mobile"}))

            expect(await response.json()).toEqual({url: "https://idp.example.com/logout?x=1"})
            const cookie = response.cookies.get("yontrack-signout-return")
            expect(cookie.value).toBe("/mobile")
            expect(cookie.httpOnly).toBe(true)
            expect(cookie.sameSite).toBe("lax")
            expect(cookie.path).toBe("/api/auth/signout-complete")
            expect(cookie.maxAge).toBe(300)
        })

        it("only ever remembers one of the two homes", async () => {
            getToken.mockResolvedValue({idToken: "it"})
            endProviderSession.mockResolvedValue({url: "https://idp.example.com/logout"})

            const response = await POST(signOutRequest({returnTo: "https://evil.example.com"}))

            expect(response.cookies.get("yontrack-signout-return").value).toBe("/")
        })

        it("marks the cookie secure on an HTTPS instance", async () => {
            process.env.NEXTAUTH_URL = "https://yontrack.example.com"
            getToken.mockResolvedValue({idToken: "it"})
            endProviderSession.mockResolvedValue({url: "https://idp.example.com/logout"})

            const response = await POST(signOutRequest({returnTo: "/"}))

            expect(response.cookies.get("yontrack-signout-return").secure).toBe(true)
        })

        it("signs out locally on a request without a body", async () => {
            getToken.mockResolvedValue(null)
            endProviderSession.mockResolvedValue({url: null})

            const response = await POST(new NextRequest(`${ORIGIN}/api/auth/federated-signout`, {method: "POST"}))

            expect(await response.json()).toEqual({url: null})
        })

        it("is never cached", async () => {
            getToken.mockResolvedValue(null)
            endProviderSession.mockResolvedValue({url: null})

            const response = await POST(signOutRequest({}))

            expect(response.headers.get("cache-control")).toBe("no-store")
        })
    })

    describe("GET /api/auth/signout-complete", () => {

        const landing = (cookie) => new NextRequest(`${ORIGIN}/api/auth/signout-complete`, {
            headers: cookie ? {Cookie: `yontrack-signout-return=${encodeURIComponent(cookie)}`} : {},
        })

        it("forwards to the mobile home when the sign-out came from the mobile UI", async () => {
            const response = await GET(landing("/mobile"))

            expect(response.status).toBe(303)
            expect(response.headers.get("location")).toBe(`${ORIGIN}/mobile`)
        })

        it("forwards to the desktop home without the cookie", async () => {
            const response = await GET(landing(undefined))

            expect(response.headers.get("location")).toBe(`${ORIGIN}/`)
        })

        it("never forwards anywhere else", async () => {
            const response = await GET(landing("https://evil.example.com"))

            expect(response.headers.get("location")).toBe(`${ORIGIN}/`)
        })

        it("forwards on the public base URL", async () => {
            process.env.NEXTAUTH_URL = "https://yontrack.example.com"

            const response = await GET(landing("/mobile"))

            expect(response.headers.get("location")).toBe("https://yontrack.example.com/mobile")
        })

        it("clears the cookie, and is never cached", async () => {
            const response = await GET(landing("/mobile"))

            expect(response.cookies.get("yontrack-signout-return").value).toBe("")
            expect(response.headers.get("cache-control")).toBe("no-store")
        })
    })
})
