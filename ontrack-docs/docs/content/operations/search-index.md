# Search index

Yontrack searches in its own Postgres database. There is no search engine to deploy:
Elasticsearch is no longer used for search, only, optionally, as a target of the
[metrics export](#elasticsearch).

What a search can find is kept in one table, `SEARCH_DOCUMENTS`: one *search document* per
project, branch, build, release, build link, Git branch, SCM commit, SCM issue, security finding
and SCM catalog entry. The documents are written in the same transaction as the change they
describe, so a new build is searchable as soon as it is created. A search document which cannot be
written never fails the change itself: the error is logged, counted in the
[metrics](#metrics), and repaired by the next rebuild of its type.

## Prerequisite: `pg_trgm`

Search uses the `pg_trgm` extension of Postgres, which ships with Postgres (it is a *contrib*
extension) and is *trusted* since Postgres 13: the owner of the database can create it, without
being a superuser, including on managed services such as Amazon RDS, Google Cloud SQL and Azure
Database for PostgreSQL.

Yontrack creates it at its first start of 6.0, with `CREATE EXTENSION IF NOT EXISTS pg_trgm`, in a
database migration. When the database user of Yontrack cannot create extensions, create it
beforehand, once, as a user who can:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

## Rebuilds

The documents of a type can be rebuilt from what they describe. A rebuild writes every document of
its type, then deletes the ones it did not write. It runs in the background; while a type is
rebuilt, search answers with the documents which already exist, and says *"Search index is being
built"*.

A rebuild happens:

* **once, at startup**, for every type which has never been rebuilt at the current version of its
  documents — which, when upgrading to 6.0, means every type. Nothing is needed from the
  administrator; on a large installation, this first rebuild takes a while, the SCM commits being
  the largest type. The `ontrack.config.search.index.reset` setting forces the rebuild of all the
  types at every startup;
* **on schedule**, for the types whose content changes outside of Yontrack, through their jobs
  (see below);
* **on demand**, through the jobs or the REST API.

### Jobs

Each type has a *rebuild* job, in the `search` category, whose key is the type:
`search / rebuild / project`, `search / rebuild / scm-commit`, and so on. They can be launched from
the *Jobs* page. Most are manual only; some run on a schedule:

| Type          | Schedule                                                                                |
|---------------|-----------------------------------------------------------------------------------------|
| `build-link`  | every day, to remove the links to builds of deleted branches or projects                 |
| `scm-commit`  | every week, a full scan of the commits (when `ontrack.config.extension.scm.search.scheduled` is `true`) |
| `scm-catalog` | every day, when the SCM catalog is enabled                                             |

In between, the SCM commits are scanned incrementally by the `scm / search-commits / incremental`
job, every hour by default (`ontrack.config.extension.scm.search.schedule`): each run scans only
the commits after the last indexed one of each project. The issues mentioned in the commit
messages are indexed by the same scans.

### REST API

Two endpoints, meant for the administrators, rebuild the documents on demand — for example to repair the documents of a type
after importing data directly in the database:

```
# Rebuilds the documents of one type, and waits for the end of the rebuild
POST /rest/search/index/type/{type}

# Deletes the documents of all the types, rebuilding them if `reindex` is true
POST /rest/search/index/reset
{"reindex": true}
```

`GET /rest/search/types` lists the types.

## Metrics

| Metric                            | Type    | Tags   | Description                                                          |
|-----------------------------------|---------|--------|----------------------------------------------------------------------|
| `ontrack_search_index_errors`     | counter | `type` | Search documents which could not be written. The change they describe is not affected; the next rebuild of the type repairs them. |
| `ontrack_search_index_all`        | timer   | `type` | Duration of the full rebuild of a type.                               |

In Yontrack 5, the rebuild timer was `ontrack_elasticsearch_index_all`, tagged by `index`.

See the [list of metrics](../generated/metrics/index.md) for all of them.

## Settings

| Setting                                  | Default | Description                                                                 |
|------------------------------------------|---------|-----------------------------------------------------------------------------|
| `ontrack.config.search.index.batch`      | `1000`  | Number of documents written together during a rebuild.                      |
| `ontrack.config.search.index.logging`    | `false` | Logs the progress of the rebuilds.                                          |
| `ontrack.config.search.index.tracing`    | `false` | With `logging`, logs every SCM commit and issue being indexed, at the `DEBUG` level. Very verbose. |
| `ontrack.config.search.index.reset`      | `false` | Rebuilds the documents of all the types at every startup.                   |
| `ontrack.config.search.count-cap`        | `1000`  | Maximum number of results counted, and ranked, for each type. Past it, the count of the type is shown as `1000+`, and only its most recently updated matches of the strongest kinds are ranked. See [Search](../search/index.md#counts). |
| `ontrack.config.search.work-mem`         | `64MB`  | `work_mem` of the Postgres transaction of a search, set with `SET LOCAL`: it ends with the transaction, and no other connection is affected. With the Postgres default of 4 MB, a search on a frequent word is several times slower. Empty to keep the setting of the database. |

The settings `ontrack.config.search.index.immediate` and
`ontrack.config.search.index.ignoreExisting` of Yontrack 5 are gone: they only made sense for
Elasticsearch, and are ignored if still set.

## Elasticsearch

Elasticsearch is only used by the export of the metrics (`ontrack.extension.elastic.metrics.*`),
which is disabled by default. Unless this export is enabled, Yontrack creates no Elasticsearch
client, never connects to Elasticsearch, and has no Elasticsearch health indicator: the
`spring.elasticsearch.*` properties can be removed.

When the export is enabled with `ontrack.extension.elastic.metrics.target=MAIN`, the default, it
uses the Elasticsearch instance of the `spring.elasticsearch.*` properties, as in Yontrack 5; with
`CUSTOM`, the one of `ontrack.extension.elastic.metrics.custom.*`.
