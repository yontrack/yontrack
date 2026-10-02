package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.extension.scorecard.estates.Estate
import net.nemerosa.ontrack.extension.scorecard.estates.EstateEnvironmentMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstateMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstatePromotionMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstateReadingConfig
import net.nemerosa.ontrack.extension.scorecard.estates.EstateSecurity
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.getNullableInt
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

@Repository
@Transactional
class EstateJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EstateRepository {

    override fun findAll(): List<Estate> =
        findEstates("", MapSqlParameterSource())

    override fun findById(id: Int): Estate? =
        findEstates("WHERE E.ID = :id", MapSqlParameterSource("id", id)).firstOrNull()

    override fun findByName(name: String): Estate? =
        findEstates("WHERE E.NAME = :name", MapSqlParameterSource("name", name)).firstOrNull()

    override fun findByLabel(labelId: Int): List<Estate> =
        findEstates(
            """
                WHERE EXISTS (
                    SELECT 1 FROM SCORECARD_ESTATE_LABELS EL
                    WHERE EL.ESTATE_ID = E.ID AND EL.LABEL_ID = :labelId
                )
            """,
            MapSqlParameterSource("labelId", labelId)
        )

    override fun findByProject(projectId: Int): List<Estate> =
        findEstates(
            """
                WHERE NOT EXISTS (
                    SELECT 1 FROM SCORECARD_ESTATE_LABELS EL
                    WHERE EL.ESTATE_ID = E.ID
                    AND NOT EXISTS (
                        SELECT 1 FROM PROJECT_LABEL PL
                        WHERE PL.PROJECT_ID = :projectId AND PL.LABEL_ID = EL.LABEL_ID
                    )
                )
                -- An estate has one label at least: guarding against an estate left with none
                AND EXISTS (SELECT 1 FROM SCORECARD_ESTATE_LABELS EL WHERE EL.ESTATE_ID = E.ID)
            """,
            MapSqlParameterSource("projectId", projectId)
        )

    override fun findProjectIds(estateId: Int): List<Int> =
        namedParameterJdbcTemplate!!.queryForList(
            """
                SELECT P.ID
                FROM PROJECTS P
                WHERE EXISTS (SELECT 1 FROM SCORECARD_ESTATE_LABELS EL WHERE EL.ESTATE_ID = :estateId)
                AND NOT EXISTS (
                    SELECT 1 FROM SCORECARD_ESTATE_LABELS EL
                    WHERE EL.ESTATE_ID = :estateId
                    AND NOT EXISTS (
                        SELECT 1 FROM PROJECT_LABEL PL
                        WHERE PL.PROJECT_ID = P.ID AND PL.LABEL_ID = EL.LABEL_ID
                    )
                )
                ORDER BY P.NAME
            """.trimIndent(),
            MapSqlParameterSource("estateId", estateId),
            Int::class.java
        ).filterNotNull()

    override fun create(
        name: String,
        description: String?,
        labelIds: List<Int>,
        marker: EstateMarker?,
        readingConfigs: List<EstateReadingConfig>,
        security: EstateSecurity,
    ): Int {
        val id = dbCreate(
            """
                INSERT INTO SCORECARD_ESTATES (NAME, DESCRIPTION, MARKER_KIND, MARKER_LEVEL, MARKER_ENVIRONMENT, MARKER_QUALIFIER,
                                               SECURITY_FRESHNESS_DAYS, SECURITY_CRITICAL_TARGET_DAYS, SECURITY_HIGH_TARGET_DAYS)
                VALUES (:name, :description, :markerKind, :markerLevel, :markerEnvironment, :markerQualifier,
                        :freshnessDays, :criticalTargetDays, :highTargetDays)
            """.trimIndent(),
            estateParams(name, description, marker, security)
        )
        saveLabelsAndReadings(id, labelIds, readingConfigs)
        saveScanKinds(id, security.expectedKinds)
        return id
    }

