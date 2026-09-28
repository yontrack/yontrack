package net.nemerosa.ontrack.kdsl.acceptance.tests.environments

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

/**
 * Marking a slot pipeline as failed through the API (#1894).
 */
class ACCSlotPipelineFailed : AbstractACCDSLTestSupport() {

    @Test
    fun `Marking a running pipeline as failed`() {
        val application = project { this }
        val branch = application.branch { this }
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        val slot = environment.createSlot(project = application)
        val build = branch.build(name = "1.0.0") { this }

        val pipeline = slot.createPipeline(build = build)
        pipeline.startDeploying()
        pipeline.fail(message = "Smoke tests failed")

        assertEquals(
            SlotPipelineStatus.FAILED,
            ontrack.environments.findPipelineById(pipeline.id)?.status,
        )
    }

    @Test
    fun `A candidate pipeline cannot be marked as failed`() {
        val application = project { this }
        val branch = application.branch { this }
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        val slot = environment.createSlot(project = application)
        val build = branch.build(name = "1.0.0") { this }

        val pipeline = slot.createPipeline(build = build)
        assertFailsWith<IllegalStateException> {
            pipeline.fail()
        }

        assertEquals(
            SlotPipelineStatus.CANDIDATE,
            ontrack.environments.findPipelineById(pipeline.id)?.status,
        )
    }

}
