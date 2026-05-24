package net.nemerosa.ontrack.extension.av.dispatcher

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.model.structure.Branch
import java.time.LocalDateTime

data class AutoVersioningOrder(
    val uuid: String,
    // Source information
    val sourceProject: String,
    val sourceBuildId: Int?, // Nullable for backward compatibility
    val sourcePromotionRunId: Int?, // Can be null if the AV is not linked to a promotion
    val sourcePromotion: String?, // Can be null if the AV is not linked to a promotion
    val sourceBackValidation: String?,
    val qualifier: String? = null,
    // Target information
    val branch: Branch,
    val targetPath: String,
    val targetRegex: String?,
    val targetProperty: String?,
    val targetPropertyRegex: String?,
    val targetPropertyType: String?,
    val targetVersion: String,
    val autoApproval: Boolean,
    val upgradeBranchPattern: String,
    val postProcessing: String?,
    val postProcessingConfig: JsonNode?,
    val validationStamp: String?,
    val autoApprovalMode: AutoApprovalMode,
    val reviewers: List<String>,
    val prTitleTemplate: String?,
    val prBodyTemplate: String?,
    val prBodyTemplateFormat: String?,
    val additionalPaths: List<AutoVersioningSourceConfigPath>,
    @APIDescription("Schedule time")
    val schedule: LocalDateTime?,
    @APIDescription("Push mode")
    val pushMode: AutoVersioningPushMode = AutoVersioningPushMode.PR,
) {
    /**
     * Gets a meaningful commit message for this order
     */
    @JsonIgnore
    fun getCommitMessage() =
        "[auto-versioning] Upgrade of $sourceProject to version $targetVersion"

    /**
     * Gets the default path
     */
    @get:JsonIgnore
    val defaultPath: AutoVersioningSourceConfigPath = AutoVersioningSourceConfigPath(
        path = targetPath,
        regex = targetRegex,
        property = targetProperty,
        propertyRegex = targetPropertyRegex,
        propertyType = targetPropertyType,
        versionSource = null,
    )

    /**
     * Gets all paths
     */
    @get:JsonIgnore
    val allPaths: List<AutoVersioningSourceConfigPath> = listOf(defaultPath) + additionalPaths
}
