package net.nemerosa.ontrack.extension.av.audit

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.repository.BranchJdbcRepositoryAccessor
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Migration of the audit records into a table.
 */
@Component
class AutoVersioningAuditStoreMigration(
    dataSource: DataSource,
    private val branchJdbcRepositoryAccessor: BranchJdbcRepositoryAccessor,
    private val autoVersioningAuditStore: AutoVersioningAuditStore,
) : StartupService, AbstractJdbcRepository(dataSource) {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningAuditStoreMigration::class.java)

    override fun getName(): String = "Storing the auto-versioning audit records into a table"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        // Getting through all records, converting them into a table record
        namedParameterJdbcTemplate!!.query(
            """
                SELECT NAME, BRANCH, JSON
                FROM ENTITY_DATA_STORE
                WHERE CATEGORY = :category
            """.trimIndent(),
            mapOf(
                "category" to CATEGORY
            )
        ) { rs ->
            val uuid = rs.getString("NAME")
            val branchId = rs.getInt("BRANCH")
            val json = readJson(rs, "JSON")

            // Converting the states' states
            json.path("states").forEach { state ->
                val oldState = state.path("state").asText()
                if (oldState == "PROCESSING_CANCELLED") {
                    (state as ObjectNode).put("state", AutoVersioningAuditState.THROTTLED.name)
                }
            }

            val branch = branchJdbcRepositoryAccessor.getBranch(ID.of(branchId))
            val data = json.parseOrNull<AutoVersioningAuditStoreData>()
            if (data != null) {
                (autoVersioningAuditStore as AutoVersioningAuditStoreImpl).saveEntry(
                    AutoVersioningAuditEntry(
                        order = AutoVersioningOrder(
                            uuid = uuid,
                            sourceProject = data.sourceProject,
                            sourceBuildId = data.sourceBuildId,
                            sourcePromotionRunId = data.sourcePromotionRunId,
                            sourcePromotion = data.sourcePromotion,
                            sourceBackValidation = data.sourceBackValidation,
                            qualifier = data.qualifier,
                            branch = branch,
                            targetPath = data.targetPaths.joinToString(","),
                            targetRegex = data.targetRegex,
                            targetProperty = data.targetProperty,
                            targetPropertyRegex = data.targetPropertyRegex,
                            targetPropertyType = data.targetPropertyType,
                            targetVersion = data.targetVersion,
                            autoApproval = data.autoApproval,
                            upgradeBranchPattern = data.upgradeBranchPattern,
                            postProcessing = data.postProcessing,
                            postProcessingConfig = data.postProcessingConfig,
                            validationStamp = data.validationStamp,
                            autoApprovalMode = data.autoApprovalMode,
                            reviewers = data.reviewers ?: emptyList(),
                            prTitleTemplate = data.prTitleTemplate,
                            prBodyTemplate = data.prBodyTemplate,
                            prBodyTemplateFormat = data.prBodyTemplateFormat,
                            additionalPaths = data.additionalPaths ?: emptyList(),
                            schedule = null,
                            pushMode = AutoVersioningPushMode.PR,
                        ),
                        audit = data.states,
                        routing = data.routing,
                        queue = data.queue,
                        upgradeBranch = data.upgradeBranch,
                    )
                )
            } else {
                logger.warn("Cannot parse audit record {} for branch {}. Not migrated.", uuid, branch.name)
            }

        }

        // Deletion of existing records
        namedParameterJdbcTemplate!!.update(
            """
                DELETE FROM ENTITY_DATA_STORE
                WHERE CATEGORY = :category
            """.trimIndent(),
            mapOf(
                "category" to CATEGORY
            )
        )
    }

    companion object {
        internal const val CATEGORY = "AutoVersioningAuditStore"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AutoVersioningAuditStoreData(
        val sourceProject: String,
        val sourceBuildId: Int?, // Nullable for backward compatibility
        val sourcePromotionRunId: Int?, // Nullable for backward compatibility
        val sourcePromotion: String?, // Nullable for backward compatibility
        val sourceBackValidation: String?, // Nullable for backward compatibility
        val qualifier: String?, // Nullable for backward compatibility
        val targetPaths: List<String>,
        val targetRegex: String?,
        val targetProperty: String?,
        val targetPropertyRegex: String?,
        val targetPropertyType: String?,
        val targetVersion: String,
        val autoApproval: Boolean,
        val upgradeBranchPattern: String,
        val upgradeBranch: String?,
        val postProcessing: String?,
        val postProcessingConfig: JsonNode?,
        val validationStamp: String?,
        val autoApprovalMode: AutoApprovalMode,
        val states: List<AutoVersioningAuditEntryState>,
        val routing: String,
        val queue: String?,
        val reviewers: List<String>?, // Nullable for backward compatibility
        val prTitleTemplate: String?,
        val prBodyTemplate: String?,
        val prBodyTemplateFormat: String?,
        val additionalPaths: List<AutoVersioningSourceConfigPath>?,
    )
}