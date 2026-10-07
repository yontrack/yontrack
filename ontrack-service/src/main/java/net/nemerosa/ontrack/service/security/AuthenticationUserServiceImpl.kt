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
        // An agent has no rights of its own: no group, no ACL, and none of the rights granted to it
        // directly. Its grants are computed for its owner, and narrowed by the agent policy in
        // AccountAuthenticatedUser - nothing to keep in sync when the owner is demoted or deleted.
        if (account.isAgent) {
            val owner = account.owner
            val ownerUser = owner?.let { createHumanAuthenticatedUser(it) }
            return AccountAuthenticatedUser(
                account = account,
                authorisations = ownerUser?.authorisations ?: Authorisations.none(),
                groups = ownerUser?.groups ?: emptyList(),
                assignedGroups = ownerUser?.assignedGroups ?: emptyList(),
                mappedGroups = ownerUser?.mappedGroups ?: emptyList(),
                idpGroups = ownerUser?.idpGroups ?: emptyList(),
            )
        }
        return createHumanAuthenticatedUser(account)
    }

    /**
     * The roles, groups, IdP groups and project ACLs of a person.
     */
    private fun createHumanAuthenticatedUser(account: Account): AccountAuthenticatedUser {
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
