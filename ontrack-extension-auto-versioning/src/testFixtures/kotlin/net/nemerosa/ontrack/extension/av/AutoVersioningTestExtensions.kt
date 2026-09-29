package net.nemerosa.ontrack.extension.av

import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.model.structure.Branch

fun AutoVersioningConfigurationService.setAutoVersioning(
    branch: Branch,
    init: AutoVersioningSetup.() -> Unit,
) {
    val setup = AutoVersioningSetup()
    setup.init()
    setupAutoVersioning(
        branch,
        AutoVersioningConfig(
            setup.configurations
        )
    )
}

class AutoVersioningSetup {

    val configurations = mutableListOf<AutoVersioningSourceConfig>()

    fun autoVersioningConfig(
        init: AutoVersioningConfigSetup.() -> Unit,
    ) {
        val setup = AutoVersioningConfigSetup()
        setup.init()
        configurations += setup()
    }

}

class AutoVersioningConfigSetup {

    var project: String? = null
    var branch: String = "main"
    var promotion: String = "IRON"

    var targetProperty = "version"

    var validationStamp: String? = null
    var qualifier: String? = null
    var versionSource: String? = null

    operator fun invoke() = AutoVersioningSourceConfig(
        sourceProject = project ?: throw IllegalStateException("Missing source project"),
        sourceBranch = branch,
        sourcePromotion = promotion,
        targetPath = "gradle.properties",
        targetRegex = null,
        targetProperty = targetProperty,
        targetPropertyRegex = null,
        targetPropertyType = null,
        autoApproval = null,
        upgradeBranchPattern = null,
        postProcessing = null,
        postProcessingConfig = null,
        validationStamp = validationStamp,
        autoApprovalMode = AutoApprovalMode.SCM,
        qualifier = qualifier,
        reviewers = null,
        versionSource = versionSource,
        prTitleTemplate = null,
        prBodyTemplate = null,
        prBodyTemplateFormat = null,
    )

}