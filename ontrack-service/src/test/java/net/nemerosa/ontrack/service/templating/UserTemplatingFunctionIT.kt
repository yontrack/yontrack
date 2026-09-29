package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class UserTemplatingFunctionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var templatingService: TemplatingService

    @Test
    fun `Current user email by default`() {
        asUser {
            val name = securityService.currentUser?.account?.email
            assertEquals(
                "Current user is $name",
                templatingService.render(
                    template = "Current user is ${'$'}{#.user}",
                    context = emptyMap(),
                    renderer = PlainEventRenderer.INSTANCE,
                )
            )
        }
    }

}
