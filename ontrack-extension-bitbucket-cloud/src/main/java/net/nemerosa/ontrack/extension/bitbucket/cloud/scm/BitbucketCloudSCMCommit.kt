package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import java.time.LocalDateTime

/**
 * [SCMCommit] for a commit read from the Bitbucket Cloud REST API.
 *
 * @param commit Commit from the API
 * @param repositoryHtmlURL URL of the repository, used for the link when the API gives none
 */
class BitbucketCloudSCMCommit(
    commit: BitbucketCloudCommit,
    repositoryHtmlURL: String,
) : SCMCommit {

    override val id: String = commit.hash
    override val shortId: String = commit.hash.take(SHORT_ID_LENGTH)
    override val author: String = commit.authorName
    override val authorEmail: String? = commit.authorEmail
    override val timestamp: LocalDateTime = commit.timestamp
    override val message: String = commit.message?.trimEnd() ?: ""
    override val link: String = commit.links?.html?.href?.takeIf { it.isNotBlank() }
        ?: "$repositoryHtmlURL/commits/${commit.hash}"

    companion object {
        /**
         * Length of the abbreviated hash, as displayed by Bitbucket Cloud.
         */
        const val SHORT_ID_LENGTH = 7
    }
}
