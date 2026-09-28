package net.nemerosa.ontrack.kdsl.acceptance.tests.environments

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.extension.environments.startPipeline
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Backdating slot pipelines through the API (#1895).
 */
class ACCSlotPipelineBackdated : AbstractACCDSLTestSupport() {

    private val ref = LocalDateTime.now(ZoneOffset.UTC).minusDays(10).withNano(0)

    @Test
    fun `Backdated deployment, failure and cancellation`() {
        val application = project { this }
        val branch = application.branch { this }
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        val slot = environment.createSlot(project = application)

        // Deployed
        val build1 = branch.build(name = "1.0.0") { this }.updateCreationTime(ref)
        val deployed = build1.startPipeline(slot, dateTime = ref.plusHours(1))
            .startDeploying(dateTime = ref.plusHours(2))
            .finishDeployment(dateTime = ref.plusHours(3))

        // Failed
        val build2 = branch.build(name = "1.0.1") { this }.updateCreationTime(ref.plusDays(1))
        val failed = slot.createPipeline(build2, dateTime = ref.plusDays(1).plusHours(1))
            .startDeploying(dateTime = ref.plusDays(1).plusHours(2))
            .fail(message = "Smoke tests failed", dateTime = ref.plusDays(1).plusHours(3))

        // Cancelled
        val build3 = branch.build(name = "1.0.2") { this }.updateCreationTime(ref.plusDays(2))
        val cancelled = slot.createPipeline(build3, dateTime = ref.plusDays(2).plusHours(1))
            .cancel(reason = "Not needed", dateTime = ref.plusDays(2).plusHours(2))

        assertNotNull(ontrack.environments.findPipelineById(deployed.id)) {
            assertEquals(SlotPipelineStatus.DONE, it.status)
            assertEquals(ref.plusHours(1), it.start)
            assertEquals(ref.plusHours(3), it.end)
        }
        assertNotNull(ontrack.environments.findPipelineById(failed.id)) {
            assertEquals(SlotPipelineStatus.FAILED, it.status)
            assertEquals(ref.plusDays(1).plusHours(1), it.start)
            assertEquals(ref.plusDays(1).plusHours(3), it.end)
        }
        assertNotNull(ontrack.environments.findPipelineById(cancelled.id)) {
            assertEquals(SlotPipelineStatus.CANCELLED, it.status)
            assertEquals(ref.plusDays(2).plusHours(1), it.start)
            assertEquals(ref.plusDays(2).plusHours(2), it.end)
        }
    }

    @Test
    fun `A start before the creation of the build is refused`() {
        val application = project { this }
        val branch = application.branch { this }
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        val slot = environment.createSlot(project = application)
        val build = branch.build(name = "1.0.0") { this }.updateCreationTime(ref)

        assertFailsWith<GraphQLClientException> {
            slot.createPipeline(build, dateTime = ref.minusHours(1))
        }
    }

}
