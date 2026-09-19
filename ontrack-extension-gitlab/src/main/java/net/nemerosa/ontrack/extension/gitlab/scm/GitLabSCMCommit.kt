package net.nemerosa.ontrack.extension.gitlab.scm

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.gitlab.model.GitLabCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import java.time.LocalDateTime

/**
 * [SCMCommit] for a commit read from the GitLab REST API.
 *
 * @param commit Commit from the API
 * @param repositoryHtmlURL URL of the project's page, used for the link when the API gives none - the
 * comparison endpoint returns commits without a `web_url`, unlike the single-commit one
 */
class GitLabSCMCommit(
    commit: GitLabCommit,
    repositoryHtmlURL: String,
) : SCMCommit {

    override val id: String = commit.id

    /**
     * GitLab's own abbreviation when it sent one, and the same length it uses otherwise.
     */
    override val shortId: String = commit.short_id.takeIf { it.isNotBlank() } ?: commit.id.take(SHORT_ID_LENGTH)

    override val author: String = commit.author_name ?: ""

    override val authorEmail: String? = commit.author_email

    override val timestamp: LocalDateTime = commit.committedTime ?: Time.now()

    /**
     * The full message when GitLab sent one, its title otherwise - the search endpoint sometimes returns
     * only the title.
     */
    override val message: String = (commit.message?.takeIf { it.isNotBlank() } ?: commit.title).trimEnd()

    override val link: String = commit.web_url?.takeIf { it.isNotBlank() }
        ?: "$repositoryHtmlURL/-/commit/${commit.id}"

    companion object {
        /**
         * Length of the abbreviated SHA, as GitLab displays it.
         */
        const val SHORT_ID_LENGTH = 8
    }
}
