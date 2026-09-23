package net.nemerosa.ontrack.extension.findings.repository

import net.nemerosa.ontrack.extension.findings.model.*
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.readLocalDateTime
import net.nemerosa.ontrack.repository.support.readLocalDateTimeNotNull
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import javax.sql.DataSource

@Repository
class FindingJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), FindingRepository {

    // Findings

    override fun insertFinding(finding: Finding): Finding {
        val keyHolder = GeneratedKeyHolder()
        namedParameterJdbcTemplate!!.update(
            """
                INSERT INTO FINDINGS (
                    PROJECT_ID, SCANNER, EXTERNAL_ID, LOCATION, KIND, TITLE, URL,
                    FIRST_SEEN, LAST_SEEN, RESOLVED_AT, MAX_SEVERITY
                ) VALUES (
                    :projectId, :scanner, :externalId, :location, :kind, :title, :url,
                    :firstSeen, :lastSeen, :resolvedAt, :maxSeverity
                )
            """.trimIndent(),
            MapSqlParameterSource(
                findingUpdateParams(finding) + mapOf(
                    "projectId" to finding.projectId,
                    "scanner" to finding.scanner,
                    "externalId" to finding.externalId,
                    "location" to finding.location,
                    "firstSeen" to dateTimeForDB(finding.firstSeen),
                )
            ),
            keyHolder,
            KEYS,
        )
        return finding.copy(id = keyHolder.key!!.toInt())
    }

    override fun updateFinding(finding: Finding) {
        namedParameterJdbcTemplate!!.update(
            """
                UPDATE FINDINGS
                SET KIND = :kind,
                    TITLE = :title,
                    URL = :url,
                    LAST_SEEN = :lastSeen,
                    RESOLVED_AT = :resolvedAt,
                    MAX_SEVERITY = :maxSeverity
                WHERE ID = :id
            """.trimIndent(),
            findingUpdateParams(finding) + mapOf("id" to finding.id)
        )
    }

    private fun findingUpdateParams(finding: Finding): Map<String, Any?> = mapOf(
        "kind" to finding.kind.name,
        "title" to finding.title,
        "url" to finding.url,
        "lastSeen" to dateTimeForDB(finding.lastSeen),
        "resolvedAt" to dateTimeForDB(finding.resolvedAt),
        "maxSeverity" to finding.maxSeverity.name,
    )

