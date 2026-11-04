package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import java.time.LocalDateTime

/**
 * Snapshot of a [MockSCMExtension.MockRepository], which is what a [MockSCMStore] keeps
 * between two runs of the process.
 *
 * The snapshot is deliberately a set of types of its own rather than the mock SCM's runtime
 * ones: those carry computed fields and mutable collections, and this is **not** a storage
 * format anything is meant to depend on. It exists so that a long-lived demo or development
 * instance does not lose its mock data on a restart; nothing reads it but the mock SCM, and
 * a version of Yontrack is free to drop whatever it finds there.
 *
 * The one property it does have to keep is the one commit ids are derived from: the order of
 * the branches and the position of a commit on its branch. A repository restored from a
 * snapshot and then written to has to number the next commit exactly as the process that
 * wrote the snapshot would have.
 */
data class MockRepositoryData(
    val name: String,
    /**
     * Last value handed out by the repository's revision counter.
     */
    val revision: Long,
    val issues: List<MockIssueData> = emptyList(),
    val branches: List<MockBranchData> = emptyList(),
    val files: List<MockFileData> = emptyList(),
    val createdBranches: List<MockCreatedBranchData> = emptyList(),
    val pullRequests: List<MockPullRequestData> = emptyList(),
)

data class MockIssueData(
    val key: String,
    val message: String,
    val updateTime: LocalDateTime,
    val types: Set<String>? = null,
    val commits: List<String> = emptyList(),
)

data class MockBranchData(
    val name: String,
    val commits: List<MockCommitData> = emptyList(),
)

data class MockCommitData(
    val id: String,
    val revision: Long,
    val message: String,
    val timestamp: LocalDateTime,
    /**
     * Defaults for a snapshot written before the commits of the mock SCM had authors.
     */
    val author: String = MockCommit.UNKNOWN_AUTHOR,
    val authorEmail: String? = null,
)

data class MockFileData(
    val scmBranch: String,
    val path: String,
    val content: String,
)

data class MockCreatedBranchData(
    val branch: String,
    val from: String,
)

data class MockPullRequestData(
    val id: Int,
    val from: String,
    val to: String,
    val title: String,
    val body: String,
    val approved: Boolean,
    val status: SCMPullRequestStatus,
    val reviewers: List<String> = emptyList(),
)
