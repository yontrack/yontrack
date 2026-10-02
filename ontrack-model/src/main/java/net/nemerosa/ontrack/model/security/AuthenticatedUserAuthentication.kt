package net.nemerosa.ontrack.model.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * Authentication of a Yontrack user.
 *
 * @property authenticatedUser Who is authenticated, and their rights
 * @property actor How they got in, as the audit trail records it
 */
class AuthenticatedUserAuthentication(
    val authenticatedUser: AuthenticatedUser,
    authorities: Collection<GrantedAuthority>,
    val actor: Actor,
) : AbstractAuthenticationToken(authorities) {

    override fun getCredentials(): Any = ""

    override fun getPrincipal(): Any = authenticatedUser

    override fun isAuthenticated(): Boolean = true
}
