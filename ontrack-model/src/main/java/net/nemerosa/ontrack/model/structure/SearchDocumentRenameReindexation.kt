package net.nemerosa.ontrack.model.structure

/**
 * Re-indexation of the search documents carrying the name of a renamed project or branch, which runs
 * asynchronously after the rename is committed (see [SearchDocumentIndexer.renameScopes]).
 */
interface SearchDocumentRenameReindexation {

    /**
     * Blocks until the re-indexations in progress are complete.
     */
    fun awaitCompletion()

}
