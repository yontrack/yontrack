package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.security.GlobalFunction

/**
 * Describes the [search documents][SearchDocument] of one [search result type][SearchResultType].
 *
 * An indexer only describes documents: it carries no SQL and does not read the entities back when
 * a search is performed, since results are rendered from [SearchDocument.data].
 *
 * Declared as a Spring `@Component`, it is picked up automatically:
 *
 * - its type is searchable, on Postgres;
 * - [indexAll] is used to rebuild all its documents: once at startup (and again whenever
 *   [documentVersion] changes), and on demand or on [indexerSchedule];
 * - day to day, the documents are written in the transaction of the change, through
 *   [SearchDocumentService.index] and [SearchDocumentService.delete], typically from an
 *   `EventListener` or a property type hook.
 *
 * See `doc/dev-guide/search-indexer.md`.
 */
interface SearchDocumentIndexer {

    /**
     * Type of the documents written by this indexer. Its ID is [SearchDocument.type].
     */
    val searchResultType: SearchResultType

    /**
     * Display name for this indexer, used for its reconciliation job.
     */
    val indexerName: String get() = searchResultType.name

    /**
     * Global function which grants access to the documents of this type which belong to no
     * project ([SearchDocument.projectId] is `null`).
     *
     * `null` (the default) when all the documents of this type belong to a project.
     */
    val globalFunction: Class<out GlobalFunction>? get() = null

    /**
     * Version of the documents of this type. The documents are rebuilt once at startup, and
     * again at the next startup after this version has changed: bump it when the shape of the
     * documents changes.
     */
    val documentVersion: Int get() = 1

    /**
     * Schedule of the reconciliation job, which rebuilds all the documents of this type.
     *
     * By default, no schedule: the reconciliation is manual only, which suits types whose
     * documents are written in the transaction of the change.
     */
    val indexerSchedule: Schedule get() = Schedule.NONE

    /**
     * Provides all the documents of this type, used for a full rebuild. The documents which are
     * not provided any longer are deleted at the end of the rebuild.
     *
     * Runs as administrator.
     */
    fun indexAll(processor: (SearchDocument) -> Unit)

}
