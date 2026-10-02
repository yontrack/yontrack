package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.NoAuthTest
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@NoAuthTest
class AuthenticationStorageServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var authenticationStorageService: AuthenticationStorageService

    @Test
    fun `Storing and restoring the security context when there is no authentication`() {
        assertFailsWith<AuthenticationStorageServiceNoAuthException> {
            authenticationStorageService.getAccountId()
        }
        assertFailsWith<AuthenticationStorageServiceNoAuthException> {
            authenticationStorageService.getActor()
        }
    }

    @Test
    fun `Storing and restoring the security context for an account`() {
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            val email = securityService.currentUser?.account?.email
            assertNotNull(email, "There is a current user with an email")
            val accountId = authenticationStorageService.getAccountId()
            assertTrue(accountId.isNotBlank())
            val actor = authenticationStorageService.getActor()
            authenticationStorageService.withAccountId(accountId, actor) {
                assertIs<AccountAuthenticatedUser>(securityService.currentUser) {
                    assertEquals(email, it.account.email)
                }
            }
        }
    }

    @Test
    fun `Storing and restoring the actor of an account`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val (accountId, storedActor) = asActor(account, actor) {
            authenticationStorageService.getAccountId() to authenticationStorageService.getActor()
        }
        assertEquals(actor, storedActor)
        authenticationStorageService.withAccountId(accountId, storedActor) {
            assertEquals(actor, securityService.currentActor)
        }
    }

    @Test
    fun `Restoring an account without its actor degrades to the account acting as system`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        authenticationStorageService.withAccountId(account.email, null) {
            assertEquals(
                Actor(account = account.email, via = ActorVia.SYSTEM),
                securityService.currentActor,
            )
        }
    }

    @Test
    fun `Restoring an account with the actor of another account degrades to the account acting as system`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val other = Actor(account = "other@yontrack.test", via = ActorVia.TOKEN, tokenName = "pipeline")
        authenticationStorageService.withAccountId(account.email, other) {
            assertEquals(
                Actor(account = account.email, via = ActorVia.SYSTEM),
                securityService.currentActor,
            )
        }
    }

    @Test
    fun `Storing and restoring the security context for a run-as admin`() {
        securityService.asAdmin {
            val accountId = authenticationStorageService.getAccountId()
            assertEquals(AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID, accountId)
            authenticationStorageService.withAccountId(accountId, authenticationStorageService.getActor()) {
                assertIs<RunAsAuthenticatedUser>(securityService.currentUser)
            }
        }
    }

    @Test
    fun `Storing and restoring the actor of a run-as admin on behalf of an account`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val (accountId, storedActor) = asActor(account, actor) {
            securityService.asAdmin("auto-versioning") {
                authenticationStorageService.getAccountId() to authenticationStorageService.getActor()
            }
        }
        val expected = Actor.system(reason = "auto-versioning", onBehalfOf = actor)
        assertEquals(expected, storedActor)
        authenticationStorageService.withAccountId(accountId, storedActor) {
            assertIs<RunAsAuthenticatedUser>(securityService.currentUser)
            assertEquals(expected, securityService.currentActor)
        }
    }

    @Test
    fun `Restoring a run-as admin without its actor degrades to the system`() {
        authenticationStorageService.withAccountId(AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID, null) {
            assertEquals(Actor.system(reason = null), securityService.currentActor)
        }
    }

}
