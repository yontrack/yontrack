package net.nemerosa.ontrack.extension.github.ingestion.processing.push.tagging

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayload
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class CommitPropertyTaggingStrategy(
    private val structureService: StructureService,
) : TaggingStrategy<Any> {

    override val type: String = COMMIT_PROPERTY_TAGGING_STRATEGY_TYPE

    override fun parseAndValidate(config: JsonNode?): Any? = null

    override fun findBuild(config: Any?, branch: Branch, payload: PushPayload): Build? =
        findBuild(config, branch.project, payload)

    fun findBuild(config: Any?, project: Project, payload: PushPayload): Build? {
        val commit = payload.headCommit?.id ?: error("Cannot process tag event because head commit is missing.")
        return structureService.buildSearch(
            project.id,
            BuildSearchForm(
                maximumCount = 1,
                property = GitCommitPropertyType::class.java.name,
                propertyValue = commit,
            )
        ).firstOrNull()
    }

    companion object {
        const val COMMIT_PROPERTY_TAGGING_STRATEGY_TYPE = "commit-property"
    }
}