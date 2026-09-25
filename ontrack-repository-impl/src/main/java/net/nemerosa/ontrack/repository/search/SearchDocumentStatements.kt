package net.nemerosa.ontrack.repository.search

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource

/**
 * The statements of a search, in the order they run.
 *
 * @property facets Count of the candidates per type. `null` for the best rows of each type, which
 * carry the count of their type: the candidates are then scanned once, not twice
 * @property rows Rows of the page, or the best rows of each type. Only run after the facets when
 * there is at least one candidate; `null` when only the facets are asked for
 */
data class SearchDocumentStatements(
    val facets: SearchDocumentStatement?,
    val rows: SearchDocumentStatement?,
)

/**
 * One SQL statement of a search, with its named parameters.
 */
data class SearchDocumentStatement(
    val sql: String,
    val params: MapSqlParameterSource,
)
