package net.nemerosa.ontrack.extension.stash.scm

import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.stash.client.BitbucketServerCommit
import net.nemerosa.ontrack.extension.stash.model.BitbucketRepository
import java.time.LocalDateTime
import java.time.ZoneOffset

class BitbucketServerSCMCommit(
    root: String,
    repo: BitbucketRepository,
    commit: BitbucketServerCommit,
) : SCMCommit {

    override val id: String = commit.id
    override val shortId: String = commit.displayId

    private val actualAuthor = commit.author ?: commit.committer

    override val author: String = actualAuthor.name
    override val authorEmail: String = actualAuthor.emailAddress

    override val committer: String = commit.committer.name

    private val actualTimestamp = commit.authorTimestamp ?: commit.committerTimestamp

    /**
     * Contrarily to what is mentioned in the REST API documentation
     * at https://developer.atlassian.com/server/bitbucket/rest/v811/api-group-repository/#api-api-latest-projects-projectkey-repos-repositoryslug-commits-commitid-get
     * the timestamps ARE NOT expressed in seconds, but in milliseconds
     */
    override val timestamp: LocalDateTime = LocalDateTime.ofEpochSecond((actualTimestamp / 1000), 0, ZoneOffset.UTC)

    override val message: String = commit.message

    override val link: String = "$root/projects/${repo.project}/repos/${repo.repository}/commits/$id"

}