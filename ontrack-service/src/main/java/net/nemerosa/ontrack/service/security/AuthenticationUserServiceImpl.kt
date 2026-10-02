package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthenticationUserServiceImpl(
    private val accountACLService: AccountACLService,
    private val accountGroupService: AccountGroupService,
) : AuthenticationUserService {

    override fun asUser(account: Account, actor: Actor) {
        val enrichedAuth = AuthenticatedUserAuthentication(
            authenticatedUser = createAuthenticatedUser(account),
            authorities = AuthorityUtils.createAuthorityList(SecurityRole.USER.name),
            actor = actor,
        )
        SecurityContextHolder.getContext().authentication = enrichedAuth
    }


    override fun createAuthenticatedUser(account: Account): AccountAuthenticatedUser {
        val (assignedGroups, mappedGroups, idpGroups) = accountGroupService.getAccountGroups(account)
        val groups = (assignedGroups + mappedGroups).distinctBy { it.id() }
        return AccountAuthenticatedUser(
            account = account,
            authorisations = accountACLService.getAuthorizations(account),
            groups = accountACLService.getAuthorizedGroups(groups),
            assignedGroups = assignedGroups,
            mappedGroups = mappedGroups,
            idpGroups = idpGroups,
        )
    }
}