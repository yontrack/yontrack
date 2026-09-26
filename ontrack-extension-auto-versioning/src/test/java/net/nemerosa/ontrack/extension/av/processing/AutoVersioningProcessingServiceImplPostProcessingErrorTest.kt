package net.nemerosa.ontrack.extension.av.processing

import com.fasterxml.jackson.databind.node.NullNode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningTargetFileService
import net.nemerosa.ontrack.extension.av.dispatcher.VersionSourceFactory
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEventService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingRegistry
import net.nemerosa.ontrack.extension.av.versionrules.AutoVersioningVersionRuleRegistry
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Regression test for the post-processing failure notification gap introduced by
 * `e9e6b1e9` (#1343): a post-processing failure must still call [AutoVersioningEventService.sendError],
 * not just propagate silently past it.
 */
class AutoVersioningProcessingServiceImplPostProcessingErrorTest {

    @Test
    fun `A post-processing failure sends an error event before being rethrown`() {
        val branch = BranchFixtures.testBranch()
        val order = branch.createOrder(
            sourceProject = "source-project",
        ).copy(
            postProcessing = "mock-post-processing",
            postProcessingConfig = NullNode.instance,
        )

        val scm = mockk<SCM>(relaxed = true)
        every { scm.getSCMBranch(branch) } returns "main"
        every { scm.repositoryURI } returns "git@example.test:org/repo.git"
        every { scm.repository } returns "org/repo"
        every { scm.createBranch(any(), any()) } returns "commit-id"
        every { scm.download(any(), any(), any()) } returns "version=1.0.0".toByteArray()

        val scmDetector = mockk<SCMDetector>()
        every { scmDetector.getSCM(branch.project) } returns scm

        val autoVersioningTargetFileService = mockk<AutoVersioningTargetFileService>(relaxed = true)
        every { autoVersioningTargetFileService.readVersion(any(), any()) } returns "1.0.0"

        // Simulates a GitHub-style failure to launch/await the post-processing workflow,
        // the same shape as the real TimeoutException/503 seen in production
        val postProcessingFailure = RuntimeException("Could not get result in time")
        val postProcessing = mockk<PostProcessing<Any>>()
        every { postProcessing.parseAndValidate(any()) } returns Any()
        every {
            postProcessing.postProcessing(any(), any(), any(), any(), any(), any(), any(), any())
        } throws postProcessingFailure

        val postProcessingRegistry = mockk<PostProcessingRegistry>()
        every { postProcessingRegistry.getPostProcessingById<Any>("mock-post-processing") } returns postProcessing

        val metrics = mockk<AutoVersioningMetricsService>(relaxed = true)
        every { metrics.postProcessingTiming(any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }

        val autoVersioningEventService = mockk<AutoVersioningEventService>(relaxed = true)

        val service = AutoVersioningProcessingServiceImpl(
            scmDetector = scmDetector,
            autoVersioningTargetFileService = autoVersioningTargetFileService,
            postProcessingRegistry = postProcessingRegistry,
            autoVersioningAuditService = mockk<AutoVersioningAuditService>(relaxed = true),
            metrics = metrics,
            autoVersioningEventService = autoVersioningEventService,
            autoVersioningCompletionListeners = mutableListOf(),
            autoVersioningTemplatingService = mockk(relaxed = true),
            versionSourceFactory = mockk<VersionSourceFactory>(relaxed = true),
            structureService = mockk<StructureService>(relaxed = true),
            autoVersioningVersionRuleRegistry = mockk<AutoVersioningVersionRuleRegistry>(relaxed = true),
        )

        assertFailsWith<RuntimeException> {
            service.process(order)
        }

        verify(exactly = 1) {
            autoVersioningEventService.sendError(order, "Could not get result in time", postProcessingFailure)
        }
    }

    @Test
    fun `A successful post-processing does not send any error event`() {
        val branch = BranchFixtures.testBranch()
        val order = branch.createOrder(
            sourceProject = "source-project",
        ).copy(
            postProcessing = "mock-post-processing",
            postProcessingConfig = NullNode.instance,
            pushMode = AutoVersioningPushMode.PUSH,
        )

        val scm = mockk<SCM>(relaxed = true)
        every { scm.getSCMBranch(branch) } returns "main"
        every { scm.repositoryURI } returns "git@example.test:org/repo.git"
        every { scm.repository } returns "org/repo"
        every { scm.createBranch(any(), any()) } returns "commit-id"
        every { scm.download(any(), any(), any()) } returns "version=1.0.0".toByteArray()

        val scmDetector = mockk<SCMDetector>()
        every { scmDetector.getSCM(branch.project) } returns scm

        val autoVersioningTargetFileService = mockk<AutoVersioningTargetFileService>(relaxed = true)
        every { autoVersioningTargetFileService.readVersion(any(), any()) } returns "1.0.0"

        // Same post-processing plugin as the failure test, except this time it succeeds
        val postProcessing = mockk<PostProcessing<Any>>()
        every { postProcessing.parseAndValidate(any()) } returns Any()
        every {
            postProcessing.postProcessing(any(), any(), any(), any(), any(), any(), any(), any())
        } just Runs

        val postProcessingRegistry = mockk<PostProcessingRegistry>()
        every { postProcessingRegistry.getPostProcessingById<Any>("mock-post-processing") } returns postProcessing

        val metrics = mockk<AutoVersioningMetricsService>(relaxed = true)
        every { metrics.postProcessingTiming(any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }

        val autoVersioningEventService = mockk<AutoVersioningEventService>(relaxed = true)

        val service = AutoVersioningProcessingServiceImpl(
            scmDetector = scmDetector,
            autoVersioningTargetFileService = autoVersioningTargetFileService,
            postProcessingRegistry = postProcessingRegistry,
            autoVersioningAuditService = mockk<AutoVersioningAuditService>(relaxed = true),
            metrics = metrics,
            autoVersioningEventService = autoVersioningEventService,
            autoVersioningCompletionListeners = mutableListOf(),
            autoVersioningTemplatingService = mockk(relaxed = true),
            versionSourceFactory = mockk<VersionSourceFactory>(relaxed = true),
            structureService = mockk<StructureService>(relaxed = true),
            autoVersioningVersionRuleRegistry = mockk<AutoVersioningVersionRuleRegistry>(relaxed = true),
        )

        val outcome = service.process(order)

        assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
        verify(exactly = 0) {
            autoVersioningEventService.sendError(any(), any(), any())
        }
    }

}
