package net.nemerosa.ontrack.extension.gitlab.model

import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.issues.model.IssueStatus
import java.time.LocalDateTime

/**
 * A GitLab issue, seen as a Yontrack [Issue].
 *
 * The key is the issue's `iid` - its number **inside its project**, the one a `#123` reference names and the
 * one the API is called with - and not the instance-wide `id`, which means nothing to a user.
 */
data class GitLabIssueWrapper(
    val gitlabIssue: GitLabIssue,
    val milestoneUrl: String?,
) : Issue {

    override val url: String get() = gitlabIssue.web_url
    override val key: String get() = gitlabIssue.iid.toString()
    override val displayKey: String get() = "#${gitlabIssue.iid}"
    override val summary: String get() = gitlabIssue.title
    override val status: IssueStatus get() = GitLabIssueStatusWrapper(gitlabIssue.state)
    override val updateTime: LocalDateTime get() = gitlabIssue.updateTime

    val labels: List<String> get() = gitlabIssue.labels

}
