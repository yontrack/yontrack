package net.nemerosa.ontrack.extension.bitbucket.cloud

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitbucketCloudTestNamesTest {

    private val now = Instant.parse("2026-09-14T10:15:30Z")

    @Test
    fun `Branch names carry the test prefix, the creation time and the run id`() {
        val names = BitbucketCloudTestNames(runId = "123-1", clock = { now })
        val branch = names.branch("auto-versioning")
        assertTrue(branch.startsWith("yontrack-test-20260914T101530Z-123-1-"), branch)
        assertTrue(branch.endsWith("-auto-versioning"), branch)
    }

    @Test
    fun `Two branches of the same run and name are distinct`() {
        val names = BitbucketCloudTestNames(runId = "local", clock = { now })
        assertNotEquals(names.branch("same"), names.branch("same"))
    }

    @Test
    fun `Branch names are sanitised`() {
        val names = BitbucketCloudTestNames(runId = "local", clock = { now })
        val branch = names.branch("Some Name/with stuff")
        assertTrue(branch.endsWith("-some-name-with-stuff"), branch)
    }

    @Test
    fun `The creation time is read back from a test branch name`() {
        val names = BitbucketCloudTestNames(runId = "local", clock = { now })
        assertEquals(now, BitbucketCloudTestNames.createdAt(names.branch("x")))
    }

    @Test
    fun `No creation time for a branch which is not a test branch`() {
        assertNull(BitbucketCloudTestNames.createdAt("main"))
        assertNull(BitbucketCloudTestNames.createdAt("feature/yontrack-test-20260914T101530Z-x"))
        assertNull(BitbucketCloudTestNames.createdAt("yontrack-test-garbage"))
    }

    @Test
    fun `Run id from the GitHub Actions run`() {
        val env = mapOf("GITHUB_RUN_ID" to "987", "GITHUB_RUN_ATTEMPT" to "2")
        assertEquals("987-2", BitbucketCloudTestNames.runId(env::get))
    }

    @Test
    fun `Run id outside of CI`() {
        assertEquals("local", BitbucketCloudTestNames.runId { null })
    }

}
