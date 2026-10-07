package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.AccountLoginService
import net.nemerosa.ontrack.model.security.AgentIdentifierRefusedException
import net.nemerosa.ontrack.repository.AccountIdpGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccountLoginServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var accountLoginService: AccountLoginService

    @Autowired
    private lateinit var accountIdpGroupRepository: AccountIdpGroupRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Test
    fun `An agent identifier never logs in`() {
        val email = uid("a-").lowercase() + "[agent]"
        assertFailsWith<AgentIdentifierRefusedException> {
            accountLoginService.login(
                email = email,
                fullName = email,
                idpGroups = emptyList(),
            )
        }
        assertNull(accountRepository.findAccountByName(email), "No account provisioned")
    }

    @Test
    fun `Creation of account on new login`() {
        val email = uid("u-") + "@ontrack.local"
        val account = accountLoginService.login(
            email = email,
            fullName = email,
            idpGroups = listOf("testers", "managers")
        )
        val groups = accountIdpGroupRepository.getAccountIdpGroups(account.id())
        assertEquals(
            listOf("managers", "testers"),
            groups
        )
    }

    @Test
    fun `Getting an existing account on login with identical IdP groups`() {
        val account = asAdmin { doCreateAccount() }
        accountIdpGroupRepository.syncGroups(account.id(), listOf("managers", "testers"))
        val loggedAccount = accountLoginService.login(
            email = account.email,
            fullName = account.fullName,
            idpGroups = listOf("managers", "testers")
        )
        assertEquals(account.id(), loggedAccount.id(), "Same account")
        val groups = accountIdpGroupRepository.getAccountIdpGroups(account.id())
        assertEquals(
            listOf("managers", "testers"),
            groups
        )
    }

    @Test
    fun `Getting an existing account on login with different IdP groups`() {
        val account = asAdmin { doCreateAccount() }
        accountIdpGroupRepository.syncGroups(account.id(), listOf("managers", "testers"))
        val loggedAccount = accountLoginService.login(
            email = account.email,
            fullName = account.fullName,
            idpGroups = listOf("managers", "engineers")
        )
        assertEquals(account.id(), loggedAccount.id(), "Same account")
        val groups = accountIdpGroupRepository.getAccountIdpGroups(account.id())
        assertEquals(
            listOf("engineers", "managers"),
            groups
        )
    }

}