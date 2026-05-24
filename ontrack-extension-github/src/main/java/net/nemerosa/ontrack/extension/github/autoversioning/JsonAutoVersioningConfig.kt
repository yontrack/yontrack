package net.nemerosa.ontrack.extension.github.autoversioning

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath

/**
 * Json representation of an auto versioning config
 */
data class JsonAutoVersioningConfig(
    val project: String,
    val branch: String,
    val promotion: String,
    val path: String,
    val regex: String?,
    val property: String?,
    @JsonProperty("property-regex")
    val propertyRegex: String?,
    @JsonProperty("property-type")
    val propertyType: String?,
    @JsonProperty("auto-approval")
    val autoApproval: Boolean?,
    @JsonProperty("upgrade-branch-pattern")
    val upgradeBranchPattern: String?,
    @JsonProperty("post-processing")
    val postProcessing: String?,
    @JsonProperty("post-processing-config")
    val postProcessingConfig: JsonNode?,
    @JsonProperty("validation-stamp")
    val validationStamp: String?,
    @JsonProperty("auto-approval-mode")
    val autoApprovalMode: AutoApprovalMode?,
    val qualifier: String?,
    val versionSource: String? = null,
    val reviewers: List<String>? = null,
    val prTitleTemplate: String? = null,
    val prBodyTemplate: String? = null,
    val prBodyTemplateFormat: String? = null,
    val additionalPaths: List<AutoVersioningSourceConfigPath>? = null,
    @APIDescription("Cron schedule (when to start applying queued requests)")
    val cronSchedule: String? = null,
    val disabled: Boolean? = null,
    @APIDescription("Push mode")
    val pushMode: AutoVersioningPushMode = AutoVersioningPushMode.PR,
) {
    fun toConfig() = AutoVersioningSourceConfig(
        sourceProject = project,
        sourceBranch = branch,
        sourcePromotion = promotion,
        targetPath = path,
        targetRegex = regex,
        targetProperty = property,
        targetPropertyRegex = propertyRegex,
        targetPropertyType = propertyType,
        autoApproval = autoApproval,
        upgradeBranchPattern = upgradeBranchPattern,
        postProcessing = postProcessing,
        postProcessingConfig = postProcessingConfig,
        validationStamp = validationStamp,
        autoApprovalMode = autoApprovalMode,
        qualifier = qualifier,
        versionSource = versionSource,
        reviewers = reviewers,
        prTitleTemplate = prTitleTemplate,
        prBodyTemplate = prBodyTemplate,
        prBodyTemplateFormat = prBodyTemplateFormat,
        additionalPaths = additionalPaths,
        cronSchedule = cronSchedule,
        disabled = disabled,
        pushMode = pushMode,
    )
}