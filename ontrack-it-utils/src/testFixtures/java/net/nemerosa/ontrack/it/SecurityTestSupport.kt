package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.AccountGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.stereotype.Component
import java.util.*

@Component
class SecurityTestSupport(
    private val securityService: SecurityService,
    private val accountService: AccountService,
    private val tokensService: TokensService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val accountGroupRepository: AccountGroupRepository,
    private val accountRepository: AccountRepository,
    private val authenticationUserService: AuthenticationUserService,
) {

    /**
     * Authenticates the [user] in a new security context.
     *
     * @param user User to authenticate
     * @param securityRole Role of the authentication
     * @param actor How the user got in - by default, through the UI
     * @return The previous security context
     */
    fun setupSecurityContext(
        user: AuthenticatedUser,
        securityRole: SecurityRole = SecurityRole.USER,
        actor: Actor = Actor(account = user.name, via = ActorVia.UI),
    ): SecurityContext {
        val authentication = AuthenticatedUserAuthentication(
            authenticatedUser = user,
            authorities = AuthorityUtils.createAuthorityList(securityRole.name),
            actor = actor,
        )
        val oldContext = SecurityContextHolder.getContext()
        val context: SecurityContext = SecurityContextImpl(authentication)
        SecurityContextHolder.setContext(context)
        return oldContext
    }

    fun createAdminAccount(): Account {
        val name = uid("admin-")
        val groupName = ontrackConfigProperties.security.authorization.admin.groupName
        val group = accountGroupRepository.findAccountGroupByName(groupName)
            ?: error("Cannot find group with name $groupName")
        val account = accountRepository.newAccount(
            Account(
                id = ID.NONE,
                fullName = name,
                email = "$name@ontrack.local",
                role = SecurityRole.USER,
            )
        )
        // Account groups
        accountGroupRepository.linkAccountToGroups(account.id(), listOf(group.id()))
        // OK
        return account
    }

    fun createOntrackAuthenticatedUser(account: Account): AuthenticatedUser =
        authenticationUserService.createAuthenticatedUser(account)

    fun provisionToken(): String {
        val accountName = securityService.currentUser?.name
            ?: error("No account found in the context")
        return securityService.asAdmin {
            val account = accountService.findAccountByName(accountName)
                ?: error("Account not found: $accountName")
            val result = tokensService.generateToken(
                accountId = account.id(),
                options = TokenOptions(
                    name = UUID.randomUUID().toString(),
                )
            )
            result.value
        }
    }

}