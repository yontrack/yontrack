package net.nemerosa.ontrack.extension.github.ingestion.processing.push.tagging

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayload
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Retrieves the last build having a given promotion.
 */
@Component
class PromotionTaggingStrategy(
    private val structureService: StructureService,
    private val buildFilterService: BuildFilterService,
) : TaggingStrategy<PromotionTaggingStrategyConfig> {

    override val type: String = "promotion"

    override fun findBuild(config: PromotionTaggingStrategyConfig?, branch: Branch, payload: PushPayload): Build? {
        if (config == null) return null
        val promotion = structureService.getPromotionLevelListForBranch(branch.id)
            .firstOrNull { it.name == config.name }
            ?: return null
        return buildFilterService.standardFilterProviderData(1)
            .withWithPromotionLevel(promotion.name)
            .build()
            .filterBranchBuilds(branch)
            .firstOrNull()
    }

    override fun parseAndValidate(config: JsonNode?): PromotionTaggingStrategyConfig? =
        config?.parse() ?: error("Promotion name is required for this strategy.")
}