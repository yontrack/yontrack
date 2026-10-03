package net.nemerosa.ontrack.extension.audittrail.repository

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
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
class TrailEntryJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), TrailEntryRepository {

    override fun lockTrail(buildId: Int) {
        // pg_advisory_xact_lock returns void: the row is read and ignored
        namedParameterJdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(:lockClass, :buildId)",
            mapOf("lockClass" to TRAIL_LOCK_CLASS, "buildId" to buildId)
        ) { _ -> }
    }

    override fun findLastEntry(buildId: Int): TrailEntry? =
        namedParameterJdbcTemplate.query(
            "SELECT * FROM BUILD_TRAIL_ENTRY WHERE BUILD_ID = :buildId ORDER BY SEQ DESC LIMIT 1",
            mapOf("buildId" to buildId)
        ) { rs, _ -> toEntry(rs) }.firstOrNull()

    override fun insert(entry: TrailEntry, canonicalPayload: String, canonicalActor: String, time: String): TrailEntry {
        val keyHolder = GeneratedKeyHolder()
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO BUILD_TRAIL_ENTRY (
                    BUILD_ID, SEQ, SCHEMA_VERSION, TYPE, PAYLOAD, ACTOR, TIME, PREV_HASH, HASH
                ) VALUES (
                    :buildId, :seq, :schemaVersion, :type, :payload, :actor, :time, :prevHash, :hash
                )
            """.trimIndent(),
            MapSqlParameterSource(
                mapOf(
                    "buildId" to entry.buildId,
                    "seq" to entry.seq,
                    "schemaVersion" to entry.schemaVersion,
                    "type" to entry.type,
                    "payload" to canonicalPayload,
                    "actor" to canonicalActor,
                    "time" to time,
                    "prevHash" to entry.prevHash,
                    "hash" to entry.hash,
                )
            ),
            keyHolder,
            KEYS,
        )
        return entry.copy(id = keyHolder.key!!.toInt())
    }

    override fun findEntries(buildId: Int): List<TrailEntry> =
        namedParameterJdbcTemplate.query(
            "SELECT * FROM BUILD_TRAIL_ENTRY WHERE BUILD_ID = :buildId ORDER BY SEQ",
            mapOf("buildId" to buildId)
        ) { rs, _ -> toEntry(rs) }

    override fun findLastEntryId(): Int? =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT MAX(ID) FROM BUILD_TRAIL_ENTRY",
            emptyMap<String, Any>(),
            Int::class.javaObjectType,
        )

    override fun findBuildIdsWithEntriesBetween(afterEntryId: Int, upToEntryId: Int): List<Int> =
        namedParameterJdbcTemplate.query(
            "SELECT DISTINCT BUILD_ID FROM BUILD_TRAIL_ENTRY WHERE ID > :after AND ID <= :upTo ORDER BY BUILD_ID",
            mapOf("after" to afterEntryId, "upTo" to upToEntryId),
        ) { rs, _ -> rs.getInt("BUILD_ID") }

    private fun toEntry(rs: ResultSet) = TrailEntry(
        id = rs.getInt("ID"),
        buildId = rs.getInt("BUILD_ID"),
        seq = rs.getInt("SEQ"),
        schemaVersion = rs.getInt("SCHEMA_VERSION"),
        type = rs.getString("TYPE"),
        payload = rs.getString("PAYLOAD").parseAsJson(),
        actor = rs.getString("ACTOR").parseAsJson(),
        time = LocalDateTime.ofInstant(Instant.parse(rs.getString("TIME")), ZoneOffset.UTC),
        prevHash = rs.getString("PREV_HASH"),
        hash = rs.getString("HASH"),
    )

    companion object {
        /**
         * First key of the advisory locks on the trails, the second being the ID of the build:
         * "TRAI" in ASCII. No other advisory lock is taken in Yontrack.
         */
        const val TRAIL_LOCK_CLASS: Int = 0x54524149
    }
}
