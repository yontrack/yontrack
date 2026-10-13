package net.nemerosa.ontrack.extension.av.queue

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The merge lock serialises the merges into the same target branch across consumers.
 *
 * Not transactional: each consumer runs on its own thread, like on its own instance.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutoVersioningProcessingLockIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingLock: AutoVersioningProcessingLock

    @Test
    fun `Two merges into the same branch run one after the other`() {
        val repository = uid("repo-")
        val (first, second) = twoMerges(repository to "main", repository to "main")
        assertFalse(first.overlaps(second), "Merges into the same branch are serialised")
    }

    @Test
    fun `Two merges into different branches run concurrently`() {
        val repository = uid("repo-")
        val (first, second) = twoMerges(repository to "main", repository to "release/1.0")
        assertTrue(first.overlaps(second), "Merges into different branches are not serialised")
    }

    private fun twoMerges(
        firstTarget: Pair<String, String>,
        secondTarget: Pair<String, String>,
    ): Pair<TrackingPostProcessing.Interval, TrackingPostProcessing.Interval> {
        // Both merges are submitted at the same time
        val start = CountDownLatch(1)
        val consumers = Executors.newFixedThreadPool(2)
        try {
            val merges = listOf(firstTarget, secondTarget).map { (repository, branch) ->
                consumers.submit<TrackingPostProcessing.Interval> {
                    start.await()
                    autoVersioningProcessingLock.withMergeLock(repository, branch) {
                        val begin = System.nanoTime()
                        Thread.sleep(1_000)
                        TrackingPostProcessing.Interval(start = begin, end = System.nanoTime())
                    }
                }
            }
            start.countDown()
            return merges[0].get(30, TimeUnit.SECONDS) to merges[1].get(30, TimeUnit.SECONDS)
        } finally {
            consumers.shutdownNow()
        }
    }
}
