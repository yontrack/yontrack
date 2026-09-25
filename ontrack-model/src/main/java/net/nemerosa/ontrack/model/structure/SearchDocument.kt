package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * What an indexer writes about one findable thing, so that search can match it and render it
 * without reading the thing itself again.
 *
 * A search document belongs to exactly one [search result type][SearchResultType] and is unique by
 * its [key] within that type. See `doc/dev-guide/search-indexer.md`.
 *
 * @property type ID of the [search result type][SearchResultType] of this document
 * @property key Unique within the [type], for example the ID of the entity. Writing a document with
 * an existing type and key replaces it.
 * @property projectId ID of the project the document belongs to. It drives who can see the
 * document, and the document is deleted with its project. `null` for a document which belongs to no
 * project: only the users granted the [global function][SearchDocumentIndexer.globalFunction] of
 * its indexer can see it.
 * @property entity Optional reference to the project entity being described
 * @property title Shown as the title of the result, and the strongest field for matching
 * @property identifiers What the thing answers to, for exact, prefix and fuzzy matching: names,
 * display names, hashes, keys. Case does not matter.
 * @property text Optional free text, matched with the lowest weight: descriptions, commit messages
 * @property data Everything the frontend needs to render and link the result
 * @property updatedAt Recency of the thing being described, breaking the ties between equally
 * relevant results (newest first). `null` means "now", the time of the indexation.
 */
data class SearchDocument(
    val type: String,
    val key: String,
    val projectId: Int?,
    val entity: ProjectEntityID?,
    val title: String,
    val identifiers: List<String>,
    val text: String?,
    val data: JsonNode,
    val updatedAt: LocalDateTime? = null,
)
