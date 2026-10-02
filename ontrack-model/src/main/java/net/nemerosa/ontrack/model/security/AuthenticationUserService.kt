package net.nemerosa.ontrack.model.security

interface AuthenticationUserService {

    fun createAuthenticatedUser(account: Account): AccountAuthenticatedUser

    /**
     * Sets the security context to this account.
     *
     * @param account Account to authenticate
     * @param actor How this account got in
     */
    fun asUser(account: Account, actor: Actor)

}