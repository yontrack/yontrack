package net.nemerosa.ontrack.model.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils

class RunAsAuthenticatedUser private constructor(
    private val authenticatedUser: AuthenticatedUser?,
) : AuthenticatedUser {

    override val account: Account? = authenticatedUser?.account

    override fun isGranted(fn: Class<out GlobalFunction>): Boolean = true

    override fun isGranted(projectId: Int, fn: Class<out ProjectFunction>): Boolean = true

    override fun getName(): String = authenticatedUser?.name ?: "admin"

    override val groups: List<AuthorizedGroup> =
        authenticatedUser?.groups ?: emptyList()

    override val assignedGroups: List<AccountGroup> = authenticatedUser?.assignedGroups ?: emptyList()
    override val mappedGroups: List<AccountGroup> = authenticatedUser?.mappedGroups ?: emptyList()
    override val idpGroups: List<String> = authenticatedUser?.idpGroups ?: emptyList()

    companion object {

        fun runAsUser(authenticatedUser: AuthenticatedUser?) = RunAsAuthenticatedUser(
            authenticatedUser = authenticatedUser
        )

        /**
         * Authentication of the administrator the system runs as.
         *
         * @param authenticatedUser User whose context the system runs from, if any
         * @param actor Actor of the system, see [Actor.runAs]
         */
        fun authentication(authenticatedUser: AuthenticatedUser?, actor: Actor): Authentication {
            return AuthenticatedUserAuthentication(
                authenticatedUser = runAsUser(authenticatedUser),
                authorities = AuthorityUtils.createAuthorityList(SecurityRole.ADMINISTRATOR.name),
                actor = actor,
            )
        }

    }

}