    override fun update(
        id: Int,
        name: String,
        description: String?,
        labelIds: List<Int>,
        marker: EstateMarker?,
        readingConfigs: List<EstateReadingConfig>,
        security: EstateSecurity,
    ) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE SCORECARD_ESTATES
                SET NAME = :name,
                    DESCRIPTION = :description,
                    MARKER_KIND = :markerKind,
                    MARKER_LEVEL = :markerLevel,
                    MARKER_ENVIRONMENT = :markerEnvironment,
                    MARKER_QUALIFIER = :markerQualifier,
                    SECURITY_FRESHNESS_DAYS = :freshnessDays,
                    SECURITY_CRITICAL_TARGET_DAYS = :criticalTargetDays,
                    SECURITY_HIGH_TARGET_DAYS = :highTargetDays
                WHERE ID = :id
            """.trimIndent(),
            estateParams(name, description, marker, security).addValue("id", id)
        )
        val params = MapSqlParameterSource("id", id)
        namedParameterJdbcTemplate!!.update("DELETE FROM SCORECARD_ESTATE_LABELS WHERE ESTATE_ID = :id", params)
        namedParameterJdbcTemplate!!.update("DELETE FROM SCORECARD_ESTATE_READINGS WHERE ESTATE_ID = :id", params)
        namedParameterJdbcTemplate!!.update("DELETE FROM SCORECARD_ESTATE_SCAN_KINDS WHERE ESTATE_ID = :id", params)
        saveLabelsAndReadings(id, labelIds, readingConfigs)
        saveScanKinds(id, security.expectedKinds)
    }

    override fun delete(id: Int) {
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM SCORECARD_ESTATES WHERE ID = :id",
            MapSqlParameterSource("id", id)
        )
    }

    private fun estateParams(name: String, description: String?, marker: EstateMarker?, security: EstateSecurity) =
        MapSqlParameterSource()
            .addValue("freshnessDays", security.freshnessDays, Types.INTEGER)
            .addValue("criticalTargetDays", security.criticalTargetDays, Types.INTEGER)
            .addValue("highTargetDays", security.highTargetDays, Types.INTEGER)
            .addValue("name", name)
            .addValue("description", description, Types.VARCHAR)
            .addValue("markerKind", marker?.kind?.name, Types.VARCHAR)
            .addValue("markerLevel", (marker as? EstatePromotionMarker)?.levelName, Types.VARCHAR)
            .addValue("markerEnvironment", (marker as? EstateEnvironmentMarker)?.environment, Types.VARCHAR)
            .addValue("markerQualifier", (marker as? EstateEnvironmentMarker)?.qualifier, Types.VARCHAR)

    private fun saveLabelsAndReadings(id: Int, labelIds: List<Int>, readingConfigs: List<EstateReadingConfig>) {
        labelIds.distinct().forEach { labelId ->
            namedParameterJdbcTemplate!!.update(
                "INSERT INTO SCORECARD_ESTATE_LABELS (ESTATE_ID, LABEL_ID) VALUES (:id, :labelId)",
                MapSqlParameterSource("id", id).addValue("labelId", labelId)
            )
        }
        readingConfigs.forEach { config ->
            namedParameterJdbcTemplate!!.update(
                """
                    INSERT INTO SCORECARD_ESTATE_READINGS (ESTATE_ID, READING, WINDOW_DAYS, TARGET)
                    VALUES (:id, :reading, :windowDays, :target)
                """.trimIndent(),
                MapSqlParameterSource("id", id)
                    .addValue("reading", config.key)
                    .addValue("windowDays", config.windowDays, Types.INTEGER)
                    .addValue("target", config.target, Types.DOUBLE)
            )
        }
    }

    private fun saveScanKinds(id: Int, kinds: List<FindingKind>) {
        kinds.distinct().forEach { kind ->
            namedParameterJdbcTemplate!!.update(
                "INSERT INTO SCORECARD_ESTATE_SCAN_KINDS (ESTATE_ID, KIND) VALUES (:id, :kind)",
                MapSqlParameterSource("id", id).addValue("kind", kind.name)
            )
        }
    }

    private fun findEstates(criteria: String, params: MapSqlParameterSource): List<Estate> {
        val estates = namedParameterJdbcTemplate!!.query(
            """
                SELECT E.*
                FROM SCORECARD_ESTATES E
                ${criteria.trimIndent()}
                ORDER BY E.NAME
            """.trimIndent(),
            params
        ) { rs, _ -> toEstateRecord(rs) }
        if (estates.isEmpty()) return emptyList()
        val ids = estates.map { it.id }
        val labels = namedParameterJdbcTemplate!!.query(
            """
                SELECT EL.ESTATE_ID, L.*
                FROM SCORECARD_ESTATE_LABELS EL
                INNER JOIN LABEL L ON L.ID = EL.LABEL_ID
                WHERE EL.ESTATE_ID IN (:ids)
                ORDER BY L.CATEGORY NULLS FIRST, L.NAME
            """.trimIndent(),
            MapSqlParameterSource("ids", ids)
        ) { rs, _ ->
            rs.getInt("ESTATE_ID") to Label(
                id = rs.getInt("ID"),
                category = rs.getString("CATEGORY"),
                name = rs.getString("NAME"),
                description = rs.getString("DESCRIPTION"),
                color = rs.getString("COLOR"),
            )
        }.groupBy({ it.first }, { it.second })
        val readingConfigs = namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM SCORECARD_ESTATE_READINGS
                WHERE ESTATE_ID IN (:ids)
                ORDER BY READING
            """.trimIndent(),
            MapSqlParameterSource("ids", ids)
        ) { rs, _ ->
            rs.getInt("ESTATE_ID") to EstateReadingConfig(
                key = rs.getString("READING"),
                windowDays = rs.getInt("WINDOW_DAYS").takeIf { !rs.wasNull() },
                target = rs.getDouble("TARGET").takeIf { !rs.wasNull() },
            )
        }.groupBy({ it.first }, { it.second })
        val scanKinds = namedParameterJdbcTemplate!!.query(
            """
                SELECT ESTATE_ID, KIND
                FROM SCORECARD_ESTATE_SCAN_KINDS
                WHERE ESTATE_ID IN (:ids)
            """.trimIndent(),
            MapSqlParameterSource("ids", ids)
        ) { rs, _ ->
            rs.getInt("ESTATE_ID") to FindingKind.entries.find { it.name == rs.getString("KIND") }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, kinds) -> kinds.filterNotNull().sorted() }
        return estates.map { record ->
            Estate(
                id = record.id,
                name = record.name,
                description = record.description,
                labels = labels[record.id] ?: emptyList(),
                marker = record.marker,
                readingConfigs = readingConfigs[record.id] ?: emptyList(),
                security = EstateSecurity(
                    expectedKinds = scanKinds[record.id] ?: emptyList(),
                    freshnessDays = record.freshnessDays,
                    criticalTargetDays = record.criticalTargetDays,
                    highTargetDays = record.highTargetDays,
                ),
            )
        }
    }

    private class EstateRecord(
        val id: Int,
        val name: String,
        val description: String?,
        val marker: EstateMarker?,
        val freshnessDays: Int?,
        val criticalTargetDays: Int?,
        val highTargetDays: Int?,
    )

    private fun toEstateRecord(rs: ResultSet) = EstateRecord(
        id = rs.getInt("ID"),
        name = rs.getString("NAME"),
        description = rs.getString("DESCRIPTION"),
        marker = when (rs.getString("MARKER_KIND")?.let { MarkerKind.valueOf(it) }) {
            null -> null
            MarkerKind.PROMOTION -> EstatePromotionMarker(
                levelName = rs.getString("MARKER_LEVEL"),
            )

            MarkerKind.ENVIRONMENT -> EstateEnvironmentMarker(
                environment = rs.getString("MARKER_ENVIRONMENT"),
                qualifier = rs.getString("MARKER_QUALIFIER") ?: "",
            )
        },
        freshnessDays = rs.getNullableInt("SECURITY_FRESHNESS_DAYS"),
        criticalTargetDays = rs.getNullableInt("SECURITY_CRITICAL_TARGET_DAYS"),
        highTargetDays = rs.getNullableInt("SECURITY_HIGH_TARGET_DAYS"),
    )
}
