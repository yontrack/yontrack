package net.nemerosa.ontrack.extension.av.processing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningTargetFileService
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEventService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingRegistry
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningProcessingLock
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningDeferredErrorException
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryService
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.BranchFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.NullNode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * A failure of the post-processing of an auto-versioning order sends an error event, like the
 * failures of the other steps of the processing, before being rethrown.
 */
class AutoVersioningProcessingServiceImplPostProcessingErrorTest {

    /**
     * A transient failure, which may be retried automatically.
     */
    private class TransientPostProcessingFailure(message: String) :
        RuntimeException(message), AutoVersioningRetryableException

    private lateinit var eventService: AutoVersioningEventService
    private lateinit var retryService: AutoVersioningRetryService
    private lateinit var postProcessing: PostProcessing<Any>
    private lateinit var service: AutoVersioningProcessingServiceImpl

    private val branch = BranchFixtures.testBranch()

    @BeforeEach
    fun init() {
        val scm = mockk<SCM>(relaxed = true)
        every { scm.getSCMBranch(branch) } returns "main"
        every { scm.repositoryURI } returns "git@example.test:org/repo.git"
        every { scm.repository } returns "org/repo"
        every { scm.createBranch(any(), any()) } returns "commit-id"
        every { scm.download(any(), any(), any()) } returns "version=1.0.0".toByteArray()
        every { scm.mergeBranch(any(), any()) } returns mockk<SCMCommit>(relaxed = true)

        val scmDetector = mockk<SCMDetector>()
        every { scmDetector.getSCM(branch.project) } returns scm

        val targetFileService = mockk<AutoVersioningTargetFileService>(relaxed = true)
        every { targetFileService.readVersion(any(), any()) } returns "1.0.0"

        postProcessing = mockk(relaxed = true)
        every { postProcessing.id } returns POST_PROCESSING_ID
        every { postProcessing.parseAndValidate(any()) } returns Unit

        val registry = mockk<PostProcessingRegistry>()
        every { registry.getPostProcessingById<Any>(POST_PROCESSING_ID) } returns postProcessing

        val metrics = mockk<AutoVersioningMetricsService>(relaxed = true)
        every { metrics.postProcessingTiming(any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }

        val lock = mockk<AutoVersioningProcessingLock>()
        every { lock.withMergeLock(any(), any(), any<() -> Any?>()) } answers {
            thirdArg<() -> Any?>().invoke()
        }

        eventService = mockk(relaxed = true)

        retryService = mockk(relaxed = true)
        every { retryService.getRetryAttempt(any()) } returns 0

        service = AutoVersioningProcessingServiceImpl(
            scmDetector = scmDetector,
            autoVersioningTargetFileService = targetFileService,
            postProcessingRegistry = registry,
            autoVersioningAuditService = mockk(relaxed = true),
            metrics = metrics,
            autoVersioningEventService = eventService,
            autoVersioningCompletionListeners = mutableListOf(),
            autoVersioningTemplatingService = mockk(relaxed = true),
            versionSourceFactory = mockk(relaxed = true),
            structureService = mockk(relaxed = true),
            autoVersioningVersionRuleRegistry = mockk(relaxed = true),
            autoVersioningRetryService = retryService,
            autoVersioningProcessingLock = lock,
        )
    }

    private fun order(pushMode: AutoVersioningPushMode = AutoVersioningPushMode.PR): AutoVersioningOrder =
        branch.createOrder(
            sourceProject = "source-project",
            pushMode = pushMode,
        ).copy(
            postProcessing = POST_PROCESSING_ID,
            postProcessingConfig = NullNode.instance,
        )

    private fun postProcessingFailsWith(failure: Exception) {
        every {
            postProcessing.postProcessing(
                config = any(),
                autoVersioningOrder = any(),
                repositoryURI = any(),
                repository = any(),
                upgradeBranch = any(),
                scm = any(),
                avTemplateRenderer = any(),
                onPostProcessingInfo = any(),
            )
        } throws failure
    }

    @Test
    fun `A failing post-processing sends an error event and the failure is rethrown`() {
        val failure = RuntimeException("Post-processing has failed")
        postProcessingFailsWith(failure)
        val order = order()

        val thrown = assertFailsWith<RuntimeException> {
            service.process(order)
        }

        assertSame(failure, thrown)
        verify(exactly = 1) { eventService.sendError(order, "Post-processing has failed", failure) }
    }

    @Test
    fun `A successful post-processing sends no error event`() {
        val order = order(pushMode = AutoVersioningPushMode.PUSH)

        val outcome = service.process(order)

        assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
        verify(exactly = 0) { eventService.sendError(any(), any(), any()) }
    }

    @Test
    fun `A transient post-processing failure has its error event deferred when the caller handles the retries`() {
        val failure = TransientPostProcessingFailure("Post-processing timed out")
        postProcessingFailsWith(failure)
        val order = order()

        val deferred = assertFailsWith<AutoVersioningDeferredErrorException> {
            service.process(order, automaticRetries = true)
        }

        assertEquals("Post-processing timed out", deferred.notificationMessage)
        assertSame(failure, deferred.cause)
        verify(exactly = 0) { eventService.sendError(any(), any(), any()) }
    }

    @Test
    fun `A transient post-processing failure sends an error event when the caller does not handle the retries`() {
        val failure = TransientPostProcessingFailure("Post-processing timed out")
        postProcessingFailsWith(failure)
        val order = order()

        val thrown = assertFailsWith<TransientPostProcessingFailure> {
            service.process(order)
        }

        assertSame(failure, thrown)
        verify(exactly = 1) { eventService.sendError(order, "Post-processing timed out", failure) }
    }

    @Test
    fun `A non-transient post-processing failure sends an error event even when the caller handles the retries`() {
        val failure = RuntimeException("Post-processing has failed")
        postProcessingFailsWith(failure)
        val order = order()

        val thrown = assertFailsWith<RuntimeException> {
            service.process(order, automaticRetries = true)
        }

        assertSame(failure, thrown)
        verify(exactly = 1) { eventService.sendError(order, "Post-processing has failed", failure) }
    }

    @Test
    fun `The error event of a post-processing failure mentions the automatic retries`() {
        val failure = RuntimeException("Post-processing has failed")
        postProcessingFailsWith(failure)
        val order = order()
        every { retryService.getRetryAttempt(order) } returns 3

        assertFailsWith<RuntimeException> {
            service.process(order, automaticRetries = true)
        }

        verify(exactly = 1) {
            eventService.sendError(order, "Post-processing has failed (failed after 3 automatic retries)", failure)
        }
    }

    @Test
    fun `The error event of a post-processing failure mentions the only automatic retry`() {
        val failure = RuntimeException("Post-processing has failed")
        postProcessingFailsWith(failure)
        val order = order()
        every { retryService.getRetryAttempt(order) } returns 1

        assertFailsWith<RuntimeException> {
            service.process(order, automaticRetries = true)
        }

        verify(exactly = 1) {
            eventService.sendError(order, "Post-processing has failed (failed after 1 automatic retry)", failure)
        }
    }

    companion object {
        private const val POST_PROCESSING_ID = "test-post-processing"
    }
}
