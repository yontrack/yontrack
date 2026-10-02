package net.nemerosa.ontrack.extension.audittrail.repository

import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.model.TrailEndorsement
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

@Repository
class TrailEndorsementJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), TrailEndorsementRepository {

    override fun insert(endorsement: TrailEndorsement) {
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO BUILD_TRAIL_ENDORSEMENT (ENTRY_ID, KEY_ID, SIGNATURE, TIME)
                VALUES (:entryId, :keyId, :signature, :time)
            """.trimIndent(),
            mapOf(
                "entryId" to endorsement.entryId,
                "keyId" to endorsement.keyId,
                "signature" to endorsement.signature,
                "time" to TrailHashFormatV1.formatTime(endorsement.time),
            )
        )
    }

    override fun findEndorsements(buildId: Int): List<TrailEndorsement> =
        namedParameterJdbcTemplate.query(
            """
                SELECT D.*
                FROM BUILD_TRAIL_ENDORSEMENT D
                INNER JOIN BUILD_TRAIL_ENTRY E ON E.ID = D.ENTRY_ID
                WHERE E.BUILD_ID = :buildId
                ORDER BY E.SEQ, D.KEY_ID
            """.trimIndent(),
            mapOf("buildId" to buildId)
        ) { rs, _ ->
            TrailEndorsement(
                entryId = rs.getInt("ENTRY_ID"),
                keyId = rs.getString("KEY_ID"),
                signature = rs.getString("SIGNATURE"),
                time = LocalDateTime.ofInstant(Instant.parse(rs.getString("TIME")), ZoneOffset.UTC),
            )
        }
}
