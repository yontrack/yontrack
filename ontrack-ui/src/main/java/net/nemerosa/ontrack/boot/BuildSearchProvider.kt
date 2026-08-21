package net.nemerosa.ontrack.boot

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class BuildSearchProvider(
    private val structureService: StructureService,
    private val searchIndexService: SearchIndexService,
    private val buildDisplayNameService: BuildDisplayNameService,
) : SearchIndexer<BuildSearchItem>, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = BUILD_SEARCH_RESULT_TYPE,
        name = "Build",
        description = "Build name in Ontrack",
        order = SearchResultType.ORDER_PROJECT + 2,
    )

    override val indexerName: String = "Builds"
    override val indexName: String = BUILD_SEARCH_INDEX

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.run {
            autoCompleteSettings()
        }.run {
            mappings { mappings ->
                mappings
                    .autoCompleteTextWithExactMatch(BuildSearchItem::name)
                    .autoCompleteTextWithExactMatch(BuildSearchItem::displayName)
                    .text(BuildSearchItem::description)
            }
        }

    override fun buildQuery(
        q: Query.Builder,
        token: String
    ): ObjectBuilder<Query> {
        return q.bool { b ->
            b
                .should { s ->
                    s.multiMatch { m ->
                        m.query(token)
                            .type(TextQueryType.BestFields)
                            .fields(
                                BuildSearchItem::name to 3.0,
                                BuildSearchItem::displayName to 3.0,
                                BuildSearchItem::description to null,
                            )
                    }
                }
                // A build whose name or display name is exactly the token is what the user is
                // looking for: it must outrank the builds merely sharing a prefix with it.
                .should { s -> s.exactMatch(BuildSearchItem::name, token) }
                .should { s -> s.exactMatch(BuildSearchItem::displayName, token) }
        }
    }

    override fun indexAll(processor: (BuildSearchItem) -> Unit) {
        structureService.projectList.forEach { project ->
            structureService.getBranchesForProject(project.id).forEach { branch ->
                structureService.forEachBuild(branch, BuildSortDirection.FROM_OLDEST) { build ->
                    processor(
                        BuildSearchItem(
                            build = build,
                            displayName = buildDisplayNameService.getFirstBuildDisplayName(build),
                        )
                    )
                    true // Going on
                }
            }
        }
    }

    override fun toSearchResult(id: String, score: Double, source: JsonNode): SearchResult? =
        structureService.findBuildByID(ID.of(id.toInt()))?.run {
            val displayName = buildDisplayNameService.getBuildDisplayNameOrName(this)
            SearchResult(
                title = displayName,
                description = description ?: "",
                accuracy = score,
                type = searchResultType,
                data = mapOf(
                    SearchResult.SEARCH_RESULT_BUILD to this,
                    SearchResult.SEARCH_RESULT_BUILD_RELEASE to displayName,
                ),
            )
        }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_BUILD -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                searchIndexService.createSearchIndex(
                    this,
                    BuildSearchItem(
                        build = build,
                        displayName = buildDisplayNameService.getFirstBuildDisplayName(build),
                    )
                )
            }

            EventFactory.UPDATE_BUILD -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                searchIndexService.updateSearchIndex(
                    this,
                    BuildSearchItem(
                        build = build,
                        displayName = buildDisplayNameService.getFirstBuildDisplayName(build),
                    )
                )
            }

            EventFactory.DELETE_BUILD -> {
                val buildId = event.getIntValue("BUILD_ID")
                searchIndexService.deleteSearchIndex(this, buildId)
            }

            EventFactory.UPDATE_BUILD_DISPLAY_NAME -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                searchIndexService.updateSearchIndex(
                    this,
                    BuildSearchItem(
                        build = build,
                        displayName = buildDisplayNameService.getFirstBuildDisplayName(build),
                    )
                )
            }
        }
    }
}

/**
 * Index name for the builds
 */
const val BUILD_SEARCH_INDEX = "builds"

/**
 * Search result type
 */
const val BUILD_SEARCH_RESULT_TYPE = "build"

data class BuildSearchItem(
    override val id: String,
    val name: String,
    val displayName: String,
    val description: String,
) : SearchItem {
    constructor(build: Build, displayName: String?) : this(
        id = build.id().toString(),
        name = build.name,
        displayName = displayName ?: "",
        description = build.description ?: ""
    )

    override val fields: Map<String, Any?> = mapOf(
        "name" to name,
        "displayName" to displayName,
        "description" to description
    )

}