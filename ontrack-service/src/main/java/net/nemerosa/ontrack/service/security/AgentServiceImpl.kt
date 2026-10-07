package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.exceptions.AccountNameAlreadyDefinedException
import net.nemerosa.ontrack.model.exceptions.AccountNotFoundException
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Token
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.repository.AccountRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AgentServiceImpl(
    private val accountRepository: AccountRepository,
    private val securityService: SecurityService,
    private val tokensService: TokensService,
) : AgentService {

    companion object {
        private const val MAX_DISPLAY_NAME = 100
        private const val MAX_TOOL = 40
        private const val MAX_DESCRIPTION = 500
    }

    override fun registerAgent(input: AgentRegistrationInput): Account {
        val current = currentAccount()
        val ownerEmail = input.owner
        val owner = if (ownerEmail.isNullOrBlank() || ownerEmail == current.email) {
            current
        } else {
            // Only an administrator registers an agent for somebody else
            securityService.checkGlobalFunction(AccountManagement::class.java)
            accountRepository.findAccountByName(ownerEmail)
                ?: throw AgentOwnerNotFoundException(ownerEmail)
        }
        checkHumanOwner(owner)
        // Validation
        if (!AgentIdentifiers.isValidSlug(input.slug)) {
            throw AgentSlugInvalidException(input.slug)
        }
        validate(input.displayName, input.tool, input.description)
        // Creation, with no group nor ACL
        val agent = Account.agent(
            slug = input.slug,
            displayName = input.displayName.trim(),
            owner = owner,
            tool = input.tool.trim(),
            description = input.description?.trim()?.takeIf { it.isNotBlank() },
        )
        if (accountRepository.findAccountByName(agent.email) != null) {
            throw AgentSlugAlreadyTakenException(input.slug)
        }
        val saved = try {
            accountRepository.newAccount(agent)
        } catch (_: AccountNameAlreadyDefinedException) {
            throw AgentSlugAlreadyTakenException(input.slug)
        }
        return accountRepository.getAccount(saved.id)
    }

    override fun updateAgent(agentId: ID, input: AgentUpdateInput): Account {
        val agent = getManagedAgent(agentId)
        validate(input.displayName, input.tool, input.description)
        accountRepository.saveAccount(
            agent.copy(
                fullName = input.displayName.trim(),
                agentTool = input.tool.trim(),
                agentDescription = input.description?.trim()?.takeIf { it.isNotBlank() },
            )
        )
        return accountRepository.getAccount(agentId)
    }

    override fun transferAgent(agentId: ID, owner: String): Account {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        val agent = loadAgent(agentId)
        val newOwner = accountRepository.findAccountByName(owner)
            ?: throw AgentOwnerNotFoundException(owner)
        checkHumanOwner(newOwner)
        accountRepository.setOwner(agent.id, newOwner.id)
        return accountRepository.getAccount(agentId)
    }

    override fun deleteAgent(agentId: ID): Ack {
        val agent = getManagedAgent(agentId)
        return accountRepository.deleteAccount(agent.id)
    }

    override fun generateAgentToken(agentId: ID, name: String): Token {
        val agent = getManagedAgent(agentId)
        if (name.isBlank()) {
            throw AgentInputException("The name of the token is required.")
        }
        return securityService.asAdmin {
            if (tokensService.getTokens(agent).any { it.name == name }) {
                throw TokenGenerationNameAlreadyExistsException(name)
            }
            // Usual validity rules of the instance
            tokensService.generateToken(agent.id(), TokenOptions(name = name))
        }
    }

    override fun revokeAgentToken(agentId: ID, name: String) {
        val agent = getManagedAgent(agentId)
        securityService.asAdmin {
            tokensService.revokeToken(agent.id(), name)
        }
    }

    override fun revokeAllAgentTokens(agentId: ID) {
        val agent = getManagedAgent(agentId)
        securityService.asAdmin {
            tokensService.revokeAllTokens(agent.id())
        }
    }

    override fun getAgents(owner: String?): List<Account> {
        val current = securityService.currentUser?.account ?: return emptyList()
        return if (isAdmin()) {
            if (owner.isNullOrBlank()) {
                accountRepository.findAgents()
            } else {
                accountRepository.findAccountByName(owner)
                    ?.let { accountRepository.findAgents(it.id) }
                    ?: emptyList()
            }
        } else if (owner.isNullOrBlank() || owner == current.email) {
            accountRepository.findAgents(current.id)
        } else {
            emptyList()
        }
    }

    override fun findAgent(agentId: ID): Account? {
        val agent = try {
            accountRepository.getAccount(agentId)
        } catch (_: AccountNotFoundException) {
            null
        }
        return agent?.takeIf { it.isAgent && canManage(it) }
    }

    override fun getAgentsOwnedBy(account: Account): List<Account> {
        val current = securityService.currentUser?.account ?: return emptyList()
        return if (isAdmin() || current.id == account.id) {
            accountRepository.findAgents(account.id)
        } else {
            emptyList()
        }
    }

    /**
     * Account of the current user, refused when not authenticated.
     */
    private fun currentAccount(): Account {
        securityService.checkAuthenticated()
        val account = securityService.currentUser?.account
            ?: throw AccessDeniedException("Authentication is required.")
        // Reloading the account to get its kind
        return if (account.id.isSet) {
            accountRepository.getAccount(account.id)
        } else {
            account
        }
    }

    private fun isAdmin() = securityService.isGlobalFunctionGranted(AccountManagement::class.java)

    private fun canManage(agent: Account): Boolean =
        isAdmin() || (securityService.currentUser?.account?.id?.let { it == agent.owner?.id } ?: false)

    private fun loadAgent(agentId: ID): Account {
        val account = try {
            accountRepository.getAccount(agentId)
        } catch (_: AccountNotFoundException) {
            throw AgentNotFoundException(agentId.value)
        }
        if (!account.isAgent) {
            throw AgentNotFoundException(agentId.value)
        }
        return account
    }

    /**
     * Loads an agent the current user can manage: its owner, or an administrator.
     */
    private fun getManagedAgent(agentId: ID): Account {
        val agent = loadAgent(agentId)
        if (!canManage(agent)) {
            throw AccessDeniedException("Only the owner of an agent or an administrator can manage it.")
        }
        return agent
    }

    private fun checkHumanOwner(owner: Account) {
        if (owner.kind != AccountKind.HUMAN) {
            throw AgentOwnerNotHumanException(owner.email)
        }
    }

    private fun validate(displayName: String, tool: String, description: String?) {
        if (displayName.isBlank() || displayName.trim().length > MAX_DISPLAY_NAME) {
            throw AgentInputException("The display name of an agent is required, and has at most $MAX_DISPLAY_NAME characters.")
        }
        if (tool.isBlank() || tool.trim().length > MAX_TOOL) {
            throw AgentInputException("The tool of an agent is required, and has at most $MAX_TOOL characters.")
        }
        if (description != null && description.trim().length > MAX_DESCRIPTION) {
            throw AgentInputException("The description of an agent has at most $MAX_DESCRIPTION characters.")
        }
    }
}
