package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityServiceUnitTest {

    private lateinit var securityService: SecurityServiceImpl

    private val alice = AccountAuthenticatedUser(
        account = Account.user("Alice", "alice@yontrack.test"),
        authorisations = Authorisations.none(),
        groups = emptyList(),
        assignedGroups = emptyList(),
        mappedGroups = emptyList(),
        idpGroups = emptyList(),
    )

    private val aliceThroughToken = Actor(
        account = "alice@yontrack.test",
        via = ActorVia.TOKEN,
        tokenName = "pipeline",
    )

    @BeforeEach
    fun before() {
        securityService = SecurityServiceImpl()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun after() {
        SecurityContextHolder.clearContext()
    }

    private fun protectedCall(): Boolean {
        securityService.checkGlobalFunction(ProjectCreation::class.java)
        return true
    }

    private fun authenticate(user: AuthenticatedUser, actor: Actor) {
        SecurityContextHolder.setContext(
            SecurityContextImpl(
                AuthenticatedUserAuthentication(
                    authenticatedUser = user,
                    authorities = AuthorityUtils.createAuthorityList(SecurityRole.USER.name),
                    actor = actor,
                )
            )
        )
    }

    @Test
    fun run_as_admin_not_applied() {
        assertFailsWith<AccessDeniedException> {
            protectedCall()
        }
    }

    @Test
    fun run_as_admin() {
        assertTrue(securityService.runAsAdmin { protectedCall() }())
    }

    @Test
    fun `Running as admin for a reason grants the rights`() {
        assertTrue(securityService.asAdmin("auto-promotion") { protectedCall() })
    }

    @Test
    fun `No actor when nobody is authenticated`() {
        assertNull(securityService.currentActor)
    }

    @Test
    fun `Actor of the authentication`() {
        authenticate(alice, aliceThroughToken)
        assertEquals(aliceThroughToken, securityService.currentActor)
    }

    @Test
    fun `Actor of an authentication which does not carry one is the account acting as system`() {
        SecurityContextHolder.setContext(SecurityContextImpl(TestingAuthenticationToken(alice, "", "USER")))
        assertEquals(
            Actor(account = "alice@yontrack.test", via = ActorVia.SYSTEM),
            securityService.currentActor,
        )
    }

    @Test
    fun `Running as admin for a reason acts as the system on behalf of the user`() {
        authenticate(alice, aliceThroughToken)
        val actor = securityService.asAdmin("auto-promotion") { securityService.currentActor }
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, system = "auto-promotion", onBehalfOf = aliceThroughToken),
            actor,
        )
        // Back to the user
        assertEquals(aliceThroughToken, securityService.currentActor)
    }

    @Test
    fun `Running as admin keeps the user and its signature`() {
        authenticate(alice, aliceThroughToken)
        securityService.asAdmin("auto-promotion") {
            assertIs<RunAsAuthenticatedUser>(securityService.currentUser)
            assertEquals("alice@yontrack.test", securityService.currentSignature.user.name)
        }
    }

    @Test
    fun `Running as admin without any user acts as the system`() {
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, system = "stale-branches"),
            securityService.asAdmin("stale-branches") { securityService.currentActor },
        )
    }

    @Test
    fun `Running as admin without reason acts as the system on behalf of the user`() {
        authenticate(alice, aliceThroughToken)
        assertEquals(
            Actor(account = "system", via = ActorVia.SYSTEM, onBehalfOf = aliceThroughToken),
            securityService.asAdmin { securityService.currentActor },
        )
    }

    @Test
    fun `Running as admin within a run as admin chains the reasons`() {
        authenticate(alice, aliceThroughToken)
        val actor = securityService.asAdmin("github-ingestion") {
            securityService.asAdmin {
                securityService.asAdmin("auto-promotion") {
                    securityService.currentActor
                }
            }
        }
        assertEquals(
            Actor.system(
                reason = "auto-promotion",
                onBehalfOf = Actor.system(reason = "github-ingestion", onBehalfOf = aliceThroughToken),
            ),
            actor,
        )
    }

    @Test
    fun `A runner keeps the actor of the context it was created in`() {
        authenticate(alice, aliceThroughToken)
        val runner = securityService.runner { _: Unit -> securityService.currentActor }
        SecurityContextHolder.clearContext()
        assertEquals(aliceThroughToken, runner(Unit))
    }
}
