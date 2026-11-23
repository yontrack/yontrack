package net.nemerosa.ontrack.extension.av.event

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.ui.controller.UILocations
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoVersioningEventsFactoryImplTest {

    private val branch = Branch.of(Project.of(nd("target", "")).withId(ID.of(1)), nd("main", "")).withId(ID.of(2))

    private val order = branch.createOrder(sourceProject = "source")

    private val uiLocations = mockk<UILocations> {
        every { page(any()) } answers { "https://yontrack${firstArg<String>()}" }
    }

    private val factory = AutoVersioningEventsFactoryImpl(
        structureService = mockk(),
        uiLocations = uiLocations,
    )

    private val auditLink = "https://yontrack/extension/auto-versioning/audit/detail/${order.uuid}"

    @Test
    fun `The link of a post-processing failure is the link of the event`() {
        val event = factory.error(
            order = order,
            message = "Post processing error",
            error = MockPostProcessingFailureException("Remote job failed", "https://job.link"),
        )
        assertEquals(AutoVersioningEvents.AUTO_VERSIONING_POST_PROCESSING_ERROR, event.eventType)
        assertEquals("https://job.link", event.getValue("LINK"))
    }

    @Test
    fun `A post-processing failure without link links to the audit entry`() {
        listOf("", " ").forEach { link ->
            val event = factory.error(
                order = order,
                message = "Kubernetes post processing job yontrack/job failed in the `run` step with exit code 2.",
                error = MockPostProcessingFailureException("Job failed", link),
            )
            assertEquals(AutoVersioningEvents.AUTO_VERSIONING_POST_PROCESSING_ERROR, event.eventType)
            assertEquals(auditLink, event.getValue("LINK"))
            assertEquals(auditLink, event.getValue("AUDIT_LINK"))
        }
    }

}
