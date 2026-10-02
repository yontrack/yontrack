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

    override fun findFindingsByExternalId(externalId: String, projectIds: Collection<Int>?): List<Finding> =
        when {
            projectIds == null -> namedParameterJdbcTemplate!!.query(
                "SELECT * FROM FINDINGS WHERE EXTERNAL_ID = :externalId ORDER BY ID",
                mapOf("externalId" to externalId)
            ) { rs, _ -> toFinding(rs) }

            projectIds.isEmpty() -> emptyList()

            else -> namedParameterJdbcTemplate!!.query(
                "SELECT * FROM FINDINGS WHERE EXTERNAL_ID = :externalId AND PROJECT_ID IN (:projectIds) ORDER BY ID",
                mapOf("externalId" to externalId, "projectIds" to projectIds.distinct())
            ) { rs, _ -> toFinding(rs) }
        }

    override fun findRankedFindings(
        countingBranchIds: Map<Int, Set<Int>>,
        date: LocalDate,
        size: Int,
    ): List<RankedFinding> {
        val branchIds = countingBranchIds.values.flatten().distinct()
        // Without any branch which counts, no finding is open
        if (branchIds.isEmpty() || size <= 0) return emptyList()
        // State of each finding on the branches which count, as FindingState rolls it up: open as soon
        // as one of its exposures is, else accepted as soon as one is, else resolved, including
        // when it has no exposure there. Then each project counted once per external ID, by its
        // most exposed finding.
        return namedParameterJdbcTemplate!!.query(
            """
                WITH FINDING_STATES AS (
                    SELECT F.ID, F.PROJECT_ID, F.EXTERNAL_ID, F.TITLE, F.FIRST_SEEN, F.LAST_SEEN,
                           $SEVERITY_RANK AS SEVERITY_RANK,
                           CASE
                               WHEN BOOL_OR(E.RESOLVED_AT IS NULL AND NOT ($ACCEPTANCE_HOLDS)) THEN $STATE_OPEN
                               WHEN BOOL_OR(E.RESOLVED_AT IS NULL AND ($ACCEPTANCE_HOLDS)) THEN $STATE_ACCEPTED
                               ELSE $STATE_RESOLVED
                           END AS STATE_RANK
                    FROM FINDINGS F
                    LEFT JOIN FINDING_EXPOSURES E ON E.FINDING_ID = F.ID AND E.BRANCH_ID IN (:branchIds)
                    WHERE F.PROJECT_ID IN (:projectIds)
                    GROUP BY F.ID
                ),
                PROJECT_STATES AS (
                    SELECT EXTERNAL_ID, PROJECT_ID, MIN(STATE_RANK) AS STATE_RANK
                    FROM FINDING_STATES
                    GROUP BY EXTERNAL_ID, PROJECT_ID
                ),
                RANKS AS (
                    SELECT EXTERNAL_ID,
                           COUNT(*) FILTER (WHERE STATE_RANK = $STATE_OPEN) AS OPEN_PROJECTS,
                           COUNT(*) FILTER (WHERE STATE_RANK = $STATE_ACCEPTED) AS ACCEPTED_PROJECTS,
                           COUNT(*) FILTER (WHERE STATE_RANK = $STATE_RESOLVED) AS RESOLVED_PROJECTS
                    FROM PROJECT_STATES
                    GROUP BY EXTERNAL_ID
                    HAVING COUNT(*) FILTER (WHERE STATE_RANK = $STATE_OPEN) > 0
                ),
                DETAILS AS (
                    SELECT EXTERNAL_ID,
                           MIN(SEVERITY_RANK) AS SEVERITY_RANK,
                           MIN(FIRST_SEEN) AS FIRST_SEEN,
                           (ARRAY_AGG(TITLE ORDER BY STATE_RANK, SEVERITY_RANK, LAST_SEEN DESC, ID))[1] AS TITLE
                    FROM FINDING_STATES
                    WHERE EXTERNAL_ID IN (SELECT EXTERNAL_ID FROM RANKS)
                    GROUP BY EXTERNAL_ID
                )
                SELECT R.EXTERNAL_ID, R.OPEN_PROJECTS, R.ACCEPTED_PROJECTS, R.RESOLVED_PROJECTS,
                       D.SEVERITY_RANK, D.FIRST_SEEN, D.TITLE
                FROM RANKS R
                INNER JOIN DETAILS D ON D.EXTERNAL_ID = R.EXTERNAL_ID
                ORDER BY R.OPEN_PROJECTS DESC, D.SEVERITY_RANK, R.EXTERNAL_ID
                LIMIT :size
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("projectIds", countingBranchIds.keys)
                .addValue("branchIds", branchIds)
                .addValue("date", date, java.sql.Types.DATE)
                .addValue("size", size)
        ) { rs, _ ->
            RankedFinding(
                externalId = rs.getString("EXTERNAL_ID"),
                title = rs.getString("TITLE"),
                severity = FindingSeverity.entries[rs.getInt("SEVERITY_RANK")],
                openProjects = rs.getInt("OPEN_PROJECTS"),
                acceptedProjects = rs.getInt("ACCEPTED_PROJECTS"),
                resolvedProjects = rs.getInt("RESOLVED_PROJECTS"),
                firstSeen = rs.readLocalDateTimeNotNull("FIRST_SEEN"),
            )
        }
    }

    override fun forEachFinding(code: (Finding) -> Unit) {
        namedParameterJdbcTemplate!!.query(
            "SELECT * FROM FINDINGS ORDER BY ID",
            emptyMap<String, Any>()
        ) { rs ->
            code(toFinding(rs))
        }
    }

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

    companion object {

        /**
         * Rank of the maximum severity of a finding, its index in [FindingSeverity]: the most
         * severe first.
         */
        private val SEVERITY_RANK: String = FindingSeverity.entries.joinToString(
            separator = " ",
            prefix = "CASE F.MAX_SEVERITY ",
            postfix = " END",
        ) { "WHEN '${it.name}' THEN ${it.ordinal}" }

        /**
         * Whether the acceptance of an exposure holds on the `:date`, as [FindingExposure.stateOn] says.
         */
        private const val ACCEPTANCE_HOLDS =
            "E.ACCEPTED AND (E.ACCEPTANCE_EXPIRES_AT IS NULL OR E.ACCEPTANCE_EXPIRES_AT >= :date)"

        /**
         * Ranks of the states of a finding, the most exposed first.
         */
        private const val STATE_OPEN = 0
        private const val STATE_ACCEPTED = 1
        private const val STATE_RESOLVED = 2
    }
}
