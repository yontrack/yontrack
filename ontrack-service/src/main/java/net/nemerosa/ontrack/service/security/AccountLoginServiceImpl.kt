package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountLoginService
import net.nemerosa.ontrack.model.security.AgentIdentifierRefusedException
import net.nemerosa.ontrack.model.security.AgentIdentifiers
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.repository.AccountIdpGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AccountLoginServiceImpl(
    private val accountRepository: AccountRepository,
    private val accountIdpGroupRepository: AccountIdpGroupRepository,
) : AccountLoginService {

    override fun login(email: String, fullName: String, idpGroups: List<String>): Account {
        // An agent never logs in through the identity provider
        if (AgentIdentifiers.isAgentIdentifier(email)) {
            throw AgentIdentifierRefusedException(email)
        }
        val account = accountRepository.findOrCreateAccount(
            Account(
                id = ID.NONE,
                fullName = fullName,
                email = email,
                role = SecurityRole.USER,
            )
        )
        // Sync IdP groups
        accountIdpGroupRepository.syncGroups(account.id(), idpGroups)
        // OK
        return account
    }

}