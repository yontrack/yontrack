package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Commit in Bitbucket Cloud.
 *
 * @property hash Full commit hash
 * @property date Commit date, ISO offset date time
 * @property message Commit message
 * @property author Author of the commit
 * @property links Links, including the HTML page of the commit
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudCommit(
    val hash: String,
    val date: String?,
    val message: String?,
    val author: BitbucketCloudCommitAuthor?,
    val links: BitbucketCloudLinks?,
) {

    /**
     * Name of the author: the Bitbucket user when the commit is linked to one, the Git author otherwise.
     */
    @get:JsonIgnore
    val authorName: String
        get() = author?.user?.display_name?.takeIf { it.isNotBlank() }
            ?: author?.raw?.substringBefore("<")?.trim()?.takeIf { it.isNotBlank() }
            ?: ""

    /**
     * Email of the Git author, parsed from `Name <email>`.
     */
    @get:JsonIgnore
    val authorEmail: String?
        get() = author?.raw
            ?.let { EMAIL.find(it) }
            ?.groupValues?.get(1)

    /**
     * Commit date, in UTC.
     */
    @get:JsonIgnore
    val timestamp: LocalDateTime
        get() = date
            ?.let { java.time.OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            ?.withOffsetSameInstant(ZoneOffset.UTC)
            ?.toLocalDateTime()
            ?: LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)

    companion object {
        private val EMAIL = "<([^>]*)>".toRegex()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudCommitAuthor(
    val raw: String?,
    val user: BitbucketCloudUser?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudUser(
    val display_name: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudLinks(
    val html: BitbucketCloudLink?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudLink(
    val href: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudCommitList(
    override val values: List<BitbucketCloudCommit>,
    override val next: String?,
    override val page: Int = 0,
) : BitbucketCloudPaginatedList<BitbucketCloudCommit>
