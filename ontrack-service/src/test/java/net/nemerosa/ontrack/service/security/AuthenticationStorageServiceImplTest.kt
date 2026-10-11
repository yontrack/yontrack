package net.nemerosa.ontrack.service.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AuthenticationStorageServiceImplTest {

    private val accountService = mockk<AccountService> {
        every { findAccountByName(any()) } returns null
    }

    private val service = AuthenticationStorageServiceImpl(
        securityService = MockSecurityService(),
        authenticationUserService = mockk(),
        accountService = accountService,
    )

    private fun restored(accountId: String, actor: Actor?): Authentication {
        var authentication: Authentication? = null
        service.withAccountId(accountId, actor) {
            authentication = SecurityContextHolder.getContext().authentication
        }
        return assertNotNull(authentication, "Authentication restored")
    }

    private fun assertRestoredAsSystem(accountId: String, actor: Actor?, expectedActor: Actor) {
        assertIs<AuthenticatedUserAuthentication>(restored(accountId, actor)) {
            assertIs<RunAsAuthenticatedUser>(it.authenticatedUser)
            assertEquals(expectedActor, it.actor)
        }
    }

    /**
     * The run-as administrator ID of a Yontrack process before #2065, random for each process:
     * the previous version during a rolling upgrade, or messages still queued at the upgrade.
     */
    private val legacyRunAsId = "25555"

    @Test
    fun `The run-as administrator is restored as the system`() {
        val actor = Actor.system(reason = "auto-versioning")
        assertRestoredAsSystem(AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID, actor, actor)
    }

    @Test
    fun `The run-as administrator ID is not an email`() {
        assertEquals("<system>", AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID)
    }

    @Test
    fun `A run-as administrator stored by another Yontrack process is restored as the system`() {
        val actor = Actor.system(reason = "auto-versioning")
        assertRestoredAsSystem(legacyRunAsId, actor, actor)
    }

    @Test
    fun `A run-as administrator stored by a Yontrack process without actor is restored as the system`() {
        assertRestoredAsSystem(legacyRunAsId, null, Actor.system(reason = null))
    }

    @Test
    fun `A numeric ID with the actor of an account is looked up as an account`() {
        val actor = Actor(account = legacyRunAsId, via = ActorVia.TOKEN, tokenName = "pipeline")
        assertFailsWith<AuthenticationStorageServiceAccountNotFoundException> {
            restored(legacyRunAsId, actor)
        }
        verify { accountService.findAccountByName(legacyRunAsId) }
    }

    @Test
    fun `An ID longer than a legacy run-as ID is looked up as an account`() {
        assertFailsWith<AuthenticationStorageServiceAccountNotFoundException> {
            restored("1234567", Actor.system(reason = null))
        }
    }

}
