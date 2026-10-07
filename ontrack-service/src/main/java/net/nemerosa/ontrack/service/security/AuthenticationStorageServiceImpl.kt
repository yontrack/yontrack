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
        val authentication = if (accountId == AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID) {
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
}