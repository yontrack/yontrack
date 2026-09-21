package net.nemerosa.ontrack.boot

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class BranchSearchProvider(
    private val structureService: StructureService,
    private val searchIndexService: SearchIndexService,
) : SearchIndexer<BranchSearchItem>, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = BRANCH_SEARCH_RESULT_TYPE,
        name = "Branch",
        description = "Branch name in Ontrack",
        order = SearchResultType.ORDER_PROJECT + 1,
    )

    override val indexerName: String = "Branches"

    override val indexName: String = BRANCH_SEARCH_INDEX

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.run {
            autoCompleteSettings()
        }.run {
            mappings { mappings ->
                mappings
                    .autoCompleteText(BranchSearchItem::name)
                    .autoCompleteText(BranchSearchItem::project)
                    .text(BranchSearchItem::description)
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
                    BranchSearchItem::name to 5.0,
                    BranchSearchItem::project to 3.0,
                    BranchSearchItem::description to 1.0,
                )
        }
    }

    override fun indexAll(processor: (BranchSearchItem) -> Unit) {
        structureService.projectList.forEach {
            structureService.getBranchesForProject(it.id).forEach { branch ->
                processor(branch.asSearchItem())
            }
        }
    }

    override fun toSearchResult(id: String, score: Double, source: JsonNode): SearchResult? =
        structureService.findBranchByID(ID.of(id.toInt()))?.run {
            SearchResult(
                title = entityDisplayName,
                description = description ?: "",
                accuracy = score,
                type = searchResultType,
                data = mapOf(
                    SearchResult.SEARCH_RESULT_BRANCH to this
                )
            )
        }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_BRANCH -> {
                val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
                searchIndexService.createSearchIndex(this, branch.asSearchItem())
            }

            EventFactory.UPDATE_BRANCH -> {
                val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
                searchIndexService.updateSearchIndex(this, branch.asSearchItem())
            }

            EventFactory.DELETE_BRANCH -> {
                val branchId = event.getIntValue("BRANCH_ID")
                searchIndexService.deleteSearchIndex(this, branchId)
            }
        }
    }

    private fun Branch.asSearchItem() = BranchSearchItem(this)
}

/**
 * Index name for the branches
 */
const val BRANCH_SEARCH_INDEX = "branches"

/**
 * Search result type
 */
const val BRANCH_SEARCH_RESULT_TYPE = "branch"

class BranchSearchItem(
    override val id: String,
    val name: String,
    val description: String,
    val project: String
) : SearchItem {

    constructor(branch: Branch) : this(
        id = branch.id.toString(),
        name = branch.name,
        description = branch.description ?: "",
        project = branch.project.name
    )

    override val fields: Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "project" to project
    )
}