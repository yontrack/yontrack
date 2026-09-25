package net.nemerosa.ontrack.model.structure

/**
 * Writing the [search documents][SearchDocument].
 *
 * Writes run in the transaction of the caller, inside a savepoint: a failed write is rolled back
 * to the savepoint, logged and counted (`ontrack_search_index_errors`), and never fails the
 * transaction of the caller. Search must never block the delivery pipeline; a document which
 * could not be written is repaired by the next rebuild of its type.
 */
interface SearchDocumentService {

    /**
     * Creates or replaces a document, identified by its type and key.
     */
    fun index(document: SearchDocument)

    /**
     * Deletes a document, identified by its type and key. Deleting a document which does not
     * exist does nothing.
     */
    fun delete(type: String, key: String)

    /**
     * Rebuilds all the documents of an indexer: all the documents it provides are written, and
     * the documents of its type it did not provide are deleted.
     */
    fun rebuild(indexer: SearchDocumentIndexer)

}
