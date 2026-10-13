package net.nemerosa.ontrack.model.structure

/**
 * Re-indexation of the search documents carrying the name of a renamed project or branch (see
 * [SearchDocumentIndexer.renameScopes]).
 *
 * It runs after the rename is committed, from a queue message, on any replica (#2083).
 */
interface SearchDocumentRenameReindexation {

    /**
     * Re-indexes the documents carrying the name of a project or branch, from its current name,
     * and deletes the stale ones. Nothing happens when the entity no longer exists.
     *
     * Two re-indexations of the same entity never run at the same time, on any replica: the second
     * one waits for the first one. It computes from the current state of the database, not from the
     * rename which triggered it, so that running it again is harmless.
     *
     * Its failures are logged and counted in `ontrack_search_index_errors`, never thrown.
     *
     * @param type [ProjectEntityType.PROJECT] or [ProjectEntityType.BRANCH]
     * @param id ID of the renamed project or branch
     */
    fun reindexRenamed(type: ProjectEntityType, id: ID)

}
