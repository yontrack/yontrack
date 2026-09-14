package net.nemerosa.ontrack.extension.bitbucket.cloud

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [BitbucketCloudTestCleanup] once per JVM, before the first real Bitbucket Cloud test.
 *
 * A failed cleanup is logged and does not fail the tests: leftovers cost nothing but clutter, and are picked
 * up again by the next run.
 */
class BitbucketCloudTestCleanupExtension : BeforeEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        if (!done.compareAndSet(false, true)) return
        try {
            if (bitbucketCloudTestEnabled()) {
                val result = BitbucketCloudTestCleanup(BitbucketCloudTestRestApi.of(bitbucketCloudTestEnv)).cleanup()
                logger.info(
                    "Bitbucket Cloud test cleanup: declined pull requests {}, deleted branches {}",
                    result.declinedPullRequests,
                    result.deletedBranches,
                )
            }
        } catch (any: Exception) {
            logger.warn("Bitbucket Cloud test cleanup failed", any)
        }
    }

    companion object {
        private val done = AtomicBoolean(false)
        private val logger = LoggerFactory.getLogger(BitbucketCloudTestCleanupExtension::class.java)
    }
}
