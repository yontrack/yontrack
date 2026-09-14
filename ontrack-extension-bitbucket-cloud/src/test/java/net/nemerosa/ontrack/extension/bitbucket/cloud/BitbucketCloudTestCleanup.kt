package net.nemerosa.ontrack.extension.bitbucket.cloud

import java.time.Duration
import java.time.Instant

data class BitbucketCloudTestPullRequest(
    val id: Int,
    val sourceBranch: String,
)

/**
 * What the cleanup needs from the fixture repository.
 */
interface BitbucketCloudTestRepositoryApi {
    fun branches(prefix: String): List<String>
    fun openPullRequests(): List<BitbucketCloudTestPullRequest>
    fun declinePullRequest(id: Int)
    fun deleteBranch(name: String)
}

data class BitbucketCloudTestCleanupResult(
    val declinedPullRequests: List<Int>,
    val deletedBranches: List<String>,
)

/**
 * Removes what earlier runs left in the fixture repository: open pull requests from test branches, then the
 * test branches themselves, once they are older than [maxAge]. Only branches named by [BitbucketCloudTestNames]
 * are ever touched.
 */
class BitbucketCloudTestCleanup(
    private val api: BitbucketCloudTestRepositoryApi,
    private val clock: () -> Instant = Instant::now,
    private val maxAge: Duration = Duration.ofDays(1),
) {

    fun cleanup(): BitbucketCloudTestCleanupResult {
        val threshold = clock() - maxAge
        fun stale(branch: String) = BitbucketCloudTestNames.createdAt(branch)?.isBefore(threshold) == true

        val declined = api.openPullRequests()
            .filter { stale(it.sourceBranch) }
            .map { pr ->
                api.declinePullRequest(pr.id)
                pr.id
            }
        val deleted = api.branches(BitbucketCloudTestNames.PREFIX)
            .filter { stale(it) }
            .onEach { api.deleteBranch(it) }

        return BitbucketCloudTestCleanupResult(
            declinedPullRequests = declined,
            deletedBranches = deleted,
        )
    }
}
