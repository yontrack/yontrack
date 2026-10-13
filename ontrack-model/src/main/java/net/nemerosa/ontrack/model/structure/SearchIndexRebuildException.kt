package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.BaseException

/**
 * The rebuild of the search documents of a type failed.
 */
class SearchIndexRebuildException(type: String, error: String) :
    BaseException("Cannot rebuild the search documents of %s: %s", type, error)
