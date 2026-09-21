package net.nemerosa.ontrack.extension.av.ci

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.FilterHelper
import net.nemerosa.ontrack.common.mergeList
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.merge
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AutoVersioningBranchCIConfigExtension(
    autoVersioningExtensionFeature: AutoVersioningExtensionFeature,
    private val autoVersioningConfigurationService: AutoVersioningConfigurationService,
    private val branchDisplayNameService: BranchDisplayNameService,
) : AbstractExtension(autoVersioningExtensionFeature), CIConfigExtension<AutoVersioningBranchCIConfig> {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningBranchCIConfigExtension::class.java)

    override val id: String = "autoVersioning"

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AutoVersioningBranchCIConfig::class)

    override fun parseData(data: JsonNode): AutoVersioningBranchCIConfig = data.parse()

    override fun mergeConfig(
        defaults: AutoVersioningBranchCIConfig,
        custom: JsonNode
    ): AutoVersioningBranchCIConfig = mergeList<JsonNode>(
        target = defaults.configurations.map { it.asJson() },
        changes = custom.path("configurations").values().map { it },
        idFn = { cfg -> cfg.parse<AVCfgID>() }
    ) { e, existing ->
        existing.merge(e)
    }.map { e ->
        e.parse<AutoVersioningSourceConfig>()
    }.let {
        AutoVersioningBranchCIConfig(
            branchFilter = defaults.branchFilter,
            configurations = it
        )
    }

    override val projectEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BRANCH)

    override fun configure(
        entity: ProjectEntity,
        data: AutoVersioningBranchCIConfig
    ) {
        val branch = entity as Branch
        val scmBranch = branchDisplayNameService.getBranchDisplayName(
            branch = branch,
            policy = BranchNamePolicy.DISPLAY_NAME_ONLY
        )
        val included = FilterHelper.includes(
            text = scmBranch,
            includes = data.branchFilter.includes,
            excludes = data.branchFilter.excludes
        )
        if (included) {
            logger.info("[av-ci-config] Setting auto-versioning configuration for branch {}", branch.entityDisplayName)
            autoVersioningConfigurationService.setupAutoVersioning(
                branch,
                config = AutoVersioningConfig(
                    configurations = data.configurations
                )
            )
        } else {
            logger.info("[av-ci-config] Unsetting auto-versioning configuration for branch {}", branch.entityDisplayName)
            autoVersioningConfigurationService.setupAutoVersioning(branch, null)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AVCfgID(
        val sourceProject: String,
        val sourceBranch: String,
        val qualifier: String? = null,
    )
}