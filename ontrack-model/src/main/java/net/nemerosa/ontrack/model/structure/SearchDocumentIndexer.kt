package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.security.ProjectFunction

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
     * Project function which grants access to the documents of this type which belong to a
     * project, on top of the view of the project.
     *
     * `null` (the default) when seeing the project is enough to see its documents of this type.
     * Only for a type whose content is protected by a function of its own, like the security
     * findings: it costs a check of the function per visible project when the function is not
     * granted to the user for all the projects at once.
     */
    val projectFunction: Class<out ProjectFunction>? get() = null

    /**
     * Whether the documents of this type are matched by similarity, the trigram tier of the query.
     *
     * `true` by default. Turned off for a type whose identifiers are codes where a similar code is
     * another thing — a CVE ID similar to the one looked for is another vulnerability. The
     * documents of such a type are still matched exactly, by prefix and by their words.
     */
    val fuzzyMatching: Boolean get() = true

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
