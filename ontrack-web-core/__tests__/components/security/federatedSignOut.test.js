/*
 * The one helper behind the two "Sign out" controls (#1734): end the provider's session, end the
 * local one whatever happened, and land on the home of the UI the user signed out of.
 */
jest.mock("next-auth/react", () => ({
    signOut: jest.fn(async () => undefined),
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
        answering({url: "https://idp.example.com/logout?id_token_hint=x"})

        await federatedSignOut({returnTo: "/"}, {navigate})

        expect(signOut).toHaveBeenCalledWith({redirect: false})
        expect(navigate).toHaveBeenCalledWith("https://idp.example.com/logout?id_token_hint=x")
        expect(signOut.mock.invocationCallOrder[0]).toBeLessThan(navigate.mock.invocationCallOrder[0])
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
