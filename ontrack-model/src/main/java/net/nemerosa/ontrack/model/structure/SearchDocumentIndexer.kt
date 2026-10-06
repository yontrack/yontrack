package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.security.ProjectFunction

/**
 * Describes the [search documents][SearchDocument] of one [search result type][SearchResultType].
 *
 * An indexer only describes documents: it carries no SQL and does not read the entities back when
 * a search is performed, since results are rendered from [SearchDocument.data] — and from the
 * current names of the projects and branches it refers to ([nameReferences]), resolved by the
 * search service in one lookup per search.
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

    /**
     * References, in the [data][SearchDocument.data] of the documents of this type, to the projects
     * and branches whose names are resolved when searching: a result shows their current names,
     * whatever names they had when its document was written. See [SearchDocumentReference].
     *
     * Every project or branch the data renders with [searchDocumentData] is declared here — with
     * [projectSearchDocumentReferences], [branchSearchDocumentReferences] or
     * [buildSearchDocumentReferences].
     */
    val nameReferences: List<SearchDocumentReference> get() = emptyList()

    /**
     * References, in the [data][SearchDocument.data] of the documents of this type, to the projects
     * or branches whose name is in the [title][SearchDocument.title] or the
     * [identifiers][SearchDocument.identifiers] of the documents — what they are matched on.
     *
     * When such a project or branch is renamed, the documents referring to it through one of these
     * references are re-indexed by [indexRenamed], after the rename is committed: those it provides
     * are written, and those of this type referring to the renamed entity which it does not
     * provide are deleted.
     *
     * None by default: the names of the documents are then resolved when searching only, which is
     * enough for a name the documents are not matched on.
     */
    val renameScopes: List<SearchDocumentReference> get() = emptyList()

    /**
     * Provides the documents of this type referring to a renamed project or branch through one of
     * the [renameScopes].
     *
     * Runs as administrator, outside the transaction of the rename, after it has been committed.
     *
     * @param scope One of the [renameScopes]
     * @param entity The renamed project or branch, with its new name
     * @param processor Receives the documents to write
     */
    fun indexRenamed(scope: SearchDocumentReference, entity: ProjectEntity, processor: (SearchDocument) -> Unit) {
    }

}
