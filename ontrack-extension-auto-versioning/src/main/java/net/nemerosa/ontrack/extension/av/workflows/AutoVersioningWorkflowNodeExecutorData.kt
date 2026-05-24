package net.nemerosa.ontrack.extension.av.workflows

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath

data class AutoVersioningWorkflowNodeExecutorData(
    val targetProject: String,
    val targetBranch: String,
    val targetVersion: String,
    val targetPath: String,
    val targetRegex: String? = null,
    val targetProperty: String? = null,
    val targetPropertyRegex: String? = null,
    val targetPropertyType: String? = null,
    val autoApproval: Boolean = true,
    val upgradeBranchPattern: String = AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
    val postProcessing: String? = null,
    val postProcessingConfig: JsonNode? = null,
    val validationStamp: String? = null,
    val autoApprovalMode: AutoApprovalMode = AutoApprovalMode.DEFAULT_AUTO_APPROVAL_MODE,
    val reviewers: List<String> = emptyList(),
    val prTitleTemplate: String? = null,
    val prBodyTemplate: String? = null,
    val prBodyTemplateFormat: String? = null,
    val additionalPaths: List<AutoVersioningSourceConfigPath> = emptyList(),
    val pushMode: AutoVersioningPushMode = AutoVersioningPushMode.PR,
) {

    fun resolve(templating: (text: String) -> String) = AutoVersioningWorkflowNodeExecutorData(
        targetProject = templating(targetProject),
        targetBranch = templating(targetBranch),
        targetVersion = templating(targetVersion),
        targetPath = targetPath,
        targetRegex = targetRegex,
        targetProperty = targetProperty,
        targetPropertyRegex = targetPropertyRegex,
        targetPropertyType = targetPropertyType,
        autoApproval = autoApproval,
        upgradeBranchPattern = upgradeBranchPattern,
        postProcessing = postProcessing,
        postProcessingConfig = postProcessingConfig,
        validationStamp = validationStamp,
        autoApprovalMode = autoApprovalMode,
        reviewers = reviewers,
        prTitleTemplate = prTitleTemplate,
        prBodyTemplate = prBodyTemplate,
        prBodyTemplateFormat = prBodyTemplateFormat,
        additionalPaths = additionalPaths,
        pushMode = pushMode,
    )

}
