package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.common.api.APIDescription
import java.time.LocalDateTime

/**
 * Common attributes for a commit.
 */
@APIDescription("Common attributes for a commit (or revision).")
interface SCMCommit {

    /**
     * Identifier of the commit
     */
    @APIDescription("Identifier of the commit")
    val id: String

    /**
     * Short identifier for the commit
     */
    @APIDescription("Short identifier for the commit")
    val shortId: String

    /**
     * Author of the commit
     */
    @APIDescription("Author of the commit")
    val author: String

    /**
     * Mail of the author of the commit. Can be `null` if not available.
     */
    @APIDescription("Mail of the author of the commit. Can be `null` if not available.")
    val authorEmail: String?

    /**
     * Timestamp of the commit
     */
    @APIDescription("Timestamp of the commit")
    val timestamp: LocalDateTime

    /**
     * Message associated with the commit
     */
    @APIDescription("Message associated with the commit")
    val message: String

    /**
     * Link to the commit
     */
    @APIDescription("Link to the commit")
    val link: String

    /**
     * Name of the committer, when the SCM gives one and it may differ from the author.
     *
     * Not published in GraphQL: it is read to recognise the assistants of the commit.
     */
    val committer: String? get() = null

    /**
     * Login of the author in the SCM (e.g. a GitHub login), when the SCM gives one.
     *
     * Not published in GraphQL: it is read to recognise the assistants of the commit.
     */
    val authorLogin: String? get() = null

    /**
     * Login of the committer in the SCM (e.g. a GitHub login), when the SCM gives one.
     *
     * Not published in GraphQL: it is read to recognise the assistants of the commit.
     */
    val committerLogin: String? get() = null

}
