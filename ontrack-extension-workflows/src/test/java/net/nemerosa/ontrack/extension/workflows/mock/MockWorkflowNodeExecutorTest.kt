package net.nemerosa.ontrack.extension.workflows.mock

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.workflows.WorkflowsExtensionFeature
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceFixtures
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * The mock executor can be enabled outside of the `dev` profile (see
 * `ontrack.config.extension.workflows.mockExecutorEnabled`), so it runs on long-lived servers
 * with concurrent node execution. Two properties it did not have when it was `dev`-only matter
 * there: `waitMs` must not be able to park an executor thread indefinitely, and the recording
 * of the texts must survive concurrent nodes.
 */
class MockWorkflowNodeExecutorTest {

    private val templatingService = mockk<TemplatingService>()
    private val executor = MockWorkflowNodeExecutor(
        workflowsExtensionFeature = WorkflowsExtensionFeature(),
        eventTemplatingService = mockk<EventTemplatingService>(relaxed = true),
        serializableEventService = mockk<SerializableEventService>(relaxed = true),
        templatingService = templatingService,
    )

    @Test
    fun `waiting time under the ceiling is left alone`() {
        assertEquals(0L, MockWorkflowNodeExecutor.clampWaitMs(0L))
        assertEquals(500L, MockWorkflowNodeExecutor.clampWaitMs(500L))
        assertEquals(
            MockWorkflowNodeExecutor.MAX_WAIT_MS,
            MockWorkflowNodeExecutor.clampWaitMs(MockWorkflowNodeExecutor.MAX_WAIT_MS),
        )
    }

    @Test
    fun `waiting time over the ceiling is clamped`() {
        assertEquals(
            MockWorkflowNodeExecutor.MAX_WAIT_MS,
            MockWorkflowNodeExecutor.clampWaitMs(MockWorkflowNodeExecutor.MAX_WAIT_MS + 1),
        )
        assertEquals(
            MockWorkflowNodeExecutor.MAX_WAIT_MS,
            MockWorkflowNodeExecutor.clampWaitMs(Long.MAX_VALUE),
        )
    }

    @Test
    fun `negative waiting time is floored at zero`() {
        assertEquals(0L, MockWorkflowNodeExecutor.clampWaitMs(-1L))
        assertEquals(0L, MockWorkflowNodeExecutor.clampWaitMs(Long.MIN_VALUE))
    }

    @Test
    fun `concurrent executions all get recorded`() {
        every { templatingService.isTemplate(any()) } returns false

        val instance = WorkflowInstanceFixtures.simpleLinear()
        val count = 64
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        try {
            repeat(count) {
                pool.submit {
                    start.await()
                    executor.execute(instance, "start") {}
                    done.countDown()
                }
            }
            start.countDown()
            check(done.await(30, TimeUnit.SECONDS)) { "Executions did not complete in time" }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(count, executor.getTextsByInstanceId(instance.id).size)
    }
}
