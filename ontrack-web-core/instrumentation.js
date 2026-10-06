/**
 * Runs once, when the server starts.
 *
 * The federated sign-out notice (#1734): an `oidc` install which has not set
 * `NEXTAUTH_FEDERATED_SIGNOUT` is told, before its first sign-out lands anyone on the provider's
 * error page, which URL to register there - Yontrack cannot check that it is.
 */
export async function register() {
    if (process.env.NEXT_RUNTIME === "nodejs") {
        const {federatedSignOutStartupNotice} = await import("@/app/api/auth/providerSignOut")
        const notice = federatedSignOutStartupNotice()
        if (notice) {
            console.info(notice)
        }
    }
}
