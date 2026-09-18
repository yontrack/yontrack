package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a build's environment decoration carries.
 *
 * The decoration answers "where is this build now" - the highest slot per qualifier holding it -
 * and since #1794 it is drawn as a journey chip, which needs the environment's rank and whether it
 * has an icon in order to draw one without a request of its own.
 */
@AsAdminTest
class BuildEnvironmentsDecorationsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var buildEnvironmentsDecorations: BuildEnvironmentsDecorations

    @Test
    fun `a deployed build carries everything its chip needs to be drawn`() {
        slotTestSupport.withSlot { slot ->
            val pipeline = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val decorations = buildEnvironmentsDecorations.getDecorations(pipeline.build)
            assertEquals(1, decorations.size, "One decoration for the build")
            val data = decorations.first().data ?: error("No data on the decoration")
            assertEquals(1, data.size, "One environment for the build")
            val stub = data.first()
            assertEquals(slot.environment.id, stub.environmentId)
            assertEquals(slot.environment.name, stub.environmentName)
            // The two fields the chip's icon is drawn from, rather than being fetched per chip.
            assertEquals(slot.environment.order, stub.environmentOrder)
            assertEquals(slot.environment.image, stub.environmentImage)
            assertEquals(slot.id, stub.slotId)
            assertEquals(slot.qualifier, stub.qualifier)
            assertEquals(pipeline.id, stub.pipelineId)
        }
    }

    @Test
    fun `a build which never reached a slot has no decoration`() {
        slotTestSupport.withSlot { slot ->
            // Started, not finished: the build is on its way, not there.
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            assertTrue(
                buildEnvironmentsDecorations.getDecorations(pipeline.build).isEmpty(),
                "No decoration for a build which is not deployed anywhere",
            )
        }
    }
}
