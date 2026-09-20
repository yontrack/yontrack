package net.nemerosa.ontrack.extension.gitlab.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.Time
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Objects returned by the GitLab REST API.
 *
 * Their fields are named as GitLab names them, so that the mapping stays a plain reading of the API
 * documentation. Every one of them ignores the fields Yontrack does not read - GitLab returns a lot of them,
 * and they differ between deployments and versions.
 */

/**
 * The authenticated user, read to validate a configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabUser(
    val id: Long = 0,
    val username: String = "",
)

/**
 * A project, as returned by `/projects`.
 *
 * [path_with_namespace] is the full path, subgroups included - `group/subgroup/project` - and is what the
 * GitLab project property of a Yontrack project holds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabProject(
    val id: Long = 0,
    val name: String = "",
    val path_with_namespace: String = "",
    val web_url: String? = null,
    val default_branch: String? = null,
)

/**
 * A milestone, as nested in an issue.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabMilestone(
    val id: Long = 0,
    val iid: Long = 0,
    val title: String = "",
    val web_url: String? = null,
)

/**
 * An issue.
 *
 * [iid] is the number a `#123` reference names, and is the one Yontrack uses as a key; [id] is global to the
 * instance and means nothing to a user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabIssue(
    val id: Long = 0,
    val iid: Long = 0,
    val project_id: Long = 0,
    val title: String = "",
    val state: String = "",
    val web_url: String = "",
    val labels: List<String> = emptyList(),
    val updated_at: String? = null,
    val milestone: GitLabMilestone? = null,
) {
    /**
     * [updated_at] as a UTC date and time, or _now_ when GitLab did not send one.
     */
    val updateTime: LocalDateTime
        get() = updated_at?.let {
            try {
                OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: Time.now()
}

/**
 * A commit, as returned by the repository and search endpoints.
 *
 * [id] is the full SHA, which is what Yontrack stores and links on; [short_id] is GitLab's abbreviation
 * of it and is only ever displayed by GitLab itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabCommit(
    val id: String = "",
    val short_id: String = "",
    val title: String = "",
    val message: String? = null,
    val committed_date: String? = null,
    val web_url: String? = null,
    val author_name: String? = null,
    val author_email: String? = null,
) {
    /**
     * [committed_date] as a UTC date and time, or `null` when GitLab sent none or an unreadable one.
     *
     * Unlike an issue's update time this does **not** fall back on _now_: a commit with no date must sort
     * last rather than first.
     */
    val committedTime: LocalDateTime?
        get() = committed_date?.let {
            try {
                OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /**
     * Does this commit name issue [iid], as opposed to merely starting with its digits?
     *
     * GitLab's basic commit search is a substring search, so a search for `#12` also brings back the
     * commits of `#123`.
     */
    fun mentionsIssue(iid: Int): Boolean =
        Regex("#$iid(?!\\d)").containsMatchIn(message ?: title)
}

/**
 * A merge request.
 *
 * [iid] is the number inside the project, which every merge request endpoint takes and which Yontrack names
 * a pull request by; [id] is global to the instance and means nothing to a user.
 *
 * Three of the fields exist for the auto-versioning merge, and each of them replaces something GitLab has
 * deprecated:
 *
 * * [detailed_merge_status] replaces `merge_status`, deprecated since 15.6, and is what the auto-versioning
 *   polling reads - see [mergeability];
 * * [sha] is the head of the source branch, which the merge call sends back: GitLab 19.2 added a project
 *   setting making `sha` mandatory on merge, and a mismatch is a 409 rather than a merge of the wrong thing;
 * * [squash_on_merge] is what GitLab will **actually** do, which project settings can force either way, as
 *   opposed to `squash`, which is only what was asked for.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabMergeRequest(
    val id: Long = 0,
    val iid: Long = 0,
    val title: String = "",
    val state: String = "",
    val source_branch: String = "",
    val target_branch: String = "",
    val web_url: String = "",
    val sha: String? = null,
    val detailed_merge_status: String? = null,
    val squash_on_merge: Boolean = false,
    val has_conflicts: Boolean = false,
) {
    /**
     * Can this merge request be merged, not yet, or not at all - read from [detailed_merge_status].
     */
    val mergeability: GitLabMergeability get() = GitLabMergeability.of(detailed_merge_status)
}

/**
 * What a merge request's `detailed_merge_status` means to a caller waiting to merge it.
 *
 * GitLab has a good twenty values there and keeps adding to them, so they are not enumerated: only the ones
 * known to be **transient** are listed, and anything else which is not `mergeable` is taken to need a human.
 * A status GitLab invents next therefore costs a give-up and a report rather than a caller waiting out its
 * whole timeout - and never a merge.
 */
enum class GitLabMergeability {

    /** Ready to be merged now. */
    MERGEABLE,

    /** Not yet, but it can become mergeable on its own: waiting is the right answer. */
    PENDING,

    /** Not mergeable without someone doing something - a conflict, a rebase, a missing approval. */
    BLOCKED;

    companion object {

        /**
         * The `detailed_merge_status` of a merge request which can be merged now.
         */
        const val MERGEABLE_STATUS = "mergeable"

        /**
         * The statuses which resolve themselves: GitLab is still computing the merge, or the pipeline has
         * not finished running yet.
         */
        val PENDING_STATUSES: Set<String> = setOf(
            "checking",
            "unchecked",
            "preparing",
            "approvals_syncing",
            "ci_must_pass",
            "ci_still_running",
        )

        /**
         * A merge request carrying no status at all is taken as [PENDING] rather than [BLOCKED]: the field
         * is only ever absent on a GitLab older than 15.6 or on a partial answer, and waiting out the
         * timeout is the safe reading of "not known".
         */
        fun of(status: String?): GitLabMergeability = when {
            status.isNullOrBlank() -> PENDING
            status == MERGEABLE_STATUS -> MERGEABLE
            status in PENDING_STATUSES -> PENDING
            else -> BLOCKED
        }
    }
}

/**
 * A file of a repository, as `GET /projects/:id/repository/files/:path` returns it.
 *
 * Only [last_commit_id] is read from it: it is what an update sends back to GitLab so that a file changed
 * since it was read is rejected rather than silently overwritten. The content itself comes from the `/raw`
 * endpoint instead, which does not base64-encode it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabFile(
    val file_path: String = "",
    val ref: String = "",
    val blob_id: String? = null,
    val last_commit_id: String? = null,
)

/**
 * A branch, as returned by `/projects/:id/repository/branches`.
 *
 * [commit] is the branch's head; GitLab nests the whole commit object rather than only its SHA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabBranch(
    val name: String = "",
    val commit: GitLabCommit? = null,
    val default: Boolean = false,
)

/**
 * The answer of `/projects/:id/repository/compare`.
 *
 * [commits] are the commits of the comparison, oldest first. [compare_timeout] is GitLab telling that it gave
 * up before walking the whole range, which makes [commits] incomplete rather than wrong.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabCompare(
    val commit: GitLabCommit? = null,
    val commits: List<GitLabCommit> = emptyList(),
    val compare_timeout: Boolean = false,
    val compare_same_ref: Boolean = false,
)

/**
 * A pipeline, as `POST /projects/:id/pipeline` and `GET /projects/:id/pipelines/:pipeline_id` return it.
 *
 * [id] is the identifier of the pipeline **in the instance** and is the one every pipeline endpoint takes;
 * [iid] is its number inside the project, which is what GitLab's own UI displays. Both are exposed, because
 * a user reading a notification recognises the `iid` while anything calling back into the API needs the `id`.
 *
 * [status] is not turned into an enum: GitLab keeps adding to the list - `waiting_for_resource` and
 * `canceling` are both recent - and a value nobody knows must still be reportable. See [completed].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabPipeline(
    val id: Long = 0,
    val iid: Long = 0,
    val project_id: Long = 0,
    val ref: String? = null,
    val sha: String? = null,
    val status: String = "",
    val source: String? = null,
    val web_url: String? = null,
) {
    /**
     * Is the pipeline over?
     *
     * Read from [GitLabPipelineStatuses.isCompleted], which lists the statuses which are **not** over rather
     * than the ones which are.
     */
    val completed: Boolean get() = GitLabPipelineStatuses.isCompleted(status)
}

