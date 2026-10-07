package net.nemerosa.ontrack.service.security

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.security.AuthenticatedUser
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.GlobalAuthorizationContext
import net.nemerosa.ontrack.model.security.GlobalFunction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CoreAuthorizationContributorTest {

    private val contributor = CoreAuthorizationContributor()

    private fun eventsAudit(granted: Boolean): Boolean? {
        val user = mockk<AuthenticatedUser>()
        every { user.isGranted(any<Class<out GlobalFunction>>()) } returns false
        every { user.isGranted(EventsAudit::class.java) } returns granted
        return contributor.getAuthorizations(user, GlobalAuthorizationContext)
            .firstOrNull { it.name == CoreAuthorizationContributor.EVENTS && it.action == CoreAuthorizationContributor.AUDIT }
            ?.authorized
    }

    @Test
    fun `Events audit authorization when granted`() {
        assertEquals(true, eventsAudit(granted = true))
    }

    @Test
    fun `Events audit authorization when not granted`() {
        assertEquals(false, eventsAudit(granted = false))
    }

}
