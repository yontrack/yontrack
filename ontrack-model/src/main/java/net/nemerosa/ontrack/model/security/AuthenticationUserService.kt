package net.nemerosa.ontrack.model.security

interface AuthenticationUserService {

    /**
     * @param account Account to authenticate
     * @param readOnlyTokenName Name of the read-only token it authenticates with, if any
     */
    fun createAuthenticatedUser(account: Account, readOnlyTokenName: String? = null): AccountAuthenticatedUser

    /**
     * Sets the security context to this account.
     *
     * @param account Account to authenticate
     * @param actor How this account got in
     * @param readOnlyTokenName Name of the read-only token it got in with, if any
     */
    fun asUser(account: Account, actor: Actor, readOnlyTokenName: String? = null)

}