/**
 * What a pipeline's `status` means to a caller waiting for it.
 *
 * Only the **in-flight** statuses are listed, and anything else counts as completed. The reasoning is
 * [GitLabMergeability]'s: GitLab invents new statuses, and a caller which does not recognise one is better
 * off reporting it than waiting out its whole timeout on it.
 */
object GitLabPipelineStatuses {

    /**
     * The status of a pipeline which completed successfully. Everything else which is completed is a failure
     * of one kind or another - `failed`, `canceled`, `skipped`, or `manual` for a pipeline waiting on a
     * manual job which is never going to be run by Yontrack.
     */
    const val SUCCESS = "success"

    /**
     * Statuses of a pipeline which is still going to move on its own.
     *
     * The five the GitLab documentation calls "in progress" - `created`, `waiting_for_resource`, `preparing`,
     * `pending` and `running` - plus `scheduled`, for a delayed pipeline, and `canceling`, which is a
     * cancellation in progress and does reach `canceled`.
     */
    val IN_FLIGHT: Set<String> = setOf(
        "created",
        "waiting_for_resource",
        "preparing",
        "pending",
        "running",
        "scheduled",
        "canceling",
    )

    /**
     * A blank status is taken as in flight: it is only ever seen on a partial answer, and polling once more
     * is the safe reading of "not known".
     */
    fun isCompleted(status: String?): Boolean = when {
        status.isNullOrBlank() -> false
        else -> status !in IN_FLIGHT
    }

    /**
     * Did the pipeline complete successfully?
     */
    fun isSuccessful(status: String?): Boolean = status == SUCCESS
}
