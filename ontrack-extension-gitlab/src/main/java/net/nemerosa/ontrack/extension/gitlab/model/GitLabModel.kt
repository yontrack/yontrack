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
)
