package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.SearchHighlightPart
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Documents found by a search.
 *
 * @property total Total number of matching documents
 * @property facets Number of matching documents per type, for the types having at least one
 * @property items Documents of the page, best first
 */
data class SearchDocumentPage(
    val total: Int,
    val facets: Map<String, Int>,
    val items: List<SearchDocumentHit>,
)

/**
 * One document found by a search.
 *
 * @property score Relevance of the document, the higher the better: `4` for an exact match, `3`
 * for a prefix, between `2` and `3` for a full-text match and between `1` and `2` for a trigram one
 * @property highlight Excerpt of the free text where it matches one of the words of the query, when
 * asked for; `null` when not asked for, or when there is no free text or it does not match
 */
data class SearchDocumentHit(
    val type: String,
    val key: String,
    val projectId: Int?,
    val entity: ProjectEntityID?,
    val title: String,
    val text: String?,
    val data: JsonNode,
    val updatedAt: LocalDateTime,
    val score: Double,
    val highlight: List<SearchHighlightPart>? = null,
)
