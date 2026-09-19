package net.nemerosa.ontrack.extension.gitlab.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * How a merge request's `detailed_merge_status` is read by the auto-versioning merge.
 *
 * `merge_status` is deprecated since 15.6 and is deliberately not consulted anywhere.
 */
class GitLabMergeabilityTest {

    @Test
    fun `Mergeable is the only status which merges`() {
        assertEquals(GitLabMergeability.MERGEABLE, GitLabMergeability.of("mergeable"))
    }

    @Test
    fun `A merge GitLab is still working out is pending`() {
        listOf("checking", "unchecked", "preparing", "approvals_syncing").forEach {
            assertEquals(GitLabMergeability.PENDING, GitLabMergeability.of(it), "Pending: $it")
        }
    }

    @Test
    fun `A pipeline which has not finished is pending`() {
        listOf("ci_must_pass", "ci_still_running").forEach {
            assertEquals(GitLabMergeability.PENDING, GitLabMergeability.of(it), "Pending: $it")
        }
    }

    @Test
    fun `A status needing a human is blocked rather than waited out`() {
        listOf(
            "conflict",
            "not_approved",
            "draft_status",
            "need_rebase",
            "discussions_not_resolved",
            "requested_changes",
            "blocked_status",
        ).forEach {
            assertEquals(GitLabMergeability.BLOCKED, GitLabMergeability.of(it), "Blocked: $it")
        }
    }

    /**
     * A status GitLab has not invented yet: it is not waited out, so a new one costs a report rather than
     * the whole timeout - and never a merge.
     */
    @Test
    fun `An unknown status is blocked`() {
        assertEquals(GitLabMergeability.BLOCKED, GitLabMergeability.of("something_new_in_20_0"))
    }

    /**
     * Only a GitLab older than 15.6, or a partial answer, leaves the field out. Waiting is the safe
     * reading of "not known".
     */
    @Test
    fun `No status at all is pending`() {
        assertEquals(GitLabMergeability.PENDING, GitLabMergeability.of(null))
        assertEquals(GitLabMergeability.PENDING, GitLabMergeability.of(""))
    }

    @Test
    fun `A merge request reads its own status`() {
        assertEquals(
            GitLabMergeability.MERGEABLE,
            GitLabMergeRequest(iid = 1, detailed_merge_status = "mergeable").mergeability,
        )
        assertEquals(
            GitLabMergeability.BLOCKED,
            GitLabMergeRequest(iid = 1, detailed_merge_status = "conflict").mergeability,
        )
    }
}
