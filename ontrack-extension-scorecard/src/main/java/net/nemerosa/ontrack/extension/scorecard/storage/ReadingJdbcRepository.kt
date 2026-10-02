package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.readLocalDateTimeNotNull
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import javax.sql.DataSource

@Repository
class ReadingJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), ReadingRepository {

    @Transactional
    override fun save(readings: List<Reading>) {
        readings.forEach { reading ->
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO SCORECARD_READINGS (
                        ESTATE_ID, PROJECT_ID, READING, DAY,
                        COMPUTED_AT, WINDOW_START, WINDOW_END,
                        VALUE, BASIS, UNKNOWN_REASON, DETAILS
                    ) VALUES (
                        :estateId, :projectId, :reading, :day,
                        :computedAt, :windowStart, :windowEnd,
                        :value, :basis, :unknownReason, CAST(:details AS JSONB)
                    )
                    ON CONFLICT ON CONSTRAINT SCORECARD_READINGS_UQ_KEY DO UPDATE
                    SET COMPUTED_AT = EXCLUDED.COMPUTED_AT,
                        WINDOW_START = EXCLUDED.WINDOW_START,
                        WINDOW_END = EXCLUDED.WINDOW_END,
                        VALUE = EXCLUDED.VALUE,
                        BASIS = EXCLUDED.BASIS,
                        UNKNOWN_REASON = EXCLUDED.UNKNOWN_REASON,
                        DETAILS = EXCLUDED.DETAILS
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("estateId", reading.estateId, Types.INTEGER)
                    .addValue("projectId", reading.projectId)
                    .addValue("reading", reading.key)
                    .addValue("day", java.sql.Date.valueOf(reading.day), Types.DATE)
                    .addValue("computedAt", dateTimeForDB(reading.computedAt))
                    .addValue("windowStart", dateTimeForDB(reading.windowStart))
                    .addValue("windowEnd", dateTimeForDB(reading.windowEnd))
                    .addValue("value", reading.value, Types.DOUBLE)
                    .addValue("basis", reading.basis.name)
                    .addValue("unknownReason", reading.unknownReason?.name, Types.VARCHAR)
                    .addValue("details", writeJson(reading.details))
            )
        }
    }

    override fun findLatestByProject(projectId: Int): List<Reading> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT DISTINCT ON (ESTATE_ID, READING) *
                FROM SCORECARD_READINGS
                WHERE PROJECT_ID = :projectId
                ORDER BY ESTATE_ID NULLS FIRST, READING, DAY DESC
            """.trimIndent(),
            mapOf("projectId" to projectId)
        ) { rs, _ -> toReading(rs) }

    override fun findLatestByEstate(estateId: Int): List<Reading> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT DISTINCT ON (PROJECT_ID, READING) *
                FROM SCORECARD_READINGS
                WHERE ESTATE_ID = :estateId
                ORDER BY PROJECT_ID, READING, DAY DESC
            """.trimIndent(),
            mapOf("estateId" to estateId)
        ) { rs, _ -> toReading(rs) }

    override fun findHistory(estateId: Int?, projectId: Int, key: String, since: LocalDate): List<Reading> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM SCORECARD_READINGS
                WHERE ${if (estateId == null) "ESTATE_ID IS NULL" else "ESTATE_ID = :estateId"}
                AND PROJECT_ID = :projectId
                AND READING = :reading
                AND DAY >= :since
                ORDER BY DAY
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("estateId", estateId, Types.INTEGER)
                .addValue("projectId", projectId)
                .addValue("reading", key)
                .addValue("since", java.sql.Date.valueOf(since), Types.DATE)
        ) { rs, _ -> toReading(rs) }

    override fun forEachPage(estates: Boolean, pageSize: Int, code: (List<Reading>) -> Unit) {
        // Keyset pagination on the ID: the pages hold no connection while they are processed
        var after = 0
        while (true) {
            val ids = mutableListOf<Int>()
            val page = namedParameterJdbcTemplate!!.query(
                """
                    SELECT *
                    FROM SCORECARD_READINGS
                    WHERE ID > :after
                    ${if (estates) "" else "AND ESTATE_ID IS NULL"}
                    ORDER BY ID
                    LIMIT :size
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("after", after)
                    .addValue("size", pageSize)
            ) { rs, _ ->
                ids += rs.getInt("ID")
                toReading(rs)
            }
            if (page.isEmpty()) {
                return
            }
            code(page)
            if (page.size < pageSize) {
                return
            }
            after = ids.last()
        }
    }

    override fun deleteBefore(day: LocalDate): Int =
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM SCORECARD_READINGS WHERE DAY < :day",
            MapSqlParameterSource().addValue("day", java.sql.Date.valueOf(day), Types.DATE)
        )

    private fun toReading(rs: ResultSet) = Reading(
        estateId = rs.getInt("ESTATE_ID").takeIf { !rs.wasNull() },
        projectId = rs.getInt("PROJECT_ID"),
        key = rs.getString("READING"),
        day = rs.getDate("DAY").toLocalDate(),
        computedAt = rs.readLocalDateTimeNotNull("COMPUTED_AT"),
        windowStart = rs.readLocalDateTimeNotNull("WINDOW_START"),
        windowEnd = rs.readLocalDateTimeNotNull("WINDOW_END"),
        value = rs.getDouble("VALUE").takeIf { !rs.wasNull() },
        basis = ReadingBasis.valueOf(rs.getString("BASIS")),
        unknownReason = rs.getString("UNKNOWN_REASON")?.let { ReadingUnknownReason.valueOf(it) },
        details = readJson(rs, "DETAILS")!!,
    )
}
