package net.nemerosa.ontrack.extension.av.config

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.IgnoreRef
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import org.apache.commons.codec.digest.DigestUtils

@APIDescription("Configuration of the auto versioning for one source.")
data class AutoVersioningSourceConfig(
    @APIDescription("Project to watch")
    @JsonAlias("project") // Compat. with old Jenkins pipeline library
    val sourceProject: String,
    @APIDescription("Branches to watch using a regular expression")
    @JsonAlias("branch") // Compat. with the old Jenkins pipeline library
    val sourceBranch: String,
    @APIDescription("Promotion to watch")
    @JsonAlias("promotion") // Compat. with the old Jenkins pipeline library
    val sourcePromotion: String,
    @APIDescription("Comma-separated list of file to update with the new version")
    @JsonAlias("path") // Compat. with the old Jenkins pipeline library
    val targetPath: String,
    @APIDescription("Regex to use in the target file to identify the line to replace with the new version. The first matching group must be the version.")
    @JsonAlias("regex") // Compat. with the old Jenkins pipeline library
    val targetRegex: String? = null,
    @APIDescription("Optional replacement for the regex, using only a property name")
    @JsonAlias("property") // Compat. with the old Jenkins pipeline library
    val targetProperty: String? = null,
    @APIDescription("Optional regex to use on the targetProperty value")
    @JsonAlias("propertyRegex") // Compat. with the old Jenkins pipeline library
    val targetPropertyRegex: String? = null,
    @APIDescription("When targetProperty is defined, defines the type of property (defaults to Java properties file, but could be NPM, etc.)")
    @JsonAlias("propertyType") // Compat. with the old Jenkins pipeline library
    val targetPropertyType: String? = null,
    @APIDescription("Check if the PR must be approved automatically or not (`true` by default)")
    val autoApproval: Boolean? = null,
    @APIDescription("Prefix to use for the upgrade branch in Git, defaults to `feature/auto-upgrade-<project>-<version>-<branch>`")
    val upgradeBranchPattern: String? = null,
    @APIDescription("Type of post processing to launch after the version has been updated")
    val postProcessing: String? = null,
    @APIDescription("Configuration of the post processing")
    val postProcessingConfig: JsonNode? = null,
    @APIDescription("Validation stamp to create on auto versioning (optional)")
    val validationStamp: String? = null,
    @APIDescription("Auto approval mode")
    val autoApprovalMode: AutoApprovalMode? = null,
    @APIDescription("Build link creation. True by default.")
    val buildLinkCreation: Boolean? = null,
    @APIDescription("List of notifications subscriptions to setup for this auto versioning")
    @ListRef(embedded = true, suffix = "Input")
    val notifications: List<AutoVersioningNotification>? = null,
    @APIDescription("Qualifier for the build link to create (when links are created)")
    val qualifier: String? = null,
    @APIDescription("Validation stamp to create on the source build (optional)")
    val backValidation: String? = null,
    @APIDescription("Source of the version for the build. By default, uses the build label is the source project is configured so, or the build name itself. This allows the customization of this behavior.")
    val versionSource: String? = null,
    @APIDescription("List of reviewers to always set on the pull request created by the auto versioning")
    @ListRef
    val reviewers: List<String>? = null,
    @APIDescription("Template for the title of the pull request (optional)")
    val prTitleTemplate: String? = null,
    @APIDescription("Template for the body of the pull request (optional)")
    val prBodyTemplate: String? = null,
    @APIDescription("Template format for the body of the pull request (plain by default, html, markdown as possible values)")
    val prBodyTemplateFormat: String? = null,
    @APIDescription("Additional paths to change")
    @ListRef(embedded = true, suffix = "Input")
    val additionalPaths: List<AutoVersioningSourceConfigPath>? = null,
    @APIDescription("Cron schedule (when to start applying queued requests)")
    val cronSchedule: String? = null,
    @APIDescription("Set if this configuration is disabled")
    val disabled: Boolean? = null,
    @APIDescription("Push mode")
    val pushMode: AutoVersioningPushMode? = AutoVersioningPushMode.PR,
) {

    /**
     * Gets the default path
     */
    @get:JsonIgnore
    @IgnoreRef
    @DocumentationIgnore
    @APIIgnore
    val defaultPath: AutoVersioningSourceConfigPath = AutoVersioningSourceConfigPath(
        path = targetPath,
        regex = targetRegex,
        property = targetProperty,
        propertyRegex = targetPropertyRegex,
        propertyType = targetPropertyType,
        versionSource = null,
    )

    /**
     * Validates that this configuration is consistent. Checks that at least the regex or the property is set,
     * and that, if it is defined, the upgrade branch pattern contains the `<version>` token.
     */
    fun validate() {
        validateTargetRegex()
        validateUpgradeBranchPrefix()
    }

    private fun validateTargetRegex() {
        if (targetRegex.isNullOrBlank() && targetProperty.isNullOrBlank()) {
            throw MissingTargetRegexOrPropertyException()
        }
    }

    private fun validateUpgradeBranchPrefix() {
        if (upgradeBranchPattern != null && !upgradeBranchPattern.contains("<version>")) {
            throw UpgradeBranchPrefixNoVersionException(upgradeBranchPattern)
        }
    }

    companion object {

        val DEFAULT_AUTO_APPROVAL_MODE = AutoApprovalMode.CLIENT

        const val DEFAULT_UPGRADE_BRANCH_PATTERN = "feature/auto-upgrade-<project>-<version>-<branch>"

        /**
         * Given a [branch pattern][upgradeBranchPattern], computes an actual branch name
         * by replacing the `<project>`, `<branch>` and `<version>` tokens by their respective values.
         *
         * @param upgradeBranchPattern Branch pattern
         * @param project Replacement of the `<project>` token
         * @param version Replacement of the `<version>` token
         * @param branch Replacement of the `<branch>` token
         * @param promotion Source promotion
         * @param paths List of updated paths
         * @param branchHash Flag to indicate if the [branch] value must be hashed before being injected (because
         * a complete branch name would be too long)
         * @return Branch name to use
         */
        fun getUpgradeBranch(
            upgradeBranchPattern: String,
            project: String,
            version: String,
            branch: String,
            promotion: String,
            paths: List<String>,
            branchHash: Boolean,
        ): String {

            // Makes sure the <branch> pattern is always included
            val actualPattern = if (upgradeBranchPattern.contains("<branch>")) {
                upgradeBranchPattern
            } else {
                "${upgradeBranchPattern}-<branch>"
            }

            val branchToken = if (branchHash) {
                DigestUtils.md5Hex("$branch,$promotion,${paths.joinToString(",")}")
            } else {
                branch
            }
            return actualPattern
                .replace("<project>", project)
                .replace("<version>", version)
                .replace("<branch>", branchToken)
        }

    }

    fun postDeserialize() =
        AutoVersioningSourceConfig(
            sourceProject = sourceProject,
            sourceBranch = sourceBranch,
            sourcePromotion = sourcePromotion,
            targetPath = targetPath,
            targetRegex = targetRegex,
            targetProperty = targetProperty,
            targetPropertyRegex = targetPropertyRegex,
            targetPropertyType = targetPropertyType,
            autoApproval = autoApproval,
            upgradeBranchPattern = upgradeBranchPattern,
            postProcessing = postProcessing,
            postProcessingConfig = postProcessingConfig?.takeIf { !it.isNull },
            validationStamp = validationStamp,
            autoApprovalMode = autoApprovalMode,
            buildLinkCreation = buildLinkCreation,
            notifications = notifications,
            qualifier = qualifier,
            backValidation = backValidation,
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