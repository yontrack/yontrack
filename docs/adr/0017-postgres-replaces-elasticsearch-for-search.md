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
