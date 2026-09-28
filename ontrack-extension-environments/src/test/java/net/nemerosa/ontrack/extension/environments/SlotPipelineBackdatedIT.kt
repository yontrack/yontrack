package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.events.EnvironmentsEvents
import net.nemerosa.ontrack.extension.environments.service.SlotPipelineDateTimeException
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.getPipelineById
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.*

/**
 * Backdated slot pipelines (#1895): every pipeline action takes an optional date/time, stored on the
 * pipeline and in its history, and constrained so that the history stays one.
 */
@QueueNoAsync
@AsAdminTest
class SlotPipelineBackdatedIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    /**
     * Reference time for a test, ten days ago, at a round second so that storage keeps it as is.
     */
    private val ref: LocalDateTime = Time.now.minusDays(10).withNano(0)

    /**
     * A slot and a build created at [ref].
     */
    private fun withBackdatedBuild(code: (slot: Slot, build: Build) -> Unit) {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                val build = build().apply { updateBuildSignature(time = ref) }
                code(slot, structureService.getBuild(build.id))
            }
        }
    }

    private fun newBuild(slot: Slot, time: LocalDateTime = ref): Build {
        val build = slot.project.branch().build().apply { updateBuildSignature(time = time) }
        return structureService.getBuild(build.id)
    }

    // ---------------------------------------------------------------------------------------------
    // Storage
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Backdated start is stored on the pipeline and its first change`() {
        withBackdatedBuild { slot, build ->
            val start = ref.plusHours(1)
            val pipeline = slotService.startPipeline(slot, build, dateTime = start)
            assertEquals(start, pipeline.start)
            assertNull(pipeline.end)
            val change = slotService.getPipelineChanges(pipeline).single()
            assertEquals(start, change.timestamp)
            assertEquals(SlotPipelineStatus.CANDIDATE, change.status)
        }
    }

    @Test
    fun `Backdated run and finish are stored in the history and the end`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            assertTrue(slotService.finishDeployment(pipeline.id, dateTime = ref.plusHours(3)).ok)

            val done = slotService.getPipelineById(pipeline.id)
            assertEquals(SlotPipelineStatus.DONE, done.status)
            assertEquals(ref.plusHours(1), done.start)
            assertEquals(ref.plusHours(3), done.end)

            val changes = slotService.getPipelineChanges(done).associate { it.status to it.timestamp }
            assertEquals(ref.plusHours(1), changes[SlotPipelineStatus.CANDIDATE])
            assertEquals(ref.plusHours(2), changes[SlotPipelineStatus.RUNNING])
            assertEquals(ref.plusHours(3), changes[SlotPipelineStatus.DONE])
        }
    }

    @Test
    fun `Backdated failure is stored in the history and the end`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            assertTrue(slotService.failPipeline(pipeline.id, message = "Boom", dateTime = ref.plusHours(3)).ok)

            val failed = slotService.getPipelineById(pipeline.id)
            assertEquals(SlotPipelineStatus.FAILED, failed.status)
            assertEquals(ref.plusHours(3), failed.end)
            val change = slotService.getPipelineChanges(failed).first()
            assertEquals(SlotPipelineStatus.FAILED, change.status)
            assertEquals(ref.plusHours(3), change.timestamp)
        }
    }

    @Test
    fun `Backdated cancellation is stored in the history and the end`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            slotService.cancelPipeline(pipeline, "Not needed", dateTime = ref.plusHours(2))

            val cancelled = slotService.getPipelineById(pipeline.id)
            assertEquals(SlotPipelineStatus.CANCELLED, cancelled.status)
            assertEquals(ref.plusHours(2), cancelled.end)
            val change = slotService.getPipelineChanges(cancelled).first()
            assertEquals(SlotPipelineStatus.CANCELLED, change.status)
            assertEquals(ref.plusHours(2), change.timestamp)
        }
    }

    @Test
    fun `Backdated forced deployment is done at the given time`() {
        withBackdatedBuild { slot, build ->
            val start = ref.plusHours(1)
            val pipeline = slotService.startPipeline(slot, build, forceDone = true, dateTime = start)
            assertEquals(SlotPipelineStatus.DONE, pipeline.status)
            assertEquals(start, pipeline.start)
            assertEquals(start, pipeline.end)
            assertTrue(slotService.getPipelineChanges(pipeline).all { it.timestamp == start })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Constraints
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `A start in the future is refused`() {
        withBackdatedBuild { slot, build ->
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.startPipeline(slot, build, dateTime = Time.now.plusHours(1))
            }
            assertNull(slotService.getCurrentPipeline(slot), "No pipeline created")
        }
    }

    @Test
    fun `A finish in the future is refused`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.finishDeployment(pipeline.id, dateTime = Time.now.plusHours(1))
            }
            assertEquals(SlotPipelineStatus.RUNNING, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A start before the creation of the build is refused`() {
        withBackdatedBuild { slot, build ->
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.startPipeline(slot, build, dateTime = ref.minusMinutes(1))
            }
            assertNull(slotService.getCurrentPipeline(slot), "No pipeline created")
        }
    }

    @Test
    fun `A start at the creation of the build is accepted`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref)
            assertEquals(ref, pipeline.start)
        }
    }

    @Test
    fun `A run before the start of the pipeline is refused`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(2))
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(1))
            }
            assertEquals(SlotPipelineStatus.CANDIDATE, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A finish before the run of the pipeline is refused`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(3)).ok)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.finishDeployment(pipeline.id, dateTime = ref.plusHours(2))
            }
            assertEquals(SlotPipelineStatus.RUNNING, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A failure before the run of the pipeline is refused`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(3)).ok)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.failPipeline(pipeline.id, dateTime = ref.plusHours(2))
            }
            assertEquals(SlotPipelineStatus.RUNNING, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A cancellation before the start of the pipeline is refused`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(2))
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.cancelPipeline(pipeline, "Too early", dateTime = ref.plusHours(1))
            }
            assertEquals(SlotPipelineStatus.CANDIDATE, slotService.getPipelineById(pipeline.id).status)
        }
    }

    @Test
    fun `A backdated action on a pipeline changed now is refused`() {
        withBackdatedBuild { slot, build ->
            // Started now, without a date
            val pipeline = slotService.startPipeline(slot, build)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(1))
            }
        }
    }

    @Test
    fun `A start before the start of the latest pipeline of the slot is refused`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(3))
            slotTestSupport.runAndFinishDeployment(first, dateTime = ref.plusHours(4))
            val other = newBuild(slot)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.startPipeline(slot, other, dateTime = ref.plusHours(2))
            }
            assertEquals(first.id, slotService.getCurrentPipeline(slot)?.id)
        }
    }

    @Test
    fun `A start before the start of the latest pipeline is refused even when it was cancelled`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(3))
            slotService.cancelPipeline(first, "Not needed", dateTime = ref.plusHours(4))
            val other = newBuild(slot)
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.startPipeline(slot, other, dateTime = ref.plusHours(2))
            }
        }
    }

    @Test
    fun `A start after the start of the latest pipeline, before its end, is accepted`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            slotTestSupport.runAndFinishDeployment(first, dateTime = ref.plusHours(5))
            // Deployments of a slot may overlap once the first one is over
            val other = newBuild(slot)
            val second = slotService.startPipeline(slot, other, dateTime = ref.plusHours(2))
            assertEquals(ref.plusHours(2), second.start)
            assertEquals(SlotPipelineStatus.DONE, slotService.getPipelineById(first.id).status)
        }
    }

    @Test
    fun `The start of a pipeline in another slot is not a constraint`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(3))
            assertNotNull(first)
            slotTestSupport.withSlot(project = slot.project, qualifier = "other") { otherSlot ->
                val pipeline = slotService.startPipeline(otherSlot, build, dateTime = ref.plusHours(1))
                assertEquals(ref.plusHours(1), pipeline.start)
            }
        }
    }

    @Test
    fun `Constraints are not checked without a date`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                // A build created "in the future" - by a clock ahead of ours
                val build = build().apply { updateBuildSignature(time = Time.now.plusHours(1)) }
                val pipeline = slotService.startPipeline(slot, structureService.getBuild(build.id))
                assertEquals(SlotPipelineStatus.CANDIDATE, pipeline.status)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Auto-cancel
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The auto-cancel of the active pipeline takes the new start time`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(first.id, dryRun = false, dateTime = ref.plusHours(2)).ok)

            val second = slotService.startPipeline(slot, newBuild(slot), dateTime = ref.plusHours(3))
            assertEquals(ref.plusHours(3), second.start)

            val cancelled = slotService.getPipelineById(first.id)
            assertEquals(SlotPipelineStatus.CANCELLED, cancelled.status)
            assertEquals(ref.plusHours(3), cancelled.end)
            val change = slotService.getPipelineChanges(cancelled).first()
            assertEquals(SlotPipelineStatus.CANCELLED, change.status)
            assertEquals(ref.plusHours(3), change.timestamp)
        }
    }

    @Test
    fun `A start which would cancel the active pipeline before its last change is refused`() {
        withBackdatedBuild { slot, build ->
            val first = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(first.id, dryRun = false, dateTime = ref.plusHours(3)).ok)

            // After the start of the latest pipeline, but before it started running
            assertFailsWith<SlotPipelineDateTimeException> {
                slotService.startPipeline(slot, newBuild(slot), dateTime = ref.plusHours(2))
            }
            // Nothing happened
            val unchanged = slotService.getPipelineById(first.id)
            assertEquals(SlotPipelineStatus.RUNNING, unchanged.status)
            assertEquals(first.id, slotService.getCurrentPipeline(slot)?.id)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events & rights
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Events are fired for backdated actions`() {
        withBackdatedBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertEvent(build, EnvironmentsEvents.PIPELINE_CREATION, pipeline)
            assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            assertEvent(build, EnvironmentsEvents.PIPELINE_DEPLOYING, pipeline)
            assertTrue(slotService.finishDeployment(pipeline.id, dateTime = ref.plusHours(3)).ok)
            assertEvent(build, EnvironmentsEvents.PIPELINE_DEPLOYED, pipeline)
        }
    }

    @Test
    fun `Backdated failure and cancellation fire their events`() {
        withBackdatedBuild { slot, build ->
            val failing = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(failing.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            assertTrue(slotService.failPipeline(failing.id, dateTime = ref.plusHours(3)).ok)
            assertEvent(build, EnvironmentsEvents.PIPELINE_FAILED, failing)

            val other = newBuild(slot)
            val cancelled = slotService.startPipeline(slot, other, dateTime = ref.plusHours(4))
            slotService.cancelPipeline(cancelled, "Not needed", dateTime = ref.plusHours(5))
            assertEvent(other, EnvironmentsEvents.PIPELINE_CANCELLED, cancelled)
        }
    }

    @Test
    fun `Backdating needs no more than the right of the action itself`() {
        withBackdatedBuild { slot, build ->
            asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                val pipeline = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
                assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
                assertTrue(slotService.finishDeployment(pipeline.id, dateTime = ref.plusHours(3)).ok)
            }
        }
    }

    private fun assertEvent(build: Build, eventType: EventType, pipeline: SlotPipeline) {
        val event = eventQueryService.getLastEvent(build, eventType)
        assertNotNull(event, "Event ${eventType.id} posted") {
            assertEquals(pipeline.id, it.getValue(EnvironmentsEvents.EVENT_PIPELINE_ID))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GraphQL
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Backdated pipeline using GraphQL`() {
        withBackdatedBuild { slot, build ->
            val pipelineId = run(
                """
                    mutation {
                        startSlotPipeline(input: {
                            slotId: "${slot.id}",
                            buildId: ${build.id},
                            dateTime: "${ref.plusHours(1)}",
                        }) {
                            pipeline { id }
                            errors { message }
                        }
                    }
                """.trimIndent()
            ).let { data ->
                checkGraphQLUserErrors(data, "startSlotPipeline")
                    .path("pipeline").path("id").asText()
            }
            run(
                """
                    mutation {
                        startSlotPipelineDeployment(input: {
                            pipelineId: "$pipelineId",
                            dateTime: "${ref.plusHours(2)}",
                        }) {
                            deploymentStatus { ok }
                            errors { message }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "startSlotPipelineDeployment") { node ->
                    assertTrue(node.path("deploymentStatus").path("ok").asBoolean())
                }
            }
            run(
                """
                    mutation {
                        failSlotPipeline(input: {
                            pipelineId: "$pipelineId",
                            dateTime: "${ref.plusHours(3)}",
                        }) {
                            failStatus { ok }
                            errors { message }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "failSlotPipeline") { node ->
                    assertTrue(node.path("failStatus").path("ok").asBoolean())
                }
            }
            val pipeline = slotService.getPipelineById(pipelineId)
            assertEquals(SlotPipelineStatus.FAILED, pipeline.status)
            assertEquals(ref.plusHours(1), pipeline.start)
            assertEquals(ref.plusHours(3), pipeline.end)
        }
    }

    @Test
    fun `Backdated finish and cancel using GraphQL`() {
        withBackdatedBuild { slot, build ->
            val deployed = slotService.startPipeline(slot, build, dateTime = ref.plusHours(1))
            assertTrue(slotService.runDeployment(deployed.id, dryRun = false, dateTime = ref.plusHours(2)).ok)
            run(
                """
                    mutation {
                        finishSlotPipelineDeployment(input: {
                            pipelineId: "${deployed.id}",
                            forcing: false,
                            dateTime: "${ref.plusHours(3)}",
                        }) {
                            finishStatus { ok }
                            errors { message }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "finishSlotPipelineDeployment") { node ->
                    assertTrue(node.path("finishStatus").path("ok").asBoolean())
                }
            }
            assertEquals(ref.plusHours(3), slotService.getPipelineById(deployed.id).end)

            val cancelled = slotService.startPipeline(slot, newBuild(slot), dateTime = ref.plusHours(4))
            run(
                """
                    mutation {
                        cancelSlotPipeline(input: {
                            pipelineId: "${cancelled.id}",
                            reason: "Not needed",
                            dateTime: "${ref.plusHours(5)}",
                        }) {
                            errors { message }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "cancelSlotPipeline")
            }
            val pipeline = slotService.getPipelineById(cancelled.id)
            assertEquals(SlotPipelineStatus.CANCELLED, pipeline.status)
            assertEquals(ref.plusHours(5), pipeline.end)
        }
    }

    @Test
    fun `A constraint violation is a user error using GraphQL`() {
        withBackdatedBuild { slot, build ->
            run(
                """
                    mutation {
                        startSlotPipeline(input: {
                            slotId: "${slot.id}",
                            buildId: ${build.id},
                            dateTime: "${ref.minusDays(1)}",
                        }) {
                            pipeline { id }
                            errors { message exception }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertUserError(
                    data,
                    "startSlotPipeline",
                    exception = SlotPipelineDateTimeException::class.java.name,
                )
            }
            assertNull(slotService.getCurrentPipeline(slot))
        }
    }

}
