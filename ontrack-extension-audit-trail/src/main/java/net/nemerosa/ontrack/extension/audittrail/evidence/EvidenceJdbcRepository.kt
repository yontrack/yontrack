package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

@Repository
class EvidenceJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EvidenceRepository {

    override fun insert(evidence: Evidence, canonicalCollectedBy: String): Evidence {
        val keyHolder = GeneratedKeyHolder()
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO EVIDENCE (
                    VALIDATION_RUN_ID, FILE_NAME, MEDIA_TYPE, SIZE, SHA256, COLLECTED_AT, COLLECTED_BY,
                    SOURCE_TOOL, SOURCE_VERSION, SOURCE_URL, EXTERNAL_DIGEST, DELETED_AT
                ) VALUES (
                    :validationRunId, :fileName, :mediaType, :size, :sha256, :collectedAt, :collectedBy,
                    :sourceTool, :sourceVersion, :sourceUrl, :externalDigest, :deletedAt
                )
            """.trimIndent(),
            MapSqlParameterSource(
                mapOf(
                    "validationRunId" to evidence.validationRunId,
                    "fileName" to evidence.fileName,
                    "mediaType" to evidence.mediaType,
                    "size" to evidence.size,
                    "sha256" to evidence.sha256,
                    "collectedAt" to TrailHashFormatV1.formatTime(evidence.collectedAt),
                    "collectedBy" to canonicalCollectedBy,
                    "sourceTool" to evidence.source?.tool,
                    "sourceVersion" to evidence.source?.version,
                    "sourceUrl" to evidence.source?.url,
                    "externalDigest" to evidence.externalDigest,
                    "deletedAt" to evidence.deletedAt?.let { TrailHashFormatV1.formatTime(it) },
                )
            ),
            keyHolder,
            KEYS,
        )
        return evidence.copy(id = keyHolder.key!!.toInt())
    }

    override fun findById(id: Int): Evidence? =
        namedParameterJdbcTemplate.query(
            "SELECT * FROM EVIDENCE WHERE ID = :id",
            mapOf("id" to id),
        ) { rs, _ -> toEvidence(rs) }.firstOrNull()

    override fun findByValidationRun(validationRunId: Int): List<Evidence> =
        namedParameterJdbcTemplate.query(
            "SELECT * FROM EVIDENCE WHERE VALIDATION_RUN_ID = :validationRunId ORDER BY ID",
            mapOf("validationRunId" to validationRunId),
        ) { rs, _ -> toEvidence(rs) }

    private fun toEvidence(rs: ResultSet): Evidence {
        val tool = rs.getString("SOURCE_TOOL")
        val version = rs.getString("SOURCE_VERSION")
        val url = rs.getString("SOURCE_URL")
        return Evidence(
            id = rs.getInt("ID"),
            validationRunId = rs.getInt("VALIDATION_RUN_ID"),
            fileName = rs.getString("FILE_NAME"),
            mediaType = rs.getString("MEDIA_TYPE"),
            size = rs.getLong("SIZE"),
            sha256 = rs.getString("SHA256"),
            collectedAt = parseTime(rs.getString("COLLECTED_AT"))!!,
            collectedBy = rs.getString("COLLECTED_BY").parseAsJson(),
            source = if (tool == null && version == null && url == null) {
                null
            } else {
                EvidenceSource(tool = tool, version = version, url = url)
            },
            externalDigest = rs.getString("EXTERNAL_DIGEST"),
            deletedAt = parseTime(rs.getString("DELETED_AT")),
        )
    }

    private fun parseTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.parse(it), ZoneOffset.UTC) }
}
