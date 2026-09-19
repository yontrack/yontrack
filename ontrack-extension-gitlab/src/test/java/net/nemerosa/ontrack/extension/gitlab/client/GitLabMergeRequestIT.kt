package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.GitLabTestFixture
import net.nemerosa.ontrack.extension.gitlab.TestOnGitLab
import net.nemerosa.ontrack.extension.gitlab.gitLabTestBranch
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigReal
import net.nemerosa.ontrack.extension.gitlab.gitLabTestEnv
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeability
import net.nemerosa.ontrack.test.TestUtils.uid
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The merge request half of the client, against the real gitlab.com fixture.
 *
 * API only: the fixture's pipeline is created **only** when `MOCK_RESULT` is passed, so pushing a branch and
 * opening a merge request here costs no compute minute at all. See the module's README.
 *
 * Skipped unless the fixture's credentials are set.
 */
class GitLabMergeRequestIT {

    private val client: GitLabClient get() = DefaultGitLabClient(gitLabTestConfigReal())

    private val project get() = gitLabTestEnv.projectPath

    @TestOnGitLab
    fun `Creating, approving and merging a merge request`() {
        val branch = branchName()
        onBranch(branch) {
            val mr = client.createMergeRequest(
                project = project,
                sourceBranch = branch,
                targetBranch = defaultBranch(),
                title = "Auto-versioning test $branch",
                description = "Opened by GitLabMergeRequestIT.",
                reviewerIds = emptyList(),
                removeSourceBranch = true,
                squash = true,
            )
            assertTrue(mr.iid > 0, "The merge request has an iid")
            assertTrue(!mr.sha.isNullOrBlank(), "The merge request carries the sha to merge with")
            // The bot approves its own merge request, which the fixture project allows through
            // merge_requests_author_approval
            client.approveMergeRequest(project, mr.iid.toInt())
            val mergeable = waitUntilMergeable(mr.iid.toInt())
            client.mergeMergeRequest(
                project = project,
                iid = mr.iid.toInt(),
                sha = assertNotNull(mergeable.sha, "The merge request carries a sha"),
                message = "Merged by GitLabMergeRequestIT",
                squash = mergeable.squash_on_merge,
                removeSourceBranch = true,
                autoMerge = false,
            )
            val merged = assertNotNull(client.getMergeRequest(project, mr.iid.toInt()))
            assertEquals("merged", merged.state)
        }
    }

    /**
     * `detailed_merge_status` is the field the auto-versioning polling reads - `merge_status` has been
     * deprecated since 15.6.
     */
    @TestOnGitLab
    fun `A merge request reports a detailed merge status`() {
        val branch = branchName()
        onBranch(branch) {
            val mr = client.createMergeRequest(
                project = project,
                sourceBranch = branch,
                targetBranch = defaultBranch(),
                title = "Auto-versioning test $branch",
                description = "Opened by GitLabMergeRequestIT.",
                reviewerIds = emptyList(),
                removeSourceBranch = true,
                squash = true,
            )
            val status = waitUntilKnown(mr.iid.toInt()).detailed_merge_status
            assertTrue(
                !status.isNullOrBlank(),
                "Expecting a detailed_merge_status on the merge request, got $status",
            )
        }
    }

    /**
     * A reviewer is configured by name and sent as a numeric `reviewer_ids`, so the lookup has to work on a
     * real instance.
     */
    @TestOnGitLab
    fun `The bot can be looked up by its username`() {
        val me = client.getCurrentUser()
        val found = assertNotNull(client.findUserByUsername(me.username), "The bot is found by its username")
        assertEquals(me.id, found.id)
    }

    @TestOnGitLab
    fun `An unknown username is null`() {
        assertNull(client.findUserByUsername("yontrack-no-such-user-${uid("")}"))
    }

    /**
     * Uploading a file twice on the same branch: the second write sends the `last_commit_id` of the first
     * and is accepted.
     */
    @TestOnGitLab
    fun `A file can be created and then replaced on a branch`() {
        val branch = branchName()
        onBranch(branch) {
            val path = "it/${uid("f")}.txt"
            client.upload(project, branch, path, "first".toByteArray(), "Creating $path")
            assertEquals("first", client.download(project, branch, path)?.decodeToString())
            client.upload(project, branch, path, "second".toByteArray(), "Replacing $path")
            assertEquals("second", client.download(project, branch, path)?.decodeToString())
        }
    }

    private fun defaultBranch(): String =
        assertNotNull(client.getProject(project)?.default_branch, "The fixture project has a default branch")

    private fun branchName() = gitLabTestBranch()

    /**
     * Creates [branch] off the default branch with one change on it, runs [code], and deletes it again -
     * whatever happened. A merge deletes the branch itself, and deleting a branch which is gone is not an
     * error, so the cleanup is unconditional.
     */
    private fun onBranch(branch: String, code: () -> Unit) {
        client.createBranch(project, defaultBranch(), branch)
        try {
            client.upload(
                project = project,
                branch = branch,
                path = GitLabTestFixture.VERSION_FILE,
                content = (
                        "# Fixture file of the GitLab test project, edited by the auto-versioning tests.\n" +
                                "${GitLabTestFixture.VERSION_PROPERTY}=${uid("1.0.")}\n"
                        ).toByteArray(),
                message = "Auto-versioning test change on $branch",
            )
            code()
        } finally {
            client.deleteBranch(project, branch)
        }
    }

    /**
     * Polls until GitLab says the merge request can be merged. A status which cannot resolve itself ends the
     * wait at once, as the auto-versioning merge does.
     */
    private fun waitUntilMergeable(iid: Int): GitLabMergeRequest {
        repeat(POLLS) {
            val mr = client.getMergeRequest(project, iid) ?: fail("Merge request !$iid is gone")
            when (mr.mergeability) {
                GitLabMergeability.MERGEABLE -> return mr
                GitLabMergeability.BLOCKED ->
                    fail("Merge request !$iid cannot be merged: ${mr.detailed_merge_status}")

                GitLabMergeability.PENDING -> Thread.sleep(POLL_MILLIS)
            }
        }
        fail("Merge request !$iid never became mergeable")
    }

    /**
     * Polls until GitLab has worked out a `detailed_merge_status` beyond its own "still checking" ones.
     */
    private fun waitUntilKnown(iid: Int): GitLabMergeRequest {
        repeat(POLLS) {
            val mr = client.getMergeRequest(project, iid) ?: fail("Merge request !$iid is gone")
            if (mr.detailed_merge_status !in CHECKING) return mr
            Thread.sleep(POLL_MILLIS)
        }
        return client.getMergeRequest(project, iid) ?: fail("Merge request !$iid is gone")
    }

    companion object {
        private const val POLLS = 30
        private const val POLL_MILLIS = 2_000L
        private val CHECKING = setOf(null, "", "checking", "unchecked", "preparing")
    }
}
