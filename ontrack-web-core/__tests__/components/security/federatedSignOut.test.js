/*
 * The one helper behind the two "Sign out" controls (#1734): end the provider's session, end the
 * local one whatever happened, and land on the home of the UI the user signed out of.
 */
jest.mock("next-auth/react", () => ({
    signOut: jest.fn(async () => undefined),
    getCsrfToken: jest.fn(async () => "csrf-1"),
}))

import {signOut} from "next-auth/react"
import {federatedSignOut} from "@components/security/federatedSignOut"

describe("federatedSignOut", () => {

    const originalFetch = global.fetch
    let navigate

    beforeEach(() => {
        navigate = jest.fn()
        signOut.mockClear()
    })

    afterEach(() => {
        global.fetch = originalFetch
    })

    const answering = (body, ok = true) => {
        global.fetch = jest.fn(async () => ({ok, json: async () => body}))
    }

    const IDP = "https://idp.example.com/logout?id_token_hint=x"

    it("asks the server to federate, before the local session - and its tokens - are gone", async () => {
        answering({url: null})
        signOut.mockImplementationOnce(async () => {
            expect(global.fetch).toHaveBeenCalled()
        })

        await federatedSignOut({returnTo: "/mobile"}, {navigate})

        expect(global.fetch).toHaveBeenCalledWith("/api/auth/federated-signout", expect.objectContaining({
            method: "POST",
            body: JSON.stringify({returnTo: "/mobile"}),
        }))
        expect(signOut).toHaveBeenCalled()
    })

    it("signs out locally to the home of the UI when the server has done it all (back-channel)", async () => {
        answering({url: null})

        await federatedSignOut({returnTo: "/mobile"}, {navigate})

        expect(signOut).toHaveBeenCalledWith({callbackUrl: "/mobile"})
        expect(navigate).not.toHaveBeenCalled()
    })

    it("signs out locally, then sends the browser to the provider (front-channel)", async () => {
        answering({url: IDP})

        await federatedSignOut({returnTo: "/mobile"}, {navigate})

        const [url, opts] = global.fetch.mock.calls[1]
        expect(url).toBe("/api/auth/signout")
        expect(opts.method).toBe("POST")
        const body = new URLSearchParams(opts.body)
        expect(body.get("csrfToken")).toBe("csrf-1")
        expect(body.get("callbackUrl")).toBe("/mobile")
        expect(navigate).toHaveBeenCalledWith(IDP)
        expect(global.fetch.mock.invocationCallOrder[1]).toBeLessThan(navigate.mock.invocationCallOrder[0])
    })

    it("does not tell the page it is signed out before leaving for the provider", async () => {
        // `signOut({redirect: false})` refreshes the session of the page, and `AuthProvider` then
        // calls `signIn()` with the current page as the callback - a navigation which overtakes
        // the one to the provider, and brings the user back to where they signed out
        answering({url: IDP})

        await federatedSignOut({returnTo: "/mobile"}, {navigate})

        expect(signOut).not.toHaveBeenCalled()
    })

    it("falls back on the plain local sign-out when the local sign-out request fails", async () => {
        global.fetch = jest.fn()
            .mockResolvedValueOnce({ok: true, json: async () => ({url: IDP})})
            .mockResolvedValueOnce({ok: false, json: async () => ({})})

        await federatedSignOut({returnTo: "/"}, {navigate})

        expect(signOut).toHaveBeenCalledWith({callbackUrl: "/"})
        expect(navigate).not.toHaveBeenCalled()
    })

    it("still signs out locally when the server fails", async () => {
        answering({}, false)

        await federatedSignOut({returnTo: "/"}, {navigate})

        expect(signOut).toHaveBeenCalledWith({callbackUrl: "/"})
        expect(navigate).not.toHaveBeenCalled()
    })

    it("still signs out locally when the server cannot be reached", async () => {
        global.fetch = jest.fn(async () => {
            throw new TypeError("Failed to fetch")
        })

        await federatedSignOut({returnTo: "/"}, {navigate})

        expect(signOut).toHaveBeenCalledWith({callbackUrl: "/"})
    })
})
