package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * No search indexer serves the given result type.
 */
class SearchResultTypeNotFoundException(type: String) : NotFoundException("Search result type not found: %s", type)
