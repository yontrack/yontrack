package net.nemerosa.ontrack.extension.scm.changelog

import java.time.LocalDateTime

data class SimpleSCMCommit(
    override val id: String,
    override val shortId: String,
    override val author: String,
    override val authorEmail: String?,
    override val timestamp: LocalDateTime,
    override val message: String,
    override val link: String
) : SCMCommit