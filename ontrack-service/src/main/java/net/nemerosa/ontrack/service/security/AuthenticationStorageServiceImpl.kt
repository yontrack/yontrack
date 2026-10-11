package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.TransientSecurityContext
import org.springframework.stereotype.Component

@Component
class AuthenticationStorageServiceImpl(
    private val securityService: SecurityService,
    private val authenticationUserService: AuthenticationUserService,
    private val accountService: AccountService,
) : AuthenticationStorageService {

    override fun getAccountId(): String {
        val user = securityService.currentUser
            ?: throw AuthenticationStorageServiceNoAuthException()
        if (user is RunAsAuthenticatedUser) {
            return AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID
        } else if (user is AccountAuthenticatedUser) {
            return user.account.email
        } else {
            throw AuthenticationStorageServiceAuthNotSupportedException(user)
        }
    }

    override fun getActor(): Actor =
        securityService.currentActor
            ?: throw AuthenticationStorageServiceNoAuthException()

    override fun withAccountId(accountId: String, actor: Actor?, code: () -> Unit) {
        val authentication = if (isRunAsAdministrator(accountId, actor)) {
            RunAsAuthenticatedUser.authentication(
                authenticatedUser = null,
                actor = actor?.takeIf { it.isSystem }
                    ?: Actor.system(reason = null),
            )
        } else {
            val account = securityService.asAdmin {
                accountService.findAccountByName(accountId)
            } ?: throw AuthenticationStorageServiceAccountNotFoundException(accountId)
            val user = authenticationUserService.createAuthenticatedUser(account)
            AuthenticatedUserAuthentication(
                authenticatedUser = user,
                authorities = AuthorityUtils.createAuthorityList(SecurityRole.USER.name),
                actor = actor?.takeIf { it.account == account.email }
                    ?: Actor.degraded(account.email, agent = ActorAgent.of(account)),
            )
        }
        val oldSecurityContext = SecurityContextHolder.getContext()
        val securityContext = TransientSecurityContext(authentication)
        try {
            SecurityContextHolder.setContext(securityContext)
            code()
        } finally {
            SecurityContextHolder.setContext(oldSecurityContext)
        }
    }

    /**
     * Is this the ID of the run-as administrator?
     *
     * Before #2065, each Yontrack process had its own random run-as ID, of up to six digits.
     * A message dispatched as the system by such a process (the previous version during a rolling
     * upgrade, or messages still queued at the upgrade) carries it, and is still restored as the
     * system when its actor is the system, or absent (5.5 messages have none). The actor keeps an
     * account whose email happens to be numeric on the account path. To be removed in V7.
     */
    private fun isRunAsAdministrator(accountId: String, actor: Actor?): Boolean =
        accountId == AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID ||
                (accountId.matches(legacyRunAsAdministratorAccountId) && (actor == null || actor.isSystem))

    companion object {
        private val legacyRunAsAdministratorAccountId = Regex("\\d{1,6}")
    }
}
