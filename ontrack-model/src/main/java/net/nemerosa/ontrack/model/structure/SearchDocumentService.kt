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
     * Creates or replaces several documents at once, identified by their type and key.
     *
     * The documents are written together: if one of them cannot be written, none is.
     */
    fun index(documents: List<SearchDocument>)

    /**
     * Creates the documents which do not exist yet, identified by their type and key, and leaves
     * the existing ones untouched (`INSERT … ON CONFLICT DO NOTHING`).
     *
     * For the types written in bulk from an external source (SCM commits, say), whose documents
     * never change once written: checking or rewriting each of them would cost more than the
     * insertion itself. A document which does change must go through [index].
     *
     * The documents are written together: if one of them cannot be written, none is.
     *
     * @return Number of documents actually created, 0 if the write failed
     */
    fun insertIfAbsent(documents: List<SearchDocument>): Int

    /**
     * Deletes a document, identified by its type and key. Deleting a document which does not
     * exist does nothing.
     */
    fun delete(type: String, key: String)

    /**
     * Deletes the documents of a type belonging to a project, except those whose key is given:
     * what a type indexed from a full scan of a project uses to drop what the scan no longer finds.
     *
     * @param type Type of the documents
     * @param projectId ID of the project
     * @param keys Keys of the documents to keep
     * @return Number of deleted documents, 0 if the deletion failed
     */
    fun deleteForProjectExcept(type: String, projectId: Int, keys: Collection<String>): Int

    /**
     * Rebuilds all the documents of an indexer: all the documents it provides are written, and
     * the documents of its type it did not provide are deleted.
     */
    fun rebuild(indexer: SearchDocumentIndexer)

}
