package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The events of a slot pipeline are signed by the one who acts on the pipeline, at the moment
 * they do it - not by the creator of the build, at its creation (#2042).
 */
@QueueNoAsync
@AsAdminTest
class SlotPipelineEventSignatureIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    /**
     * Creation time of the build, ten days ago, at a round second so that storage keeps it as is.
     */
    private val buildTime: LocalDateTime = Time.now.minusDays(10).withNano(0)

    /**
     * A slot and a build created at [buildTime] by somebody else than the one deploying it.
     */
    private fun withBackdatedBuild(code: (slot: Slot, build: Build) -> Unit) {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                val build = build().apply { updateBuildSignature(user = BUILD_CREATOR, time = buildTime) }
                code(slot, structureService.getBuild(build.id))
            }
        }
    }

    @Test
    fun `Pipeline events are signed by the caller, now`() {
        withBackdatedBuild { slot, build ->
            val before = Time.now.withNano(0)
            val pipeline = slotService.startPipeline(slot, build)
            assertSignedNow(build, EnvironmentsEvents.PIPELINE_CREATION, before)
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false).ok)
            assertSignedNow(build, EnvironmentsEvents.PIPELINE_DEPLOYING, before)
            assertTrue(slotService.finishDeployment(pipeline.id).ok)
            assertSignedNow(build, EnvironmentsEvents.PIPELINE_DEPLOYED, before)
        }
    }

    @Test
    fun `Failure and cancellation are signed by the caller, now`() {
        withBackdatedBuild { slot, build ->
            val before = Time.now.withNano(0)
            val failing = slotService.startPipeline(slot, build)
            assertTrue(slotService.runDeployment(failing.id, dryRun = false).ok)
            assertTrue(slotService.failPipeline(failing.id).ok)
            assertSignedNow(build, EnvironmentsEvents.PIPELINE_FAILED, before)

            val cancelled = slotService.startPipeline(slot, build)
            slotService.cancelPipeline(cancelled, "Not needed")
            assertSignedNow(build, EnvironmentsEvents.PIPELINE_CANCELLED, before)
        }
    }

    @Test
    fun `Backdated pipeline events are dated at the time of the action`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = buildTime.plusHours(1))
            assertSignedAt(build, EnvironmentsEvents.PIPELINE_CREATION, buildTime.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = buildTime.plusHours(2)).ok)
            assertSignedAt(build, EnvironmentsEvents.PIPELINE_DEPLOYING, buildTime.plusHours(2))
            assertTrue(slotService.finishDeployment(pipeline.id, dateTime = buildTime.plusHours(3)).ok)
            assertSignedAt(build, EnvironmentsEvents.PIPELINE_DEPLOYED, buildTime.plusHours(3))
        }
    }

    @Test
    fun `Backdated failure and cancellation are dated at the time of the action`() {
        withBackdatedBuild { slot, build ->
            val failing = slotService.startPipeline(slot, build, dateTime = buildTime.plusHours(1))
            assertTrue(slotService.runDeployment(failing.id, dryRun = false, dateTime = buildTime.plusHours(2)).ok)
            assertTrue(slotService.failPipeline(failing.id, dateTime = buildTime.plusHours(3)).ok)
            assertSignedAt(build, EnvironmentsEvents.PIPELINE_FAILED, buildTime.plusHours(3))

            val cancelled = slotService.startPipeline(slot, build, dateTime = buildTime.plusHours(4))
            slotService.cancelPipeline(cancelled, "Not needed", dateTime = buildTime.plusHours(5))
            assertSignedAt(build, EnvironmentsEvents.PIPELINE_CANCELLED, buildTime.plusHours(5))
        }
    }

    private fun lastSignature(build: Build, eventType: EventType): Signature {
        val event = eventQueryService.getLastEvent(build, eventType)
        assertNotNull(event, "Event ${eventType.id} posted")
        val signature = event.signature
        assertNotNull(signature, "Event ${eventType.id} is signed")
        assertEquals(
            securityService.currentSignature.user.name,
            signature.user.name,
            "Event ${eventType.id} is signed by the caller, not by the creator of the build"
        )
        return signature
    }

    private fun assertSignedNow(build: Build, eventType: EventType, before: LocalDateTime) {
        val signature = lastSignature(build, eventType)
        assertTrue(
            !signature.time.isBefore(before),
            "Event ${eventType.id} is dated ${signature.time}, not before $before (the build was created at $buildTime)"
        )
    }

    private fun assertSignedAt(build: Build, eventType: EventType, time: LocalDateTime) {
        val signature = lastSignature(build, eventType)
        assertEquals(time, signature.time, "Event ${eventType.id} is dated at the time of the action")
    }

    companion object {
        private const val BUILD_CREATOR = "build-creator"
    }
}
