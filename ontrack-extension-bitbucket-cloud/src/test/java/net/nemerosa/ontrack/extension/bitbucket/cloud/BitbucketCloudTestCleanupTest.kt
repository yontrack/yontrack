package net.nemerosa.ontrack.extension.bitbucket.cloud

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class BitbucketCloudTestCleanupTest {

    private val now = Instant.parse("2026-09-14T10:00:00Z")

    private fun branchCreatedAgo(age: Duration, name: String = "x") =
        BitbucketCloudTestNames(runId = "1-1", clock = { now - age }).branch(name)

    private class FakeApi(
        branches: List<String>,
        pullRequests: List<BitbucketCloudTestPullRequest>,
    ) : BitbucketCloudTestRepositoryApi {
        val branches = branches.toMutableList()
        val pullRequests = pullRequests.toMutableList()
        val calls = mutableListOf<String>()

        override fun branches(prefix: String): List<String> = branches.filter { it.startsWith(prefix) }

        override fun openPullRequests(): List<BitbucketCloudTestPullRequest> = pullRequests.toList()

        override fun declinePullRequest(id: Int) {
            calls += "decline:$id"
            pullRequests.removeIf { it.id == id }
        }

        override fun deleteBranch(name: String) {
            calls += "delete:$name"
            branches.remove(name)
        }
    }

    @Test
    fun `Test branches older than a day are deleted, recent ones are kept`() {
        val old = branchCreatedAgo(Duration.ofHours(25))
        val recent = branchCreatedAgo(Duration.ofHours(23))
        val api = FakeApi(listOf("main", old, recent), emptyList())

        BitbucketCloudTestCleanup(api, clock = { now }).cleanup()

        assertEquals(listOf("main", recent), api.branches)
    }

    @Test
    fun `A branch using the prefix without a readable creation time is kept`() {
        val api = FakeApi(listOf("yontrack-test-something"), emptyList())

        BitbucketCloudTestCleanup(api, clock = { now }).cleanup()

        assertEquals(listOf("yontrack-test-something"), api.branches)
    }

    @Test
    fun `Open pull requests from old test branches are declined before their branch is deleted`() {
        val old = branchCreatedAgo(Duration.ofDays(3))
        val recent = branchCreatedAgo(Duration.ofMinutes(5))
        val api = FakeApi(
            listOf(old, recent, "feature"),
            listOf(
                BitbucketCloudTestPullRequest(id = 1, sourceBranch = old),
                BitbucketCloudTestPullRequest(id = 2, sourceBranch = recent),
                BitbucketCloudTestPullRequest(id = 3, sourceBranch = "feature"),
            )
        )

        val result = BitbucketCloudTestCleanup(api, clock = { now }).cleanup()

        assertEquals(listOf("decline:1", "delete:$old"), api.calls)
        assertEquals(listOf(1), result.declinedPullRequests)
        assertEquals(listOf(old), result.deletedBranches)
    }

}
