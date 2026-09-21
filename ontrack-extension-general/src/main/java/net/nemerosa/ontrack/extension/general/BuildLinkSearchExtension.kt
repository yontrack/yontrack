package net.nemerosa.ontrack.extension.general

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.asMap
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.events.BuildLinkListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Searching on the build links.
 */
@Component
class BuildLinkSearchExtension(
    extensionFeature: GeneralExtensionFeature,
    private val structureService: StructureService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val searchIndexService: SearchIndexService
) : AbstractExtension(extensionFeature), SearchIndexer<BuildLinkSearchItem>, BuildLinkListener {

    override val indexerName: String = "Build links"

    override val indexName: String = BUILD_LINK_SEARCH_INDEX

    override val indexerSchedule: Schedule = Schedule.EVERY_DAY

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = "build-link",
        name = "Linked Build",
        description = "Reference to a linked project and build, using format project:[build] where the target build is optional",
        order = SearchResultType.ORDER_PROPERTIES + 30,
    )

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.run {
            autoCompleteSettings()
        }.run {
            mappings { mappings ->
                mappings
                    .id(BuildLinkSearchItem::fromBuildId)
                    .id(BuildLinkSearchItem::targetBuildId)
                    .autoCompleteText(BuildLinkSearchItem::targetProject)  // Changed from keyword
                    .autoCompleteText(BuildLinkSearchItem::targetBuild)    // Changed from keyword
                    .autoCompleteText(BuildLinkSearchItem::targetKey)      // Changed from text
            }
        }

    override fun buildQuery(
        q: Query.Builder,
        token: String
    ): ObjectBuilder<Query> {
        return q.multiMatch { m ->
            m.query(token)
                .type(TextQueryType.BestFields)
                .fields(
                    BuildLinkSearchItem::targetProject to 3.0,  // Project name priority
                    BuildLinkSearchItem::targetBuild to 2.0,    // Build name secondary
                    BuildLinkSearchItem::targetKey to 1.0,      // Combined key lowest
                )
        }
    }

    override fun indexAll(processor: (BuildLinkSearchItem) -> Unit) {
        structureService.forEachBuildLink { from, to, qualifier ->
            process(from, to, qualifier, processor)
        }
    }

    override fun toSearchResult(id: String, score: Double, source: JsonNode): SearchResult? {
        // Parsing
        val item = source.parseOrNull<BuildLinkSearchItem>() ?: return null
        // Source and target build for the link
        val sourceBuild = structureService.findBuildByID(ID.of(item.fromBuildId)) ?: return null
        val targetBuild = structureService.findBuildByID(ID.of(item.targetBuildId)) ?: return null
        val qualifier = item.qualifier
        // Result
        return SearchResult(
            title = sourceBuild.entityDisplayName,
            description = "Linked to ${item.targetProject}:${item.targetBuild}",
            accuracy = score,
            type = searchResultType,
            // Puts source & linked build, and qualifier
            data = mapOf(
                "sourceBuild" to sourceBuild,
                "targetBuild" to targetBuild,
                "qualifier" to qualifier,
            ),
        )
    }

    override fun onBuildLinkAdded(from: Build, to: Build, qualifier: String) {
        process(from, to, qualifier) { item ->
            searchIndexService.createSearchIndex(this, item)
        }
    }

    override fun onBuildLinkDeleted(from: Build, to: Build, qualifier: String) {
        process(from, to, qualifier) { item ->
            searchIndexService.deleteSearchIndex(this, item.documentId)
        }
    }

    private fun process(from: Build, to: Build, qualifier: String, processor: (BuildLinkSearchItem) -> Unit) {
        processor(BuildLinkSearchItem(from, to, qualifier = qualifier))
        // Alternative name
        val otherName = buildDisplayNameService.getFirstBuildDisplayName(to)
        if (otherName != null) {
            processor(BuildLinkSearchItem(from, to, otherName))
        }
    }
}

/**
 * Index name for the build links
 */
const val BUILD_LINK_SEARCH_INDEX = "build-links"

class BuildLinkSearchItem(
    val fromBuildId: Int,
    val targetBuildId: Int,
    val targetProject: String,
    val targetBuild: String,
    val qualifier: String,
) : SearchItem {

    constructor(
        from: Build,
        to: Build,
        targetBuildName: String = to.name,
        qualifier: String = BuildLink.DEFAULT
    ) : this(
        fromBuildId = from.id(),
        targetBuildId = to.id(),
        targetProject = to.project.name,
        targetBuild = targetBuildName,
        qualifier = qualifier,
    )

    override val id: String = "$fromBuildId::$targetBuildId"

    val targetKey = "$targetProject:$targetBuild"

    override val fields: Map<String, Any?> = asMap(
        this::fromBuildId,
        this::targetBuildId,
        this::targetProject,
        this::targetBuild,
        this::targetKey,
        this::qualifier,
    )

}