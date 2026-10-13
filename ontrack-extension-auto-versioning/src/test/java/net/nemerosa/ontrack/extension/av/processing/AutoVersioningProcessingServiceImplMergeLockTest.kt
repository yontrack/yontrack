package net.nemerosa.ontrack.extension.av.processing

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningTargetFileService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningProcessingLock
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.BranchFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The direct push of an auto-versioning order merges into the target branch under the merge lock
 * of this branch, so that two orders with different paths — processed concurrently — are merged
 * one after the other.
 */
class AutoVersioningProcessingServiceImplMergeLockTest {

    @Test
    fun `Two orders on the same target branch with different paths are merged under the same lock`() {
        val branch = BranchFixtures.testBranch()
        val orders = listOf("gradle.properties", "other/gradle.properties").map { path ->
            branch.createOrder(
                sourceProject = "source-project",
                targetPaths = listOf(path),
            ).copy(
                postProcessing = null,
                pushMode = AutoVersioningPushMode.PUSH,
            )
        }

        val lockKeys = mutableListOf<Pair<String, String>>()
        var locked = false
        val lock = mockk<AutoVersioningProcessingLock>()
        every { lock.withMergeLock(any(), any(), any<() -> Any?>()) } answers {
            lockKeys += firstArg<String>() to secondArg<String>()
            locked = true
            try {
                thirdArg<() -> Any?>().invoke()
            } finally {
                locked = false
            }
        }

        val merges = mutableListOf<Boolean>()
        val scm = mockk<SCM>(relaxed = true)
        every { scm.getSCMBranch(branch) } returns "main"
        every { scm.repositoryURI } returns "git@example.test:org/repo.git"
        every { scm.repository } returns "org/repo"
        every { scm.createBranch(any(), any()) } returns "commit-id"
        every { scm.download(any(), any(), any()) } returns "version=1.0.0".toByteArray()
        every { scm.mergeBranch(any(), any()) } answers {
            merges += locked
            mockk<SCMCommit>(relaxed = true)
        }

        val scmDetector = mockk<SCMDetector>()
        every { scmDetector.getSCM(branch.project) } returns scm

        val autoVersioningTargetFileService = mockk<AutoVersioningTargetFileService>(relaxed = true)
        every { autoVersioningTargetFileService.readVersion(any(), any()) } returns "1.0.0"

        val service = AutoVersioningProcessingServiceImpl(
            scmDetector = scmDetector,
            autoVersioningTargetFileService = autoVersioningTargetFileService,
            postProcessingRegistry = mockk(relaxed = true),
            autoVersioningAuditService = mockk(relaxed = true),
            metrics = mockk<AutoVersioningMetricsService>(relaxed = true),
            autoVersioningEventService = mockk(relaxed = true),
            autoVersioningCompletionListeners = mutableListOf(),
            autoVersioningTemplatingService = mockk(relaxed = true),
            versionSourceFactory = mockk(relaxed = true),
            structureService = mockk(relaxed = true),
            autoVersioningVersionRuleRegistry = mockk(relaxed = true),
            autoVersioningRetryService = mockk(relaxed = true),
            autoVersioningProcessingLock = lock,
        )

        orders.forEach { order ->
            assertEquals(AutoVersioningProcessingOutcome.CREATED, service.process(order))
        }

        assertEquals(listOf(true, true), merges, "Each merge runs under the merge lock")
        assertEquals(
            listOf("git@example.test:org/repo.git" to "main", "git@example.test:org/repo.git" to "main"),
            lockKeys,
            "Both orders take the lock on the same repository and target branch"
        )
        assertTrue(lockKeys.distinct().size == 1)
    }
}