    override fun findFindingById(id: Int): Finding? =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDINGS WHERE ID = :id",
            mapOf("id" to id)
        ) { rs, _ -> toFinding(rs) }.firstOrNull()

    override fun findFindingsByIds(ids: Collection<Int>): List<Finding> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                "SELECT * FROM FINDINGS WHERE ID IN (:ids) ORDER BY ID",
                mapOf("ids" to ids)
            ) { rs, _ -> toFinding(rs) }
        }

    override fun findFindingByKey(projectId: Int, scanner: String, externalId: String, location: String): Finding? =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT * FROM FINDINGS
                WHERE PROJECT_ID = :projectId
                AND SCANNER = :scanner
                AND EXTERNAL_ID = :externalId
                AND LOCATION = :location
            """.trimIndent(),
            mapOf(
                "projectId" to projectId,
                "scanner" to scanner,
                "externalId" to externalId,
                "location" to location,
            )
        ) { rs, _ -> toFinding(rs) }.firstOrNull()

    override fun findFindingsByProject(projectId: Int): List<Finding> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDINGS WHERE PROJECT_ID = :projectId ORDER BY ID",
            mapOf("projectId" to projectId)
        ) { rs, _ -> toFinding(rs) }

    override fun findFindingsByProjectAndScanner(projectId: Int, scanner: String): List<Finding> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDINGS WHERE PROJECT_ID = :projectId AND SCANNER = :scanner ORDER BY ID",
            mapOf("projectId" to projectId, "scanner" to scanner)
        ) { rs, _ -> toFinding(rs) }

    override fun findFindingsByExternalId(externalId: String): List<Finding> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDINGS WHERE EXTERNAL_ID = :externalId ORDER BY ID",
            mapOf("externalId" to externalId)
        ) { rs, _ -> toFinding(rs) }

    private fun toFinding(rs: ResultSet) = Finding(
        id = rs.getInt("ID"),
        projectId = rs.getInt("PROJECT_ID"),
        scanner = rs.getString("SCANNER"),
        externalId = rs.getString("EXTERNAL_ID"),
        location = rs.getString("LOCATION"),
        kind = FindingKind.valueOf(rs.getString("KIND")),
        title = rs.getString("TITLE"),
        url = rs.getString("URL"),
        firstSeen = rs.readLocalDateTimeNotNull("FIRST_SEEN"),
        lastSeen = rs.readLocalDateTimeNotNull("LAST_SEEN"),
        resolvedAt = rs.readLocalDateTime("RESOLVED_AT"),
        maxSeverity = FindingSeverity.valueOf(rs.getString("MAX_SEVERITY")),
    )

    // Observations

    override fun insertObservations(observations: List<FindingObservation>) {
        if (observations.isEmpty()) return
        namedParameterJdbcTemplate!!.batchUpdate(
            """
                INSERT INTO FINDING_OBSERVATIONS (
                    FINDING_ID, VALIDATION_RUN_ID, OBSERVED_AT, SEVERITY, RAW_SEVERITY,
                    INSTALLED_VERSION, FIXED_VERSION,
                    ACCEPTED, ACCEPTANCE_STATEMENT, ACCEPTANCE_EXPIRES_AT, ACCEPTANCE_SOURCE
                ) VALUES (
                    :findingId, :validationRunId, :observedAt, :severity, :rawSeverity,
                    :installedVersion, :fixedVersion,
                    :accepted, :acceptanceStatement, :acceptanceExpiresAt, :acceptanceSource
                )
            """.trimIndent(),
            observations.map { observation ->
                MapSqlParameterSource()
                    .addValue("findingId", observation.findingId)
                    .addValue("validationRunId", observation.validationRunId)
                    .addValue("observedAt", dateTimeForDB(observation.time))
                    .addValue("severity", observation.severity.name)
                    .addValue("rawSeverity", observation.rawSeverity)
                    .addValue("installedVersion", observation.installedVersion)
                    .addValue("fixedVersion", observation.fixedVersion)
                    .addValue("accepted", observation.acceptance != null)
                    .addValue("acceptanceStatement", observation.acceptance?.statement)
                    .addValue("acceptanceExpiresAt", observation.acceptance?.expiresAt, java.sql.Types.DATE)
                    .addValue("acceptanceSource", observation.acceptance?.source)
            }.toTypedArray()
        )
    }

    override fun findObservationsByFinding(findingId: Int): List<FindingObservation> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDING_OBSERVATIONS WHERE FINDING_ID = :findingId ORDER BY OBSERVED_AT DESC, ID DESC",
            mapOf("findingId" to findingId)
        ) { rs, _ -> toObservation(rs) }

    override fun findObservationsByValidationRun(validationRunId: Int): List<FindingObservation> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDING_OBSERVATIONS WHERE VALIDATION_RUN_ID = :validationRunId ORDER BY ID",
            mapOf("validationRunId" to validationRunId)
        ) { rs, _ -> toObservation(rs) }

    override fun findLatestSeverities(validationStampId: Int, findingIds: Collection<Int>): Map<Int, FindingSeverity> =
        if (findingIds.isEmpty()) {
            emptyMap()
        } else {
            namedParameterJdbcTemplate!!.query(
                """
                    SELECT DISTINCT ON (O.FINDING_ID) O.FINDING_ID, O.SEVERITY
                    FROM FINDING_OBSERVATIONS O
                    INNER JOIN VALIDATION_RUNS R ON R.ID = O.VALIDATION_RUN_ID
                    WHERE R.VALIDATIONSTAMPID = :validationStampId
                    AND O.FINDING_ID IN (:findingIds)
                    ORDER BY O.FINDING_ID, O.OBSERVED_AT DESC, O.ID DESC
                """.trimIndent(),
                mapOf("validationStampId" to validationStampId, "findingIds" to findingIds)
            ) { rs, _ -> rs.getInt("FINDING_ID") to FindingSeverity.valueOf(rs.getString("SEVERITY")) }
                .toMap()
        }

    private fun toObservation(rs: ResultSet) = FindingObservation(
        findingId = rs.getInt("FINDING_ID"),
        validationRunId = rs.getInt("VALIDATION_RUN_ID"),
        time = rs.readLocalDateTimeNotNull("OBSERVED_AT"),
        severity = FindingSeverity.valueOf(rs.getString("SEVERITY")),
        rawSeverity = rs.getString("RAW_SEVERITY"),
        installedVersion = rs.getString("INSTALLED_VERSION"),
        fixedVersion = rs.getString("FIXED_VERSION"),
        acceptance = if (rs.getBoolean("ACCEPTED")) {
            FindingAcceptance(
                statement = rs.getString("ACCEPTANCE_STATEMENT"),
                expiresAt = rs.getObject("ACCEPTANCE_EXPIRES_AT", LocalDate::class.java),
                source = rs.getString("ACCEPTANCE_SOURCE"),
            )
        } else {
            null
        },
    )

    // Exposure

    override fun saveExposures(exposures: List<FindingExposure>) {
        if (exposures.isEmpty()) return
        namedParameterJdbcTemplate!!.batchUpdate(
            """
                INSERT INTO FINDING_EXPOSURES (
                    FINDING_ID, BRANCH_ID, VALIDATION_STAMP_ID, SINCE,
                    ACCEPTED, ACCEPTANCE_EXPIRES_AT, RESOLVED_AT, RESOLUTION_REASON
                ) VALUES (
                    :findingId, :branchId, :validationStampId, :since,
                    :accepted, :acceptanceExpiresAt, :resolvedAt, :resolutionReason
                )
                ON CONFLICT (FINDING_ID, BRANCH_ID, VALIDATION_STAMP_ID) DO UPDATE
                SET SINCE = EXCLUDED.SINCE,
                    ACCEPTED = EXCLUDED.ACCEPTED,
                    ACCEPTANCE_EXPIRES_AT = EXCLUDED.ACCEPTANCE_EXPIRES_AT,
                    RESOLVED_AT = EXCLUDED.RESOLVED_AT,
                    RESOLUTION_REASON = EXCLUDED.RESOLUTION_REASON
            """.trimIndent(),
            exposures.map { exposure ->
                MapSqlParameterSource()
                    .addValue("findingId", exposure.findingId)
                    .addValue("branchId", exposure.branchId)
                    .addValue("validationStampId", exposure.validationStampId)
                    .addValue("since", dateTimeForDB(exposure.since))
                    .addValue("accepted", exposure.accepted)
                    .addValue("acceptanceExpiresAt", exposure.acceptanceExpiresAt, java.sql.Types.DATE)
                    .addValue("resolvedAt", dateTimeForDB(exposure.resolvedAt))
                    .addValue("resolutionReason", exposure.resolutionReason?.name)
            }.toTypedArray()
        )
    }

    override fun findExposuresByFinding(findingId: Int): List<FindingExposure> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDING_EXPOSURES WHERE FINDING_ID = :findingId ORDER BY ID",
            mapOf("findingId" to findingId)
        ) { rs, _ -> toExposure(rs) }

    override fun findExposuresByFindings(findingIds: Collection<Int>): List<FindingExposure> =
        if (findingIds.isEmpty()) {
            emptyList()
        } else {
            namedParameterJdbcTemplate!!.query(
                "SELECT * FROM FINDING_EXPOSURES WHERE FINDING_ID IN (:findingIds) ORDER BY ID",
                mapOf("findingIds" to findingIds)
            ) { rs, _ -> toExposure(rs) }
        }

    override fun findExposuresByBranch(branchId: Int): List<FindingExposure> =
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDING_EXPOSURES WHERE BRANCH_ID = :branchId ORDER BY ID",
            mapOf("branchId" to branchId)
        ) { rs, _ -> toExposure(rs) }

    override fun findExposuresByBranchAndStamp(branchId: Int, validationStampId: Int): List<FindingExposure> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT * FROM FINDING_EXPOSURES
                WHERE BRANCH_ID = :branchId
                AND VALIDATION_STAMP_ID = :validationStampId
                ORDER BY ID
            """.trimIndent(),
            mapOf("branchId" to branchId, "validationStampId" to validationStampId)
        ) { rs, _ -> toExposure(rs) }

    private fun toExposure(rs: ResultSet) = FindingExposure(
        findingId = rs.getInt("FINDING_ID"),
        branchId = rs.getInt("BRANCH_ID"),
        validationStampId = rs.getInt("VALIDATION_STAMP_ID"),
        since = rs.readLocalDateTimeNotNull("SINCE"),
        accepted = rs.getBoolean("ACCEPTED"),
        acceptanceExpiresAt = rs.getObject("ACCEPTANCE_EXPIRES_AT", LocalDate::class.java),
        resolvedAt = rs.readLocalDateTime("RESOLVED_AT"),
        resolutionReason = rs.getString("RESOLUTION_REASON")?.let { FindingResolutionReason.valueOf(it) },
    )
}
