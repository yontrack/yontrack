package net.nemerosa.ontrack.boot.search

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Type of the [TestElasticSearchIndexer]
 */
const val TEST_ELASTIC_SEARCH_RESULT_TYPE = "test-elastic"

/**
 * A search result type still on Elasticsearch, for the tests of the routing between the two
 * backends, independent of which production indexers are migrated. Goes with the router (#1882).
 */
@Component
class TestElasticSearchIndexer(
    private val searchIndexService: SearchIndexService,
) : SearchIndexer<TestElasticSearchItem> {

    override val indexerName: String = "Test Elasticsearch"

    override val indexName: String = TEST_ELASTIC_SEARCH_RESULT_TYPE

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = TEST_ELASTIC_SEARCH_RESULT_TYPE,
        name = "Test Elasticsearch",
        description = "Test type searched on Elasticsearch",
        order = SearchResultType.ORDER_PROPERTIES + 1000,
    )

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.mappings { mappings ->
            mappings.keyword(TestElasticSearchItem::value)
        }

    override fun buildQuery(q: Query.Builder, token: String): ObjectBuilder<Query> =
        q.term { term ->
            term.field(TestElasticSearchItem::value.name)
                .value(token)
                .caseInsensitive(true)
        }

    override fun indexAll(processor: (TestElasticSearchItem) -> Unit) {
    }

    override fun toSearchResult(id: String, score: Double, source: JsonNode): SearchResult? =
        source.parseOrNull<TestElasticSearchItem>()?.let { item ->
            SearchResult(
                title = item.value,
                description = "",
                accuracy = score,
                type = searchResultType,
                data = mapOf("value" to item.value),
            )
        }

    /**
     * Indexes a value
     */
    fun index(value: String) {
        searchIndexService.createSearchIndex(this, TestElasticSearchItem(value))
    }
}

data class TestElasticSearchItem(
    val value: String,
) : SearchItem {
    override val id: String get() = value
    override val fields: Map<String, Any?> get() = mapOf("value" to value)
}
