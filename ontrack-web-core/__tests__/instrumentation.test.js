/**
 * @jest-environment node
 */
/*
 * The startup hook: the federated sign-out notice is logged once, when the server starts (#1734).
 */
import {register} from "../instrumentation"

describe("register", () => {

    const originalEnv = process.env

    beforeEach(() => {
        jest.spyOn(console, "info").mockImplementation(() => {})
    })

    afterEach(() => {
        process.env = originalEnv
        jest.restoreAllMocks()
    })

    const registerWith = async (env) => {
        process.env = {...originalEnv, NEXT_RUNTIME: "nodejs", NEXTAUTH_FEDERATED_SIGNOUT: undefined, ...env}
        await register()
    }

    it("logs the federated sign-out notice for an oidc install without the switch", async () => {
        await registerWith({NEXTAUTH_PROVIDER: "oidc", NEXTAUTH_URL: "https://yontrack.example.com"})

        expect(console.info).toHaveBeenCalledWith(expect.stringContaining(
            "https://yontrack.example.com/api/auth/signout-complete"
        ))
    })

    it("logs no notice once the switch is set", async () => {
        await registerWith({NEXTAUTH_PROVIDER: "oidc", NEXTAUTH_FEDERATED_SIGNOUT: "true"})

        expect(console.info).not.toHaveBeenCalled()
    })

    it("logs no notice for a keycloak install", async () => {
        await registerWith({NEXTAUTH_PROVIDER: undefined})

        expect(console.info).not.toHaveBeenCalled()
    })

    it("does nothing on the edge runtime", async () => {
        await registerWith({NEXT_RUNTIME: "edge", NEXTAUTH_PROVIDER: "oidc"})

        expect(console.info).not.toHaveBeenCalled()
    })
})
