package net.nemerosa.ontrack.extension.audittrail.security

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.security.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditTrailAuthorizationContributorTest {

    private val securityService = mockk<SecurityService>()
    private val contributor = AuditTrailAuthorizationContributor(securityService)

    @Test
    fun `Global authorizations only`() {
        assertTrue(contributor.appliesTo(GlobalAuthorizationContext))
        assertFalse(contributor.appliesTo("Something else"))
    }

    @Test
    fun `Status viewable with the global settings`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns true
        assertEquals(
            listOf(Authorization("auditTrailStatus", Authorization.VIEW, true)),
            contributor.getAuthorizations(mockk(), GlobalAuthorizationContext)
        )
    }

    @Test
    fun `Status not viewable without the global settings`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns false
        assertEquals(
            listOf(Authorization("auditTrailStatus", Authorization.VIEW, false)),
            contributor.getAuthorizations(mockk(), GlobalAuthorizationContext)
        )
    }
}
