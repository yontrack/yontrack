# Search indexers

Search runs on Postgres, in one table, `SEARCH_DOCUMENTS`, and one ranked query runs across every
*search result type*. An indexer does not search: it **describes the search documents** of its type,
and the search service writes them and queries them. There is no SQL in an indexer, and nothing is
read back from the entities when a search is performed — a result is rendered from what its
document carries.

The vocabulary is the one of `CONTEXT.md`: a *search document* is what an indexer writes about one
findable thing, and a *search result type* is the kind of thing a result is.

> Up to 5.x, search ran on Elasticsearch, behind the `SearchIndexer` extension point. 6.0 replaced
> it by the contract below and removed the Elasticsearch search (#1882): `SearchIndexer`,
> `SearchIndexService` and `SearchIndexUtils` are gone, and `elasticsearch-java` is no longer a
> dependency of `ontrack-model`. Elasticsearch is left to the export of the metrics
> (`ontrack-extension-elastic`). See ADR 0017 (`docs/adr/0017-postgres-replaces-elasticsearch-for-search.md`).

## The contract

In `ontrack-model`, package `net.nemerosa.ontrack.model.structure`:

```kotlin
data class SearchDocument(
    val type: String,               // ID of the search result type
    val key: String,                // unique within the type
    val projectId: Int?,            // access, and deletion with the project; null = no project
    val entity: ProjectEntityID?,   // optional entity being described
    val title: String,              // shown, strongest matching field
    val identifiers: List<String>,  // exact / prefix / trigram: names, display names, hashes, keys
    val text: String?,              // free text, lowest weight: descriptions, messages
    val data: JsonNode,             // what the frontend needs to render and link the result
    val updatedAt: LocalDateTime? = null, // recency for the ranking; null = time of indexation
)

interface SearchDocumentIndexer {
    val searchResultType: SearchResultType
    val indexerName: String get() = searchResultType.name
    val globalFunction: Class<out GlobalFunction>? get() = null
    val projectFunction: Class<out ProjectFunction>? get() = null
    val fuzzyMatching: Boolean get() = true
    val documentVersion: Int get() = 1
    val indexerSchedule: Schedule get() = Schedule.NONE
    fun indexAll(processor: (SearchDocument) -> Unit)
}

interface SearchDocumentService {
    fun index(document: SearchDocument)      // upsert on (type, key)
    fun index(documents: List<SearchDocument>)           // upsert, in one batch
    fun insertIfAbsent(documents: List<SearchDocument>): Int  // INSERT … ON CONFLICT DO NOTHING
    fun delete(type: String, key: String)
    fun rebuild(indexer: SearchDocumentIndexer)
}
```

## Writing an indexer

1. Declare the indexer as a Spring `@Component` implementing `SearchDocumentIndexer`. That is all
   the registration there is: its type becomes searchable, it gets a reconciliation job, and its
   documents are built at the next startup.
2. Write the documents **in the transaction of the change**, from what already reacts to it — an
   `EventListener` for the structure events, a property type's `onPropertyChanged` /
   `onPropertyDeleted` hooks — through `SearchDocumentService.index` and `delete`.
3. Give `indexAll` every document of the type, for the rebuilds.
4. Add the `framework/search/{type}/Result.js` and `Icon.js` components in `ontrack-web-core`,
   reading the `data` of the result.

`ProjectSearchProvider` (`ontrack-ui`) is the reference, and `BranchSearchProvider` and
`BuildSearchProvider` next to it show a type with a parent and with display names. For a type
written from a property type's hooks, see `ReleaseSearchExtension` (`ontrack-extension-general`) or
`GitBranchSearchIndexer` (`ontrack-extension-git`); for one pointing at two entities,
`BuildLinkSearchExtension`:

```kotlin
@Component
class ProjectSearchProvider(
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
) : SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = "project",
        name = "Project",
        description = "Project name in Ontrack",
        order = SearchResultType.ORDER_PROJECT,
    )

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { processor(it.asSearchDocument()) }
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_PROJECT, EventFactory.UPDATE_PROJECT -> {
                val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
                searchDocumentService.index(project.asSearchDocument())
            }
            EventFactory.DELETE_PROJECT ->
                searchDocumentService.delete("project", event.getIntValue("PROJECT_ID").toString())
        }
    }
}
```

### Choosing the fields

- **`key`**: stable and unique within the type, usually the ID of the entity. Writing a document
  with an existing key replaces it.
- **`projectId`**: the project the thing belongs to. It decides who sees the document, and the
  document is deleted with its project (`ON DELETE CASCADE`) — a type whose documents all belong to
  a project needs no deletion code for project deletions.
- **`entity`**: the project entity the document describes. When a branch or a build is deleted,
  the search service deletes the documents of every type whose entity is that branch or build —
  or, for a branch, one of its builds (`SearchDocumentDeletionListener`). A document describing
  anything else, or pointing at a deleted entity without describing it, is deleted explicitly.
  A build link, say, describes its source build: the deletion of its target build is handled by
  its indexer, and that of the target's branch or project — which would need to look at all their
  builds — by a daily reconciliation (`indexerSchedule`).
- **`title`**: what is shown. Its prefix matches, and so does a fuzzy match on it.
- **`identifiers`**: everything the thing answers to *exactly* — name, display name, commit hash
  and short hash, issue key. An exact identifier is the strongest match there is: put the name there
  even when it is also the title. Case does not matter.
- **`text`**: free text, matched word by word with the lowest weight. Keep it bounded (commit
  messages are truncated to 2 KB).
- **`data`**: everything the `Result` component needs, so that no result costs a read of the
  database. It is returned as the `data` of the `SearchResult`. Render the structure entities with
  `searchDocumentData()` (`SearchDocumentData.kt`, `ontrack-model`), so that every `Result`
  component receives a project, a branch or a build in the same shape. A document carrying
  another entity's names is rewritten when that entity is updated.
- **`updatedAt`**: the recency of the thing (a build's creation, say). Equally relevant results are
  ranked newest first.

### A type without a project

Documents with a `null` `projectId` are only visible to the users granted the `globalFunction` of
their indexer. An indexer which declares none never shows its project-less documents.
`SCMCatalogSearchIndexer` (`ontrack-extension-scm`) is the example: a catalog entry may be linked
to a project or not, and its documents are visible to the users granted `SCMCatalogAccessFunction`,
what the SCM catalog itself requires.

### A type protected by a project function

Seeing a project is usually enough to see its documents. A type whose content is protected by a
project function of its own declares it as `projectFunction`: its documents are then visible only
in the projects where the user is granted this function, on top of the project view. The security
findings (`FindingSearchIndexer`, `ontrack-extension-findings`) are the example: their documents
need `ProjectFindingsView`, which a user seeing every project through the *grant project view to
all* setting does not have.

The search checks the function once per search, not per document: granted independently of the
project — a global role, the administrator — it restricts nothing; else the search computes the
visible projects where it is granted, and filters the documents of the type on them in SQL, so
that totals and facets stay right. Declare one only when the type needs it: it costs a check per
visible project for the users who hold the function through their project roles only.

### A type of codes

A document is matched by similarity (the trigram tier) by default, which forgives a typo in a name.
A type whose identifiers are codes, where a similar code is another thing, turns it off with
`fuzzyMatching = false`: `CVE-2021-44228` must not find `CVE-2021-44229`. Its documents are still
matched exactly, by prefix and by their words. The findings are the example.

### A type indexed from an external source

Some things change outside of Yontrack and of any transaction: the commits of a repository, the
entries of the SCM catalog. No event says when, so their documents are written by **scheduled
scans**, and the reconciliation job of the type (`indexerSchedule`) is what keeps them in line.

When a full scan is too expensive to run often — the SCM commits are the largest index —
scan incrementally in between, as `ScmCommitSearchExtension` does:

- An **incremental scan** (its own job, `ScmCommitSearchJobs`, hourly) remembers the last commit
  it indexed per project, in the `StorageService`, and scans only the commits after it. It writes
  with `insertIfAbsent`: a commit never changes, so an existing document is left as it is, and
  neither a read nor a rewrite is paid for it.
- A **full scan** is the rebuild of the type, weekly: it rewrites every document, deletes the
  stale ones (a force push), and catches up on what the incremental scans missed (a failed write,
  a commit pushed with an older date than the last indexed one). A project whose scan fails in the
  full scan loses its documents with the stale ones: its marker is dropped, so that its next
  incremental scan is a full one.
- A source which cannot list what is new keeps the full scan every time: `insertIfAbsent` makes
  it cheap on the database side. For the commits, an SCM says so with
  `SCMChangeLogEnabled.commitsSinceSupported`.

`insertIfAbsent` never refreshes a document, nor its time of write: a document whose content may
change goes through `index`. The commit documents carry the name of their project, which is
therefore renamed in them only by the weekly full scan.

The issues found in the commit messages (`ScmIssueSearchExtension`) are written by the same pass,
with `index`. The rebuild of their own type scans the commits again, for the issues only.

## What the service guarantees

- **Same transaction, inside a savepoint.** A write joins the transaction of the change, so the
  document is searchable as soon as the change is committed — and within the transaction itself.
  It runs inside a savepoint: in Postgres a failed statement aborts the whole transaction, so a
  search bug would otherwise fail the creation of a build. A failed write is rolled back to the
  savepoint, logged, and counted in `ontrack_search_index_errors{type}`; the change itself goes on.
  **Search must never block the delivery pipeline.**
- **Rebuild = reconciliation.** `rebuild` writes every document `indexAll` provides, in batches of
  `ontrack.config.search.index.batch`, then deletes the documents of the type it did not write. It
  runs as administrator. It is what repairs a failed write, or an import which bypassed the events.
- **Once at startup.** Every indexer is rebuilt asynchronously at startup until it has been rebuilt
  once at its `documentVersion`, tracked by a storage key per indexer: a new indexer triggers only
  its own rebuild. **Bump `documentVersion` whenever the shape of the documents changes**, so that
  the existing ones are rebuilt. `ontrack.config.search.index.reset=true` forces all of them. While
  a type is rebuilt, search answers with what exists so far and the message *"Search index is being
  built"*. The rebuild of a type is timed in `ontrack_search_index_all{type}`.
- **Reconciliation job.** Each indexer gets a job, `search / rebuild / {type}`, manual unless the
  indexer declares an `indexerSchedule` — which the types indexed outside any transaction do (SCM
  commits every week, the SCM catalog every day), and so do the types whose deletions cannot all
  be followed in the transaction (build links, every day).
- **Batches.** `index(documents)` and `insertIfAbsent(documents)` write a batch in one savepoint:
  a failed batch writes none of its documents, and is counted as one error.

## How a query is matched and ranked

The query is trimmed, lower-cased and its white spaces collapsed. It must be at least 2 characters
long. Four tiers, strongest first:

| Tier      | Matches                                                  | From        |
|-----------|----------------------------------------------------------|-------------|
| Exact     | one identifier is the query                              | 2 characters |
| Prefix    | the query starts one identifier, or the title            | 2 characters |
| Full-text | each word of the query starts a word of the title, the identifiers or the text (`simple` configuration: no stemming) | 3 characters |
| Trigram   | the query is similar to a word of the title or of the identifiers (`pg_trgm`), unless the type turns `fuzzyMatching` off | 3 characters |

A result is ranked by its tier, then by the full-text rank or the trigram similarity within the
tier, then by `updatedAt` (newest first), then by the `order` of its type. **No type outranks
another**: an exact build name beats a vague project match. Grouping results by type is a matter of
presentation only.

Access is filtered in SQL — a user with the global project list sees every project's documents,
anyone else those of the projects they can see, and a type with a `projectFunction` only in the
projects where it is granted — so totals, facets and pages count only what the user can see.

The SQL is in `SearchDocumentJdbcRepository` (`ontrack-repository-impl`), the parsing of the query
in `ParsedSearchQuery`, the table in migrations `V85__1877_search_documents.sql` and
`V86__1878_search_documents_entity_index.sql`.

## Querying

```graphql
search(query: String, types: [String!], offset: Int = 0, size: Int = 20, perType: Int): SearchResults!
# SearchResults { total, facets { type count }, items { title description accuracy type data }, message }
```

`perType` returns the best *N* results of each type in one request, instead of a page. The
`search(token, type, offset, size) { pageInfo pageItems }` form is deprecated, kept until 7.0 as a
wrapper of this one. In the KDSL: `ontrack.search(query, types, offset, size, perType)`.

## Tests

`SearchServiceIT` (`ontrack-service`) shows how to test an indexer's documents with test indexers,
and `ProjectSearchIT` (`ontrack-ui`) how to test one end to end. ITs run in a transaction which is
rolled back: a test calling `rebuild` must run outside it
(`@Transactional(propagation = Propagation.NOT_SUPPORTED)`), since the rebuild commits its own
transactions and cannot see uncommitted data.
