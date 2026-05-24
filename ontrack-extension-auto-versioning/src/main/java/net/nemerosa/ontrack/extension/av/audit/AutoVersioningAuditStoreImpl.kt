package net.nemerosa.ontrack.extension.av.audit

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.repository.BranchJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import net.nemerosa.ontrack.repository.support.getNullableInt
import net.nemerosa.ontrack.repository.support.readLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Auto-versioning audit store based on the entity data store of the source project.
 */
@Component
class AutoVersioningAuditStoreImpl(
    dataSource: DataSource,
    private val securityService: SecurityService,
    private val branchJdbcRepositoryAccessor: BranchJdbcRepositoryAccessor,
) : AutoVersioningAuditStore, AbstractJdbcRepository(dataSource) {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningAuditStoreImpl::class.java)

    /**
     * Made accessible for tests
     */
    internal var signatureProvider: () -> Signature = { securityService.currentSignature }

    private fun signature() = signatureProvider()

    override fun throttling(order: AutoVersioningOrder): Int {
        val filter = AutoVersioningAuditQueryFilter(
            source = order.sourceProject,
            project = order.branch.project.name,
            branch = order.branch.name,
            qualifier = order.qualifier,
            states = AutoVersioningAuditState.runningAndNotProcessingStates,
            targetPaths = order.allPaths.flatMap { it.paths },
        )
        val throttledEntries = auditVersioningEntries(filter)

        throttledEntries.forEach { entry ->
            logger.debug("Throttling {}", entry)
            addState(
                targetBranch = entry.order.branch,
                uuid = entry.order.uuid,
                queue = null,
                state = AutoVersioningAuditState.THROTTLED,
            )
        }

        return throttledEntries.size
    }

    override fun create(order: AutoVersioningOrder): AutoVersioningAuditEntry {
        val signature = signature()

        val initialState = AutoVersioningAuditEntryState(
            signature = signature,
            state = AutoVersioningAuditState.CREATED,
            data = emptyMap()
        )

        val entry = AutoVersioningAuditEntry(
            order = order,
            audit = listOf(initialState),
            routing = null,
            queue = null,
            upgradeBranch = null,
        )

        saveEntry(entry)

        return entry
    }

    internal fun saveEntry(entry: AutoVersioningAuditEntry) {
        val statesJson = writeJson(entry.audit)
        val targetPathsJson = writeJson(entry.order.defaultPath.paths)
        val reviewersJson = writeJson(entry.order.reviewers)
        val additionalPathsJson = writeJson(entry.order.additionalPaths)

        val sql = """
            INSERT INTO AV_AUDIT (
                UUID, TIMESTAMP, BRANCH_ID,
                SOURCE_PROJECT, SOURCE_BUILD_ID, SOURCE_PROMOTION_RUN_ID, SOURCE_PROMOTION, SOURCE_BACK_VALIDATION, QUALIFIER,
                TARGET_PATHS, TARGET_REGEX, TARGET_PROPERTY, TARGET_PROPERTY_REGEX, TARGET_PROPERTY_TYPE, TARGET_VERSION,
                AUTO_APPROVAL, UPGRADE_BRANCH_PATTERN, UPGRADE_BRANCH, AUTO_APPROVAL_MODE,
                POST_PROCESSING, POST_PROCESSING_CONFIG, VALIDATION_STAMP,
                MOST_RECENT_STATE, RUNNING, STATES, ROUTING, QUEUE,
                REVIEWERS, PR_TITLE_TEMPLATE, PR_BODY_TEMPLATE, PR_BODY_TEMPLATE_FORMAT,
                ADDITIONAL_PATHS, SCHEDULE
            ) VALUES (
                :uuid, :timestamp, :branchId,
                :sourceProject, :sourceBuildId, :sourcePromotionRunId, :sourcePromotion, :sourceBackValidation, :qualifier,
                CAST(:targetPaths as JSONB), :targetRegex, :targetProperty, :targetPropertyRegex, :targetPropertyType, :targetVersion,
                :autoApproval, :upgradeBranchPattern, :upgradeBranch, :autoApprovalMode,
                :postProcessing, CAST(:postProcessingConfig as JSONB), :validationStamp,
                :mostRecentState, :running, CAST(:states as JSONB), :routing, :queue,
                :reviewers, :prTitleTemplate, :prBodyTemplate, :prBodyTemplateFormat,
                CAST(:additionalPaths as JSONB), :schedule
            )
        """.trimIndent()

        val params = mapOf(
            "uuid" to entry.order.uuid,
            "timestamp" to dateTimeForDB(entry.mostRecentState.signature.time),
            "branchId" to entry.order.branch.id(),
            "sourceProject" to entry.order.sourceProject,
            "sourceBuildId" to entry.order.sourceBuildId,
            "sourcePromotionRunId" to entry.order.sourcePromotionRunId,
            "sourcePromotion" to entry.order.sourcePromotion,
            "sourceBackValidation" to entry.order.sourceBackValidation,
            "qualifier" to entry.order.qualifier,
            "targetPaths" to targetPathsJson,
            "targetRegex" to entry.order.targetRegex,
            "targetProperty" to entry.order.targetProperty,
            "targetPropertyRegex" to entry.order.targetPropertyRegex,
            "targetPropertyType" to entry.order.targetPropertyType,
            "targetVersion" to entry.order.targetVersion,
            "autoApproval" to entry.order.autoApproval,
            "upgradeBranchPattern" to entry.order.upgradeBranchPattern,
            "upgradeBranch" to entry.upgradeBranch,
            "autoApprovalMode" to entry.order.autoApprovalMode.name,
            "postProcessing" to entry.order.postProcessing,
            "postProcessingConfig" to writeJson(entry.order.postProcessingConfig),
            "validationStamp" to entry.order.validationStamp,
            "mostRecentState" to entry.mostRecentState.state.name,
            "running" to entry.mostRecentState.state.isRunning,
            "states" to statesJson,
            "routing" to null,
            "queue" to null,
            "reviewers" to reviewersJson,
            "prTitleTemplate" to entry.order.prTitleTemplate,
            "prBodyTemplate" to entry.order.prBodyTemplate,
            "prBodyTemplateFormat" to entry.order.prBodyTemplateFormat,
            "additionalPaths" to additionalPathsJson,
            "schedule" to dateTimeForDB(entry.order.schedule),
        )

        namedParameterJdbcTemplate!!.update(sql, params)
    }

    override fun addState(
        targetBranch: Branch,
        uuid: String,
        routing: String?,
        queue: String?,
        upgradeBranch: String?,
        state: AutoVersioningAuditState,
        vararg data: Pair<String, String>,
    ) {
        // First, fetch the current record
        val selectSql = "SELECT * FROM AV_AUDIT WHERE UUID = :uuid AND BRANCH_ID = :branchId"
        val selectParams = mapOf(
            "uuid" to uuid,
            "branchId" to targetBranch.id()
        )

        val currentStates = namedParameterJdbcTemplate!!.query(selectSql, selectParams) { rs, _ ->
            rs.readStates()
        }.firstOrNull()

        if (currentStates != null) {

            val signature = signature()
            val newState = AutoVersioningAuditEntryState(
                signature = signature,
                state = state,
                data = data.toMap()
            )

            // Create the new states list with the new state prepended
            val updatedStates = listOf(newState) + currentStates
            val updatedStatesJson = writeJson(updatedStates)

            // Determine what to update
            val updateParams = mutableMapOf<String, Any?>(
                "uuid" to uuid,
                "branchId" to targetBranch.id(),
                "states" to updatedStatesJson,
                "mostRecentState" to newState.state.name,
                "timestamp" to dateTimeForDB(newState.signature.time),
                "running" to newState.state.isRunning,
            )

            val setClause = mutableListOf(
                "STATES = CAST(:states as JSONB)",
                "MOST_RECENT_STATE = :mostRecentState",
                "TIMESTAMP = :timestamp",
                "RUNNING = :running",
            )

            if (routing != null) {
                setClause.add("ROUTING = :routing")
                updateParams["routing"] = routing
            }

            if (queue != null) {
                setClause.add("QUEUE = :queue")
                updateParams["queue"] = queue
            }

            if (upgradeBranch != null) {
                setClause.add("UPGRADE_BRANCH = :upgradeBranch")
                updateParams["upgradeBranch"] = upgradeBranch
            }

            val updateSql = """
                UPDATE AV_AUDIT
                SET ${setClause.joinToString(", ")}
                WHERE UUID = :uuid AND BRANCH_ID = :branchId
            """.trimIndent()

            namedParameterJdbcTemplate!!.update(updateSql, updateParams)
        }
    }

    override fun findByUUID(targetBranch: Branch, uuid: String): AutoVersioningAuditEntry? {
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM AV_AUDIT WHERE UUID = :uuid AND BRANCH_ID = :branchId",
            mapOf(
                "uuid" to uuid,
                "branchId" to targetBranch.id()
            )
        ) { rs, _ ->
            rs.toEntry()
        }.firstOrNull()
    }

    override fun findAllBefore(
        retentionDate: LocalDateTime,
        nonRunningOnly: Boolean
    ): List<AutoVersioningAuditEntry> {
        val params = mutableMapOf<String, Any?>()
        val query = queryAllBefore("SELECT *", retentionDate, nonRunningOnly, params)
        return namedParameterJdbcTemplate!!.query(query, params) { rs, _ ->
            rs.toEntry()
        }
    }

    override fun removeAllBefore(retentionDate: LocalDateTime, nonRunningOnly: Boolean): Int {
        val params = mutableMapOf<String, Any?>()
        val query = queryAllBefore("DELETE", retentionDate, nonRunningOnly, params)
        return namedParameterJdbcTemplate!!.update(query, params)
    }

    private fun queryAllBefore(
        header: String,
        retentionDate: LocalDateTime,
        nonRunningOnly: Boolean,
        params: MutableMap<String, Any?>
    ): String {
        var query = """
                $header FROM AV_AUDIT
                WHERE TIMESTAMP < :retentionDate
            """.trimIndent()
        if (nonRunningOnly) {
            query += " AND RUNNING = FALSE"
        }
        params["retentionDate"] = dateTimeForDB(retentionDate)
        return query
    }

    override fun removeAll() {
        @Suppress("SqlWithoutWhere")
        jdbcTemplate!!.update("DELETE FROM AV_AUDIT")
    }

    private fun ResultSet.getJsonStringArray(name: String): List<String> {
        val json = getString(name)
        return readJson(json)?.map { it.asText() } ?: emptyList()
    }

    private fun ResultSet.toEntry(): AutoVersioningAuditEntry {

        val branchId = getInt("BRANCH_ID")
        val branch = branchJdbcRepositoryAccessor.getBranch(ID.of(branchId))

        val targetPath = AutoVersioningSourceConfigPath.toString(getJsonStringArray("TARGET_PATHS"))

        val additionalPaths =
            readJson(this, "ADDITIONAL_PATHS").map {
                it.parse<AutoVersioningSourceConfigPath>()
            }

        return AutoVersioningAuditEntry(
            order = AutoVersioningOrder(
                uuid = getString("UUID"),
                branch = branch,
                sourceProject = getString("SOURCE_PROJECT"),
                sourceBuildId = getNullableInt("SOURCE_BUILD_ID"),
                sourcePromotionRunId = getNullableInt("SOURCE_PROMOTION_RUN_ID"),
                sourcePromotion = getString("SOURCE_PROMOTION"),
                sourceBackValidation = getString("SOURCE_BACK_VALIDATION"),
                qualifier = getString("QUALIFIER"),
                targetPath = targetPath,
                targetRegex = getString("TARGET_REGEX"),
                targetProperty = getString("TARGET_PROPERTY"),
                targetPropertyRegex = getString("TARGET_PROPERTY_REGEX"),
                targetPropertyType = getString("TARGET_PROPERTY_TYPE"),
                targetVersion = getString("TARGET_VERSION"),
                autoApproval = getBoolean("AUTO_APPROVAL"),
                upgradeBranchPattern = getString("UPGRADE_BRANCH_PATTERN"),
                postProcessing = getString("POST_PROCESSING"),
                postProcessingConfig = readJson(this, "POST_PROCESSING_CONFIG"),
                validationStamp = getString("VALIDATION_STAMP"),
                autoApprovalMode = getString("AUTO_APPROVAL_MODE").let { AutoApprovalMode.valueOf(it) },
                reviewers = getJsonStringArray("REVIEWERS"),
                prTitleTemplate = getString("PR_TITLE_TEMPLATE"),
                prBodyTemplate = getString("PR_BODY_TEMPLATE"),
                prBodyTemplateFormat = getString("PR_BODY_TEMPLATE_FORMAT"),
                additionalPaths = additionalPaths,
                schedule = readLocalDateTime("SCHEDULE"),
                pushMode = getString("PUSH_MODE").let { AutoVersioningPushMode.valueOf(it) },
            ),
            audit = readStates(),
            routing = getString("ROUTING"),
            queue = getString("QUEUE"),
            upgradeBranch = getString("UPGRADE_BRANCH"),
        )
    }

    private fun ResultSet.readStates(): List<AutoVersioningAuditEntryState> =
        readJson(this, "STATES")?.let { readStates(it) }
            ?: error("States cannot be empty for an auto-versioning audit entry")

    private fun readStates(json: JsonNode): List<AutoVersioningAuditEntryState> =
        json.map { it.parse<AutoVersioningAuditEntryState>() }

    override fun countByState(state: AutoVersioningAuditState): Int =
        namedParameterJdbcTemplate!!.queryForObject(
            """
                SELECT COUNT(UUID)
                FROM AV_AUDIT
                WHERE MOST_RECENT_STATE = :state
            """,
            params("state", state.name),
            Int::class.java
        ) ?: 0

    override fun auditVersioningEntriesCount(filter: AutoVersioningAuditQueryFilter): Int {
        val params = mutableMapOf<String, Any>()
        val query = auditVersioningEntriesQuery(filter, params, select = "SELECT COUNT(S.UUID)", limits = false)

        // Runs the query
        return namedParameterJdbcTemplate!!.queryForObject(query, params, Int::class.java) ?: 0
    }

    override fun auditVersioningEntries(filter: AutoVersioningAuditQueryFilter): List<AutoVersioningAuditEntry> {
        val params = mutableMapOf<String, Any>()
        val query = auditVersioningEntriesQuery(filter, params)

        // Runs the query
        return namedParameterJdbcTemplate!!.query(query, params) { rs, _ ->
            rs.toEntry()
        }
    }

    override fun findByReady(time: LocalDateTime): List<AutoVersioningAuditEntry> {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM AV_AUDIT
                WHERE (MOST_RECENT_STATE = :createdState AND SCHEDULE IS NULL)
                OR (MOST_RECENT_STATE = :pendingState AND SCHEDULE <= :time)
            """.trimIndent(),
            mapOf(
                "time" to dateTimeForDB(time),
                "createdState" to AutoVersioningAuditState.CREATED.name,
                "pendingState" to AutoVersioningAuditState.PENDING_SCHEDULE.name,
            )
        ) { rs, _ ->
            rs.toEntry()
        }
    }

    private fun auditVersioningEntriesQuery(
        filter: AutoVersioningAuditQueryFilter,
        params: MutableMap<String, Any>,
        select: String = "SELECT * ",
        limits: Boolean = true,
    ): String {
        // Base query
        val queries = mutableListOf<String>()
        val joins = mutableListOf<String>()

        // Filter on uuid
        filter.uuid?.takeIf { it.isNotBlank() }?.let {
            queries += "S.UUID = :uuid"
            params += "uuid" to it
        }
        // Filter on qualifier
        filter.qualifier?.takeIf { it.isNotBlank() }?.let {
            queries += "S.QUALIFIER = :qualifier"
            params += "qualifier" to it
        }
        // Filter on state
        filter.state?.let {
            queries += "S.MOST_RECENT_STATE = :state"
            params += "state" to it.name
        }
        // Filter on state(s)
        if (!filter.states.isNullOrEmpty()) {
            val states = filter.states.joinToString(", ") { "'$it'" }
            queries += "S.MOST_RECENT_STATE IN ($states)"
        }
        // Filter on running state
        filter.running?.let { flag ->
            queries += "S.RUNNING = :running"
            params += "running" to flag
        }
        // Filter on source
        filter.source?.takeIf { it.isNotBlank() }?.let {
            queries += "S.SOURCE_PROJECT = :source"
            params += "source" to it
        }
        // Filter on version
        filter.version?.takeIf { it.isNotBlank() }?.let {
            queries += "S.TARGET_VERSION = :version"
            params += "version" to it
        }
        // Filter on routing
        filter.routing?.takeIf { it.isNotBlank() }?.let {
            queries += "S.ROUTING = :routing"
            params += "routing" to it
        }
        // Filter on queue
        filter.queue?.takeIf { it.isNotBlank() }?.let {
            queries += "S.QUEUE = :queue"
            params += "queue" to it
        }
        // Filter on paths
        filter.targetPaths?.takeIf { it.isNotEmpty() }?.let { paths ->
            queries += """
                array_to_string(array(select jsonb_array_elements_text(TARGET_PATHS::jsonb)), ',') = :targetPaths
            """.trimIndent()
            params += "targetPaths" to paths.joinToString(",")
        }

        // Target project filter
        if (!filter.project.isNullOrBlank()) {
            joins += "INNER JOIN BRANCHES B ON B.ID = S.BRANCH_ID"
            joins += "INNER JOIN PROJECTS P ON P.ID = B.PROJECTID"
            queries += "P.NAME = :project"
            params += "project" to filter.project
            // Target branch filter
            if (!filter.branch.isNullOrBlank()) {
                queries += "B.NAME = :branch"
                params += "branch" to filter.branch
            }
        }

        // Final query
        val query = if (queries.isNotEmpty()) {
            "WHERE " + queries.joinToString(" AND ") { "($it)" }
        } else {
            ""
        }
        val joinQuery = joins.joinToString("\n") { it }
        var sql = """
            $select
            FROM AV_AUDIT S 
            $joinQuery
            $query
        """.trimIndent()
        if (limits) {
            sql += " ORDER BY S.TIMESTAMP DESC LIMIT ${filter.count} OFFSET ${filter.offset}"
        }
        return sql
    }

}