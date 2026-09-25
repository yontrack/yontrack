package net.nemerosa.ontrack.model.structure

/**
 * One part of the highlighted free text of a [search result][SearchResult]: a sequence of these
 * parts, joined, is an excerpt of the text, the parts matching the query being flagged.
 *
 * The parts are plain text: any markup the text contains is part of the text, never of the
 * highlighting.
 *
 * @property text Part of the text
 * @property match `true` if this part matches the query
 */
data class SearchHighlightPart(
    val text: String,
    val match: Boolean,
)
