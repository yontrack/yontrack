# Postgres replaces Elasticsearch for search

Up to 5.x, the search box ran on Elasticsearch, and Yontrack could not start without it:
`ElasticSearchStartupService` created the indexes at startup with no fallback. It was a whole
cluster to deploy, secure and upgrade for one feature, and its types leaked into the public model
(`SearchIndexer.initIndex` and `buildQuery` took Elasticsearch builders, and `elasticsearch-java`
was an `api` dependency of `ontrack-model`). Yontrack 6.0 searches on Postgres, which it already
needs, with nothing beyond the built-in full-text search and the `pg_trgm` contrib extension —
trusted since Postgres 13, so a database owner can create it on managed services. Elasticsearch
stays only as an optional target of the metrics export (`ontrack-extension-elastic`), and its
client and Spring Boot auto-configuration exist only when that export is enabled. Four decisions
shape it.

## One table, not the source tables

Every indexer writes *search documents* into one table, `SEARCH_DOCUMENTS`, and one query ranks
them all, rather than the search querying the tables of projects, branches and builds. The command
palette needs one ranked list across every type; the properties searched (release, display name,
Git branch) are JSON, and the commits are not in the database at all, so querying the source tables
would not have covered them anyway. An indexer only *describes* documents: the SQL is written once,
in the search service, and a result is rendered from the document's `DATA`, with no database read
per hit. Access is filtered in SQL, so totals, facets and pages count only what the user can see.

One table, partitioned by type (#1888, V87): hash partitions on `TYPE`, so that each type has
indexes of its own. A search reads each type on its own, in the order of its ranking, and stops at
the cap of the counts — which, on one unpartitioned table, meant searching every full-text and
trigram index once per type, through the entries of all the types. It is still one table for the
indexers and for the query.

## In the entity's transaction, inside a savepoint

A document is written in the same transaction as the change it describes, by the synchronous
listeners and property hooks which already react to it: no lag, no refresh setting, and no drift
between the entity and its document. In Postgres a failed statement aborts the whole transaction,
so the write runs inside a savepoint: a failure is rolled back to it, logged and counted in
`ontrack_search_index_errors{type}`, and the change goes on. **Search must never block the delivery
pipeline** — a search bug must not fail the creation of a build. The repair is the rebuild of the
type (the per-indexer reconciliation job), which upserts every document and deletes the stale ones;
it is scheduled for the types fed from outside any transaction (SCM commits, SCM catalog) and
manual for the others.

## No type precedence

The score is the matching tier (exact identifier, prefix, full-text, trigram), then the full-text
rank or the trigram similarity, then recency, and only then the `order` of the type. No type
outranks another: an exact build name beats a vague project match. Grouping by type is a
presentation concern of the palette.

## No dual backends in a release

The migration was incremental on `v6`, one type at a time, behind a temporary per-type router
between the two backends; the router went with the Elasticsearch search code (#1882), and no
release ships both. An upgrade to 6.0 rebuilds every type once, asynchronously, at the first start,
tracked per indexer, and search answers with what exists so far in the meantime.

## Considered options

- **Querying the source tables** — no cross-type ranking, and neither the properties nor the
  commits are queryable that way.
- **An `english` text search configuration** — stemming damages identifiers (`1.2.3`,
  `release/4.1`, `ABC-123`); the `simple` configuration is used.
- **Non-contrib extensions** (ParadeDB `pg_search`, `pgvector`, even `unaccent`) — each would bring
  back a deployment prerequisite, which is what this change removes.
- **An asynchronous indexing queue** — lag, and a refresh setting to wait on in tests, where the
  in-transaction write with a savepoint gives neither.

## Amendment, 2026-10-06: parent names are resolved at search time (#1889)

A result is no longer rendered from `DATA` alone. Documents copied the names of the projects and
branches they refer to when they were written, so a rename left them stale: a build showed the old
name of its project until a rebuild, and the commits — written once, with `insertIfAbsent` — until
the weekly full scan. Rewriting every document carrying a name on each rename would have put a
bulk write in the transaction of the rename, which is what the savepoint design avoids.

- **Names are resolved when searching.** `DATA` keeps the IDs of the projects and branches it
  refers to; each indexer declares where they are (`SearchDocumentIndexer.nameReferences`). Once
  the rows of a page — or of `perType` — are selected, the search service looks up their current
  names in **one query per search**, never one per result. A project the user cannot see, or which
  no longer exists, gets a `null` name: the lookup gives away no name. A title is still shown as
  written — a build link is found and shown by `targetProject:build` — as it was before.
- **What is matched on a name is re-indexed after the rename.** A document whose title or
  identifiers carry the name of another entity — a branch (`project/branch`), a build link
  (`targetProject:build`, never a branch name), a catalog entry (`project (repository)`) — declares it
  (`SearchDocumentIndexer.renameScopes`). A rename, and only a rename, re-indexes those documents
  through a scoped variant of the rebuild, asynchronously **after the commit**; a failure is logged
  and counted in `ontrack_search_index_errors{type}`, and the reconciliation stays the safety net.
  The update events say when there is a rename: they carry the previous name (`PREVIOUS_NAME`)
  only then.

The text above is left as it was decided, and the principle of the section *One table, not the
source tables* holds: the lookup reads two primary-key indexes for the IDs of one page, it does
not query the source tables to find anything.
