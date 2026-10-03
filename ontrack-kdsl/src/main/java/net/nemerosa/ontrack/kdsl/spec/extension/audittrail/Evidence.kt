package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.EvidenceFragment
import org.springframework.web.client.HttpStatusCodeException
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A file attached to a validation run, referenced by the `evidence.attached` entry of the trail of
 * its build.
 *
 * @property id ID of the evidence
 * @property validationRunId ID of the validation run the evidence is attached to
 * @property fileName Name of the file
 * @property mediaType Media type declared when it was uploaded
 * @property size Size of the content, in bytes
 * @property sha256 SHA-256 of the content as the server computed it, in lowercase hexadecimal
 * @property collectedAt Server time of the upload, UTC
 * @property collectedBy Actor of the upload, as entries carry it
 * @property source Where the evidence comes from, as its client claimed it
 * @property externalDigest SHA-256 the client claimed, which matched the computed one
 * @property deletedAt When the evidence was deleted, `null` while it is not
 * @property downloadUrl Path of the download of the content, `null` once the evidence is deleted
 */
class Evidence(
    connector: Connector,
    val id: Int,
    val validationRunId: Int,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val collectedAt: LocalDateTime,
    val collectedBy: JsonNode,
    val source: EvidenceSource?,
    val externalDigest: String?,
    val deletedAt: LocalDateTime?,
    val downloadUrl: String?,
) : Connected(connector) {

    /**
     * Downloads the content of this evidence — whatever the licence.
     *
     * @return Content of the evidence
     * @throws IllegalStateException When the evidence is deleted
     * @throws EvidenceRefusedException When the server refuses the download with a code
     */
    fun download(): ByteArray {
        val path = checkNotNull(downloadUrl) { "Evidence $id is deleted: its content cannot be downloaded." }
        return evidenceCall {
            connector.get(path, headers = mapOf("Accept" to "*/*")).body.asBytes()
        }
    }

    /**
     * Deletes this evidence: it is kept, marked as deleted, and its deletion is written to the
     * trail of its build. Needs the `EvidenceDelete` function on its project.
     *
     * @return The deleted evidence
     * @throws EvidenceRefusedException When the deletion is refused with a code — without the
     * licence, for example
     */
    fun delete(): Evidence = evidenceCall {
        connector.delete("/rest/extension/audit-trail/evidence/$id").body.asJson().toEvidence(connector)
    }

    override fun toString(): String = "Evidence(id=$id, fileName=$fileName, sha256=$sha256, deletedAt=$deletedAt)"
}

/**
 * Where an evidence comes from, as its client claimed it.
 *
 * @property tool Tool which produced the evidence, like `trivy`
 * @property version Version of the tool
 * @property url Where the evidence was produced — a CI job, a report
 */
data class EvidenceSource(
    val tool: String?,
    val version: String?,
    val url: String?,
)

/**
 * A file to attach as evidence.
 *
 * @property fileName Name of the file — a label, never a path
 * @property content Content of the file
 * @property mediaType Media type of the content
 * @property sourceTool Tool which produced the evidence, like `trivy`
 * @property sourceVersion Version of the tool
 * @property sourceUrl Where the evidence was produced: an HTTP or HTTPS URL
 * @property externalDigest SHA-256 of the content, in hexadecimal, as the client computed it: the
 * upload is refused when it does not match
 */
class EvidenceFile(
    val fileName: String,
    val content: ByteArray,
    val mediaType: String,
    val sourceTool: String? = null,
    val sourceVersion: String? = null,
    val sourceUrl: String? = null,
    val externalDigest: String? = null,
)

/**
 * Refusal of an evidence by the server, with its stable code.
 *
 * @property status HTTP status of the refusal
 * @property code Stable code of the refusal, like [STORAGE_NOT_CONFIGURED]
 */
class EvidenceRefusedException(
    val status: Int,
    val code: String,
    message: String,
) : RuntimeException(message) {

    companion object {
        /**
         * The storage of the evidence is not configured
         */
        const val STORAGE_NOT_CONFIGURED = "audit-trail.evidence.storage-not-configured"

        /**
         * The storage of the evidence cannot be reached
         */
        const val STORAGE_UNREACHABLE = "audit-trail.evidence.storage-unreachable"

        /**
         * The evidence is bigger than the instance allows
         */
        const val TOO_LARGE = "audit-trail.evidence.too-large"

        /**
         * The digest claimed by the client is not the one of the content
         */
        const val DIGEST_MISMATCH = "audit-trail.evidence.digest-mismatch"

        /**
         * The licence does not allow the audit trail
         */
        const val NOT_LICENSED = "audit-trail.evidence.not-licensed"

        /**
         * What was sent with the evidence cannot be accepted
         */
        const val INVALID = "audit-trail.evidence.invalid"
    }
}

/**
 * Runs a REST call on the evidence, turning a refusal which carries a code into an
 * [EvidenceRefusedException]. Any other failure is thrown as it is.
 */
internal fun <T> evidenceCall(code: () -> T): T =
    try {
        code()
    } catch (ex: HttpStatusCodeException) {
        val body = try {
            ex.responseBodyAsString.takeIf { it.isNotBlank() }?.parseAsJson()
        } catch (_: Exception) {
            null
        }
        val refusal = body?.path("code")?.takeIf { it.isString }?.asString()
        if (refusal != null) {
            throw EvidenceRefusedException(
                status = ex.statusCode.value(),
                code = refusal,
                message = body.path("message").takeIf { it.isString }?.asString() ?: refusal,
            )
        } else {
            throw ex
        }
    }

/**
 * Evidence, as the REST API returns it.
 */
internal fun JsonNode.toEvidence(connector: Connector) = Evidence(
    connector = connector,
    id = path("id").asInt(),
    validationRunId = path("validationRunId").asInt(),
    fileName = path("fileName").asString(),
    mediaType = path("mediaType").asString(),
    size = path("size").asLong(),
    sha256 = path("sha256").asString(),
    collectedAt = parseTime(path("collectedAt").asString()),
    collectedBy = path("collectedBy"),
    source = path("source").takeIf { it.isObject }?.let { source ->
        EvidenceSource(
            tool = source.optionalText("tool"),
            version = source.optionalText("version"),
            url = source.optionalText("url"),
        )
    },
    externalDigest = optionalText("externalDigest"),
    deletedAt = optionalText("deletedAt")?.let { parseTime(it) },
    downloadUrl = optionalText("downloadUrl"),
)

/**
 * Evidence, as the GraphQL API returns it.
 */
internal fun EvidenceFragment.toEvidence(connector: Connector) = Evidence(
    connector = connector,
    id = id,
    validationRunId = validationRunId,
    fileName = fileName,
    mediaType = mediaType,
    size = size,
    sha256 = sha256,
    collectedAt = collectedAt,
    collectedBy = collectedBy,
    source = source?.let { EvidenceSource(tool = it.tool, version = it.version, url = it.url) },
    externalDigest = externalDigest,
    deletedAt = deletedAt,
    downloadUrl = downloadUrl,
)

private fun JsonNode.optionalText(field: String): String? =
    path(field).takeIf { it.isString }?.asString()

private fun parseTime(value: String): LocalDateTime =
    try {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: DateTimeParseException) {
        LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)
    }
