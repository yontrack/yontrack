# Migration to V6

Yontrack 6 is a major release. This page lists everything a Yontrack 5 installation must know to
upgrade, for the people who **deploy** Yontrack, the clients of its **API** (GraphQL and REST), the
users of the **KDSL** client, and those who configure it **as code** (CasC):

* [Upgrade path](#upgrade-path) — which versions upgrade to 6.0, and how to prepare;
* [Breaking changes](#breaking-changes) — the platform changes and the features removed;
* [Removed](#removed) — the items Yontrack 5 deprecated, and their replacement;
* [Newly deprecated](#newly-deprecated) — the items Yontrack 6 deprecates, and which Yontrack 7
  removes.

## Upgrade path

* **From any 5.x release** — Yontrack 6.0 upgrades an installation running any 5.x release. Its
  database migrations run at its first start, whichever 5.x version it starts from.
* **From 4.x** — upgrade to a 5.x release first, and start it once, before upgrading to 6.0: see
  [Migration from V4](migration-from-v4.md). Yontrack 6 no longer carries the data conversions
  which 5.0 ran once at its first start, so a 4.x database upgraded straight to 6.0 is not
  supported. A new installation, on an empty database, starts normally.
* **Check the deprecated items before upgrading** — the latest 5.5.x release counts every use of an
  item Yontrack 6 removes, in the `ontrack_deprecated_usage_total` metric, tagged by `surface` and
  `item`, and logs a `WARN` the first time each one is used. Upgrade to it, let it run through the
  usual activity of the installation — the CI pipelines, the CasC, the API clients — then list what
  is still used:

    ```
    sum by (surface, item) (ontrack_deprecated_usage_total)
    ```

    Every item it lists must be migrated before the upgrade: [Removed](#removed) gives the
    replacement of each one, and [Usage of deprecated features](../operations/metrics.md#usage-of-deprecated-features)
    how the metric works.

* **Check the [breaking changes](#breaking-changes)** — among them, JDK 25 to run the JAR, the
  `pg_trgm` extension of Postgres, and the removed features.

## Breaking changes

### Java 25

Yontrack 6 is built for and runs on **JDK 25**, an LTS release. Yontrack 5 ran on JDK 21.

#### For deployers

* **Docker image** — the `nemerosa/ontrack` image now runs on `azul/zulu-openjdk-alpine:25`.
  Nothing changes for an installation that runs the image.
* **Running the JAR yourself** — the minimum runtime is now JDK 25: the classes are compiled for
  it, and an older JVM refuses to load them.
* **Runtime warnings** — JDK 25 warns about libraries (Netty, Kotlin coroutines, …) which still
  use `sun.misc.Unsafe` memory access or restricted native methods. These warnings are expected
  and harmless.

### Spring Boot 4

Yontrack 6 runs on Spring Boot 4.1, Spring Framework 7, Spring Security 7 and Kotlin 2.3. JSON is
handled by Jackson 3: see [Jackson 3](#jackson-3).

#### For deployers

* **Configuration properties** — no Yontrack or Spring property changes its name. The
  management server behaves as in 5.x: port `8800`, base path `/manage`, only `health`, `info`
  and `prometheus` exposed, the `account` end point off (see [Management port](../operations/management-port.md)).
* **Elasticsearch 9** — the export of the metrics to Elasticsearch, the only use of
  Elasticsearch left in Yontrack 6 (see [Elasticsearch search](#elasticsearch-search-removed)),
  uses the 9.x Elasticsearch client, which talks to Elasticsearch 9: an installation exporting its
  metrics to an Elasticsearch 8 server must upgrade it. The `spring.elasticsearch.*` properties
  are unchanged.
* **Vault key store** — keys stored in Vault by Yontrack 5 are read as they are: their format does
  not change.
* **Custom JWT `typ`** — `ontrack.config.security.authorization.jwt.typ` still makes the API
  accept that `typ` header instead of the standard `JWT`. With it set, Yontrack no longer
  contacts the identity provider while starting up: the provider is reached on the first
  authenticated call, as it is without the setting.

#### For KDSL users

The KDSL builds its HTTP client with `spring-boot-restclient`. A program which built its own
`RestTemplate` alongside it should build it from
`net.nemerosa.ontrack.kdsl.connector.support.restTemplateBuilder()`: its JSON mapper then behaves as
the one of the 5.x clients — unknown properties ignored, properties written in their declaration
order — where the default one of Spring Framework 7 has the Jackson 3 defaults.

### Jackson 3

Yontrack 6 reads and writes JSON with Jackson 3 (`tools.jackson`) instead of Jackson 2
(`com.fasterxml.jackson`). The JSON it stores does not change.

#### For deployers

Nothing: the data stored by Yontrack 5 is read as it is.

#### For REST API clients

The dates in the answers of the REST API (`/rest/...`) are written as the rest of Yontrack writes
them — as a UTC timestamp string, `"2025-11-04T09:12:30.123400Z"` — where Yontrack 5 wrote them as
an array of numbers, `[2025,11,4,9,12,30,123400000]`. The GraphQL API does not change.

#### For KDSL users

`JsonNode` in the KDSL API is now `tools.jackson.databind.JsonNode`. The
[Jackson 3 migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
lists the classes and methods it renamed. Two changes compile and still break:

* **`JsonNode.map`** — in Kotlin, `node.map { ... }` now calls the new `JsonNode.map(Function)`
  member, which maps the node itself, instead of iterating over its elements. Write
  `node.values().map { ... }`.
* **Strict accessors** — `stringValue()` (formerly `textValue()`) and `intValue()` throw on a node of
  another type, and `asText()` throws on an object or an array; `asText()` of a JSON `null` is `""`,
  no longer `"null"`.

### Indicators removed

The indicators — project indicators, their categories, types, views and portfolios, the GitHub
compliance checks, and the indicators computed from Jenkins pipeline files and libraries and from
SonarQube — are removed, with no replacement. For numbers about how projects deliver, see the
[delivery scorecard](../scorecard/scorecard.md), which Yontrack computes from its own data.

#### For deployers

* **Data** — at its first start, Yontrack 6 deletes the stored indicator values, categories, types,
  views, portfolios and computed state, and the Jenkins pipeline-library indicator settings.
* **Roles** — `PROJECT_INDICATOR_MANAGER` and `GLOBAL_INDICATOR_MANAGER` are gone, and their grants
  are deleted.
* **Metrics** — `ontrack_indicator` (exported) and `ontrack_indicators_computing_ms` are gone.

#### For CasC users

The `ontrack.config.settings.jenkins-pipeline-library-indicator` key is ignored with a warning: see
[Unknown and removed keys](../configuration/casc.md#unknown-and-removed-keys).

#### For API clients

The indicator queries and mutations of the GraphQL API are gone, among them `indicatorCategories`,
`indicatorTypes`, `indicatorPortfolios`, `indicatorViewList`, `indicatorsManagement`,
`configurableIndicators` and `Project.projectIndicators`.

### Elasticsearch search removed

Yontrack 6 searches in its Postgres database, and no longer needs Elasticsearch. See
[Search index](../operations/search-index.md) for how it works and how to operate it.

#### For deployers

* **Elasticsearch is optional** — it is only used by the export of the metrics
  (`ontrack.extension.elastic.metrics.enabled=true`), which is disabled by default. Without it,
  Yontrack creates no Elasticsearch client and has no Elasticsearch health indicator, and the
  Elasticsearch cluster can be retired, along with the `spring.elasticsearch.*` properties. An
  export with `target: MAIN` still reads `spring.elasticsearch.*`. When it is enabled, the
  Elasticsearch health indicator is part of the health of Yontrack: the
  `management.health.elasticsearch.enabled: false` default of 5.x is gone.
* **`pg_trgm`** — search needs the `pg_trgm` extension of Postgres. Yontrack creates it at its
  first start, with `CREATE EXTENSION IF NOT EXISTS pg_trgm`. When the database user of Yontrack
  cannot create extensions, create it beforehand, once, as a user who can. It ships with
  Postgres, and the owner of a database can create it on the managed services (RDS, Cloud SQL,
  Azure), since it is a trusted extension.
* **A one-off rebuild** — at its first start, Yontrack 6 builds the search documents of every
  type in the background. Yontrack is usable in the meantime, and a search answers with what
  exists so far, saying *"Search index is being built"*. On a large installation, the SCM commits
  take the longest.
* **Retired settings** — `ontrack.config.search.index.immediate` and
  `ontrack.config.search.index.ignoreExisting` are gone, and ignored if still set.
  `ontrack.config.search.index.batch`, `logging`, `tracing` and `reset` keep their meaning.
* **Jobs and metrics** — the jobs of the `elasticsearch` category are replaced by the
  `search / rebuild / {type}` jobs of the `search` category, and the `ontrack_elasticsearch_*`
  metrics by `ontrack_search_*`: `ontrack_elasticsearch_index_all{index}` is now
  `ontrack_search_index_all{type}`, next to `ontrack_search_index_errors{type}`.

#### For API clients

* The `POST /rest/search/index/type/{type}` and `POST /rest/search/index/reset` endpoints are
  unchanged, and act on the Postgres search documents. An unknown type now answers with an error.
* The GraphQL `search(query, types, offset, size, perType)` query replaces the
  `search(token, type, offset, size)` form, which is deprecated: see
  [Newly deprecated](#newly-deprecated).

### Pure-Git support removed

A project can no longer be associated with a plain Git repository through the *Git configuration*
property. Yontrack 6 reads the code of a project from GitHub, GitLab or Bitbucket (Cloud or
Server) only. The Git configurations, which only this property used, go with it.

#### For deployers

* **Before upgrading**, move every project still using the *Git configuration* property to its
  [GitHub](../start/configuration/github.md), [GitLab](../start/configuration/gitlab.md) or
  [Bitbucket Cloud](../start/configuration/bitbucket-cloud.md) configuration. Yontrack 5.5 counts
  each project read through it in `ontrack_deprecated_usage_total`, with `surface=property` and
  `item=net.nemerosa.ontrack.extension.git.property.GitProjectConfigurationPropertyType`.
* **Data** — at its first start, Yontrack 6 deletes the *Git configuration* property of every
  project, and every Git configuration. A project which still had the property is left with no
  SCM: its Git indexation stops, and its change logs, commit information and branch information
  are no longer available until it is given a GitHub, GitLab or Bitbucket configuration. Its
  branches keep their *Git branch* property, and its builds their *Git commit* property.
* **Connectors** — the connector status no longer lists the `git` connectors.

#### For API clients

The `Project.gitProjectConfigurationProperty` field of the GraphQL API is gone, and the
`net.nemerosa.ontrack.extension.git.property.GitProjectConfigurationPropertyType` property type
can no longer be read or set, through the GraphQL API or the REST API.

### Pull request cache now active

The information Yontrack reads about a pull request — its title, status, source and target — is
now cached, as the `ontrack.config.extension.git.pull-requests.cache.*` properties always said it
was. Yontrack 5 had the condition inverted: with the cache enabled, the default, every lookup went
to the SCM, and disabling the cache turned it on
([#1934](https://github.com/yontrack/yontrack/issues/1934)).

This only concerns an instance with pull requests enabled
(`ontrack.config.extension.git.pull-requests.enabled: true`, off by default).

#### For deployers

* **PR information can be up to 6 hours old.** The cache keeps a pull request for
  `ontrack.config.extension.git.pull-requests.cache.duration` — 6 hours by default — before asking
  the SCM again, so a pull request renamed, merged or closed in the SCM can show its former state in
  Yontrack until then.
* **Fewer calls to the SCM.** Pull request lookups by the branch pages, the PR decorations and the
  stale-branch cleanup mostly stop reaching GitHub, GitLab or Bitbucket.
* **The cache metrics move.** `ontrack_extension_git_pr_cache_hits` and
  `ontrack_extension_git_pr_cache_miss` now count, where they stayed at zero.
* **To keep the uncached behaviour of Yontrack 5**, set
  `ontrack.config.extension.git.pull-requests.cache.enabled: false`
  (`ONTRACK_CONFIG_EXTENSION_GIT_PULLREQUESTS_CACHE_ENABLED=false`). An instance which had set it
  to `false` to turn the cache off was in fact running with it on, and gets the uncached behaviour
  it asked for.

### Delivery metrics and the delivery scorecard

Yontrack 6 introduces the [delivery scorecard](../scorecard/scorecard.md): lead time, frequency,
success rate, time to restore, test pass rate and test flakiness, and the
[security readings](../scorecard/scorecard.md#security-readings) — security maturity, remediation
time and overdue findings, read from the [security scans](../integrations/findings/findings.md) —
read every day for every project and kept as daily snapshots, and read together, against targets,
in licensed [estates](../scorecard/estates.md). The [estate view](../scorecard/estate-view.md) shows
the projects of an estate side by side, and fans one finding out over them. The delivery-metrics
extension of Yontrack 5 is removed, and its promotion-level charts now run on the samples of the
scorecard.

#### For deployers

**The promotion-level charts are kept** — their names, their `getChart` path, their
[dashboard widgets](../dashboards/widgets/promotion-charts.md) and their options are unchanged, and
so is the [end-to-end lead time](../dashboards/widgets/e2e-lead-time-chart.md) chart. Three of
them change what they measure:

* **Time to restore starts earlier.** An outage now starts at the **first** unpromoted build after
  a promoted one, not at the last. For B1 promoted, B2, B3 and B4 not promoted, and B5 promoted,
  the time to restore goes from the creation of B2 to the promotion of B5, where it used to go from
  the creation of B4. **The numbers grow for any outage longer than one broken build**; an outage of
  a single build reads as before.
* **Lead time** is counted in the period of the build's first promotion at the level, and once per
  build, where it was counted in the period of the build's creation, once per promotion run.
* **Success rate** leaves out the builds in flight at the end of the charted interval — created
  within its median lead time before its end — which had not had the time to be promoted.

**The `ontrack_dm_*` metrics are removed**, all seven of them, with no transition release. The
readings take over, exported as the [`ontrack_reading`](../scorecard/scorecard.md#metrics-export)
metric:

| Yontrack 5 metric                          | Yontrack 6                                                  |
|--------------------------------------------|-------------------------------------------------------------|
| `ontrack_dm_promotion_lead_time`           | `ontrack_reading`, `reading=delivery.leadTime`              |
| `ontrack_dm_promotion_success_rate`        | `ontrack_reading`, `reading=delivery.successRate`           |
| `ontrack_dm_promotion_ttr`                 | `ontrack_reading`, `reading=delivery.mttr`                  |
| `ontrack_dm_time_since_promotion`          | No replacement                                              |
| `ontrack_dm_relative_time_since_promotion` | No replacement                                              |
| `ontrack_dm_time_since_passed_validation`  | No replacement                                              |
| `ontrack_dm_time_since_validation`         | No replacement                                              |

The mapping is not one to one, and dashboards built on the old metrics need rebuilding rather than
renaming:

* The `ontrack_dm_promotion_*` metrics gave one point per build, tagged by source and target
  project, branch and promotion level. `ontrack_reading` gives **one point per project, reading and
  set, per day**: the median for a duration, in seconds, the percentage for a success rate.
* The promotion level is no longer a tag: a reading is read up to its set's marker — with no
  estate, the last promotion level of each branch. Filter on `estate=-` for the readings of each
  project on its own, or on an estate's name for the estate's marker.
* The time-to-restore reading has the new start above.
* The time-since-event metrics have no replacement.

**Removed configuration:**

* the *E2E Promotion Metrics Export* settings — deleted at the first start of Yontrack 6;
* the `ontrack.extension.delivery-metrics.tse.enabled` and
  `ontrack.extension.delivery-metrics.tse.interval` configuration properties, no longer read;
* the jobs of the `delivery-metrics` category.

**New:** the *Delivery scorecard* [settings](../scorecard/scorecard.md#settings), the daily jobs of
the *Delivery scorecard* category, and the `ontrack_readings_computation` and
`ontrack_readings_errors` metrics.

#### For API clients

The data of a `security-findings` validation run now records the **kind** of the scan — `IMAGE`,
`CODE`, `SECRETS`, `DAST`, `DEPENDENCIES` or `OTHER` — beside the counts by severity, as `kind`. It
is what the [security maturity](../scorecard/scorecard.md#security-readings) reads; a run sent
before Yontrack 6 has none, and is read by the kinds of the findings it reported.

#### For CasC users

The `ontrack.config.settings.e2e-promotion-metrics` key, which configured the *E2E Promotion
Metrics Export* settings, is ignored with a warning: see
[Unknown and removed keys](../configuration/casc.md#unknown-and-removed-keys).

### Failed and backdated deployments

A slot deployment can now [fail](../integrations/environments/environments.md#failed-deployments),
and be [recorded after the fact](../integrations/environments/environments.md#backdating-deployments).

#### For API clients

* **`FAILED` status** — a slot pipeline has a new, final status, `FAILED`, beside `DONE` and
  `CANCELLED`. A client reading the status of a deployment must expect it. It is reached through
  the new `failSlotPipeline` mutation, and sends the new `slot-pipeline-failed` event.
* **`dateTime`** — `startSlotPipeline`, `startSlotPipelineDeployment`,
  `finishSlotPipelineDeployment`, `failSlotPipeline` and `cancelSlotPipeline` take an optional
  `dateTime`. Without it, nothing changes.
* **License errors** — calling a licensed query, field or mutation without the license — of the
  environments, for example — now fails with a `FORBIDDEN` error naming the feature, such as
  *Feature not allowed by the license: extension.environments*, where it failed with an
  `INTERNAL_ERROR` and no message.
* **Pending checks** — `SlotPipeline.errorMessage` now reports *failed* checks only. A deployment
  merely waiting — a workflow still running or not started yet, a manual approval not answered —
  has a null `errorMessage` and says what it waits for in the new `SlotPipeline.pendingMessage`. A
  client polling `errorMessage` to know whether a deployment is blocked should read
  `pendingMessage` too, or `Slot.blocked`. `SlotDeploymentCheck.state` (`OK`, `PENDING`, `FAILED`)
  and `Slot.blockingState` are new and tell the two apart.

#### For KDSL users

`SlotPipeline.fail(message, dateTime)` and `SlotPipeline.cancel(reason, dateTime)` are new, and
`Build.startPipeline`, `SlotPipeline.startDeploying` and `SlotPipeline.finishDeployment` take an
optional `dateTime`.

### Audit trail

Yontrack 6 records the story of every build in an [audit trail](../audit-trail/index.md), under
license, with evidence attached to the validation runs. Without the license, nothing is recorded,
but these changes, which came with it, apply to every installation.

#### For deployers

* **`DEGRADED` health** — the health has a new status, `DEGRADED`, answered with HTTP `200`: a
  feature does not work while the instance does. The overall status is `DEGRADED` while the
  license of the audit trail is on and its evidence storage is not configured or cannot be
  reached. A monitoring which expected only `UP` or `DOWN` must accept it. See
  [Management port](../operations/management-port.md).
* **Multipart limits** — `spring.servlet.multipart.max-file-size` and `max-request-size` are raised
  for every multipart request to the maximum size of an evidence,
  `ontrack.extension.audit-trail.storage.max-size` (50 MB by default) — plus 1 MB for the request.
  The multipart requests are parsed only once a controller reads them.
* **Ingress** — the CI pipelines upload evidence to `/rest/extension/audit-trail`: an ingress which
  routes only `/graphql` and `/hook` to the backend must route it too. See
  [Uploading evidence](../audit-trail/index.md#uploading-evidence).

#### For API clients

* **Unknown paths with a token** — a call authenticated by an API token to a path which does not
  exist now answers `404`, as for any other authenticated caller, where it answered `401`.

### GitHub workflow ID no longer sent by default

GitHub returns the run of a workflow it dispatches, so the `id` input which Yontrack passed to find
it is needed only on a GitHub which does not return it. The `workflowSendId` field of a
[GitHub configuration](../start/configuration/github.md#dispatching-workflows) is now `false` by
default: the auto-versioning post-processing and the GitHub workflow notifications no longer send
`id`, unless their `sendId` says otherwise.

#### For deployers

The GitHub configurations stored before the upgrade keep `workflowSendId: true`: the upgrade
writes it into those which did not set it. Nothing changes for them.

#### For API clients

`createGitHubConfiguration` without a `workflowSendId` input now creates a configuration with
`workflowSendId: false`.

#### For CasC users

A GitHub configuration defined as code is applied again from its YAML at startup, so the upgrade
does not keep its former value: without `workflowSendId`, it becomes `false`, and a workflow
declaring the `id` input rejects the dispatch. Either add `workflowSendId: true` to the
configuration to keep sending `id`, or remove the `id` input and the `inputs-<id>.properties`
artifact step from the workflows — on a GitHub which returns the run of a dispatch.

### Federated sign-out

Signing out of Yontrack now also ends the session at the identity provider, so that signing back
in asks for the credentials again instead of being answered silently. See
[Sign-out](../security/oidc.md#sign-out).

#### For deployers

* **The Keycloak provider of the UI** — Yontrack's own Keycloak, the default, or a Keycloak of
  yours, with `NEXTAUTH_PROVIDER` not set to `oidc` — nothing to do: the session is ended from the
  server.
* **The generic OIDC provider of the UI** (`NEXTAUTH_PROVIDER=oidc` — Auth0, Microsoft Entra ID,
  any OIDC provider, see [OIDC authentication](../security/oidc.md)) — register
  `https://<your-yontrack>/api/auth/signout-complete` at the provider, as a post-logout redirect
  URI: see [Sign-out](../security/oidc.md#sign-out) for where. Until it is registered,
  signing out leaves the user on the provider's page — an error page on Keycloak and Auth0 —
  instead of coming back to Yontrack. To keep signing out of Yontrack only, set the
  `NEXTAUTH_FEDERATED_SIGNOUT` environment variable of the UI to `false`.

## Removed

Yontrack 6 removes what Yontrack 5 deprecated. Each item is listed here with what to use instead,
by the way it is used: the GraphQL API, the REST API, configuration as code, the configuration
properties and environment variables, templating, the CI configuration, the GitHub ingestion
configuration, and the KDSL.

The latest 5.5.x release counts every use of these items: see [Upgrade path](#upgrade-path).

### GraphQL API

| Removed                              | Use instead                                                                  |
|--------------------------------------|------------------------------------------------------------------------------|
| `Account.name`                       | `Account.email`                                                              |
| `PromotionLevel.promotionRuns`       | `PromotionLevel.promotionRunsPaginated`, its `size` argument for `first`     |
| `PromotionLevel.autoVersioningTrail` | `PromotionLevel.autoVersioningTrailPaginated`                                |
| `PromotionRun.autoVersioningTrail`   | `PromotionRun.autoVersioningTrailPaginated`                                  |
| `AutoVersioningOrder.targetPaths`    | `AutoVersioningOrder.targetPath`                                             |
| `SearchResult.page`                  | No replacement: build the link from the `type` and `data` of the result      |
| `SearchResult.uri`                   | No replacement: build the link from the `type` and `data` of the result      |
| `VersionInfo.date`                   | No replacement: the field was always empty                                   |
| `HookResponse.info`                  | `HookResponse.infoLink`                                                      |
| `EventSubscriptionPayload.id`        | `EventSubscriptionPayload.name`                                              |

* **`promotionRuns`** — `promotionRunsPaginated` returns a page, `{ pageInfo pageItems }`, the most
  recent runs first. `promotionRuns(first: N)` becomes `promotionRunsPaginated(size: N)`; the
  `last` argument has no equivalent.
* **`autoVersioningTrail`** — the trail is now a page of branches, `{ pageInfo pageItems }`, where
  `autoVersioningTrail` returned an `AutoVersioningTrail` object and its `branches`: the
  `AutoVersioningTrail` type is gone with it. The paginated field returns only the eligible
  branches by default; pass `filter: {onlyEligible: false}` to get the rejected ones too, with
  their `rejectionReason`, as `autoVersioningTrail` did.
* **`HookResponse.info`** — the unstructured information returned by a hook is gone, from the
  hook records and from the answer of the `POST /hook/secured/{hook}` endpoint alike. `infoLink`
  carries the information structured; a disabled hook answers with the `IGNORED` type, and its
  record has the `DISABLED` state. The hook records stored by Yontrack 5 are still read, without it.
* **Subscriptions without a name** — the `name` of `SubscribeToEventsInput`, and of the
  `subscribe<Entity>ToEvents` mutations (`subscribeProjectToEvents`, `subscribeBranchToEvents`, …),
  is now required. A subscription created without one is rejected with a validation error, where
  Yontrack 5 named it with a hash of its content. Counted by 5.5.x as
  `subscription without name`, with `surface="graphql"`. The subscriptions Yontrack 5 already
  stored keep their names.

### REST API

| Removed                                                  | Use instead                                                  |
|----------------------------------------------------------|--------------------------------------------------------------|
| `POST /rest/structure/promotionLevels/{id}/image`        | `PUT /rest/structure/promotionLevels/{id}/image`             |
| `POST /rest/admin/predefinedPromotionLevels/{id}/image`  | `PUT /rest/admin/predefinedPromotionLevels/{id}/image`       |

The `POST` endpoints took the image as a multipart `file`. The `PUT` endpoints take the PNG image
encoded in Base64, on a single line, as the body of the request, for example:

```bash
base64 < gold.png | tr -d '\n' | curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: text/plain" --data-binary @- \
  "$YONTRACK_URL/rest/structure/promotionLevels/42/image"
```

The 6.x KDSL sets the image of a predefined promotion level with the `PUT` endpoint. A program
calling `createPredefinedPromotionLevel` with an `image` must use it: the 5.x KDSL still calls the
`POST` endpoint.

### Configuration as code

| Removed                                                  | Use instead                    |
|----------------------------------------------------------|--------------------------------|
| A subscription without a `name`, `global-subscriptions`  | A `name`, unique in its scope  |
| A subscription without a `name`, `entity-subscriptions`  | A `name`, unique in its entity |

Under `ontrack.extensions.notifications`, every entry of `global-subscriptions`, and every entry of
the `subscriptions` of an `entity-subscriptions` item, must have a `name`. A configuration with an
unnamed subscription fails to apply, where Yontrack 5 named it with a hash of its content. Counted
by 5.5.x as `subscription without name`, with `surface="casc"`. To keep a subscription Yontrack 5
created from an unnamed entry, give the entry the name Yontrack 5 generated for it, as the
*Subscriptions* page or the rendered CasC shows it. Under another name, a new subscription is
created: `entity-subscriptions` deletes the old one, `global-subscriptions` keeps it until it is
deleted by hand.

### Configuration properties

| Removed                                                    | Use instead                                                            |
|------------------------------------------------------------|------------------------------------------------------------------------|
| `ontrack.extension.elastic.metrics.api-compatibility-mode` | No replacement: the Elasticsearch 9 client needs no compatibility mode |

The property is ignored if still set.

### Templating

| Removed                                                        | Use instead                                                  |
|----------------------------------------------------------------|--------------------------------------------------------------|
| The `name` field of the `#.user` function, `#.user?field=name` | `#.user?field=email`, or `#.user`: the email is the default  |

A template still using `#.user?field=name` fails to render, with an error listing the accepted
fields, `display` and `email`.

### Auto-versioning configuration

Yontrack 5 accepted short aliases for eight parameters of an
[auto-versioning configuration](../integrations/auto-versioning/auto-versioning.md#configuration),
kept for the old Jenkins pipeline library ([#1515](https://github.com/yontrack/yontrack/issues/1515)).
Yontrack 6 accepts only the full names:

| Removed         | Use instead           | Counted by 5.5.x as (`surface="ci-config"`)   |
|-----------------|-----------------------|-----------------------------------------------|
| `project`       | `sourceProject`       | `autoVersioning.configurations.project`       |
| `branch`        | `sourceBranch`        | `autoVersioning.configurations.branch`        |
| `promotion`     | `sourcePromotion`     | `autoVersioning.configurations.promotion`     |
| `path`          | `targetPath`          | `autoVersioning.configurations.path`          |
| `regex`         | `targetRegex`         | `autoVersioning.configurations.regex`         |
| `property`      | `targetProperty`      | `autoVersioning.configurations.property`      |
| `propertyRegex` | `targetPropertyRegex` | `autoVersioning.configurations.propertyRegex` |
| `propertyType`  | `targetPropertyType`  | `autoVersioning.configurations.propertyType`  |

A configuration still using one of them is rejected: in the `autoVersioning` section of the
[CI configuration](../configuration/ci-config.md), the whole CI configuration fails to apply, with
an error naming the field — the missing `sourceProject` for a configuration using `project`, the
unrecognized `regex` for one using `regex`. The entries of `additionalPaths` are not concerned:
they keep their `path`, `regex`, `property`, `propertyRegex` and `propertyType` names.

The configurations Yontrack 5 already stored need no conversion: it always stored them with the
full names, whatever name they were sent with.

### GitHub ingestion configuration

The GitHub ingestion reads its configuration from the `.github/ontrack/ingestion.yml` file of the
repository, or from the `setBranchGitHubIngestionConfig` mutation. Yontrack 6 reads only the `v2`
format of this file:

| Removed                     | Use instead                                         |
|-----------------------------|-----------------------------------------------------|
| `version: v1`               | `version: v2`, with `vs-name-normalization: LEGACY` |
| The unversioned format      | `version: v2`                                       |

* **`v1`** — its fields are the ones of `v2`; only the default of `vs-name-normalization` differs,
  `LEGACY` in `v1` and `DEFAULT` in `v2`. Set `version: v2` and `vs-name-normalization: LEGACY` to
  keep the names of the validation stamps the ingestion creates. A `v1` file is rejected: the
  ingestion of the payload which loads it fails with *Unsupported version for the ingestion
  configuration: v1. Use version v2 instead.*
* **The unversioned format** — the original format, before `v1`, had `jobs` and `steps` as lists,
  and `general`, `jobsFilter`, `stepsFilter`, `validations`, `promotions`, `runs` and `casc` at the
  top level: `v2` has `jobs.mappings`, `steps.mappings`, `jobs.filter`, `steps.filter`, and
  `setup.validations`, `setup.promotions`, `setup.project` and `setup.branch`. A file without a
  `version` is now read as `v2`: one in the unversioned format fails to parse, on the first field
  `v2` does not know or types differently, and one with only `workflows.filter` or `tagging` reads
  as it did.

Unlike the other items of this page, 5.5.x does not count the use of these formats: check the
`ingestion.yml` files of the ingested repositories for a `version` other than `v2`.

### KDSL

| Removed                                          | Use instead                                           |
|--------------------------------------------------|-------------------------------------------------------|
| `NotificationsMgt.subscribe` without a `name`    | `NotificationsMgt.subscribe(name = ..., ...)`         |
| `Connector.uploadFile` with a `Pair` file        | `Connector.uploadFile` with a `FileContent` file      |

The `name` parameter of `NotificationsMgt.subscribe` no longer has a default: a call without it no
longer compiles.

`Connector.uploadFile(path, headers, file = name to bytes)` is gone: pass the file as a
`FileContent(name, content, type)`, which also gives the part its content type.

## Newly deprecated

Yontrack 6 deprecates the items below. They still work in every 6.x release, and are removed in
Yontrack 7. Each use of one of them is counted in `ontrack_deprecated_usage_total`, and logged as a
`WARN` the first time: see [Usage of deprecated features](../operations/metrics.md#usage-of-deprecated-features).

### GraphQL API

| Deprecated                                             | Use instead                                                   |
|--------------------------------------------------------|---------------------------------------------------------------|
| The `Query.search(token)` argument                     | `query`                                                       |
| The `Query.search(type)` argument                      | `types`, a list of types                                      |
| The `SearchResults.pageInfo` field                     | `total`, with the `offset` and `size` arguments of `search`   |
| The `SearchResults.pageItems` field                    | `items`                                                       |

### GitHub configurations

| Deprecated                                                           | Use instead                                                          |
|----------------------------------------------------------------------|----------------------------------------------------------------------|
| Authenticating a GitHub configuration with a `user` and a `password` | A token, `oauth2Token`, or a GitHub App, `appId` and `appPrivateKey` |

GitHub refuses passwords for its API. A GitHub configuration authenticating with a user and a
password still works in 6.x, whether it is created in the UI, through the
`createGitHubConfiguration` mutation — its `password` input field — as code, or with the
`GitHubConfiguration.password` field of the KDSL. Each time such a configuration is saved or used,
it is counted as `GitHub configuration password authentication`, with `surface="settings"`.
The *GitHub configurations* page marks it with a warning in its *Authentication* column.

### Configuration as code

The kebab-case aliases of a few CasC keys are deprecated: use the camel-case names, the only ones
the [CasC JSON schema](../configuration/casc.md) lists. Each use of an alias is counted with
`surface="casc"`, under the item given here.

| Deprecated alias                                                                        | Use instead                  |
|-----------------------------------------------------------------------------------------|------------------------------|
| `ontrack.config.github.app-id`                                                          | `appId`                      |
| `ontrack.config.github.app-private-key`                                                 | `appPrivateKey`              |
| `ontrack.config.github.app-installation`                                                | `appInstallationAccountName` |
| `ontrack.config.github.auto-merge-token`                                                | `autoMergeToken`             |
| `ontrack.config.github.workflow-send-id`                                                | `workflowSendId`             |
| `ontrack.config.webhooks.timeout-seconds`                                               | `timeoutSeconds`             |
| `ontrack.extensions.notifications.global-subscriptions.channel-config`                  | `channelConfig`              |
| `ontrack.extensions.notifications.entity-subscriptions.subscriptions.channel-config`    | `channelConfig`              |

For example, a global subscription:

```yaml
ontrack:
  extensions:
    notifications:
      global-subscriptions:
        - name: On Gold
          events:
            - new_promotion_run
          channel: slack
          channelConfig: # instead of channel-config
            channel: "#my-channel"
```

### Configuration properties

| Deprecated                                      | Use instead                                    |
|-------------------------------------------------|------------------------------------------------|
| `ontrack.extension.queue.general.warn-if-async` | `ontrack.extension.queue.general.warn-if-sync` |

The property emits a warning when the queues are processed synchronously: its former name said the
opposite of what it does. The old name, in any of its spellings — `warnIfAsync`, or
`ONTRACK_EXTENSION_QUEUE_GENERAL_WARNIFASYNC` as an environment variable — still sets the property,
and is counted once at startup with `surface="config"`. When both are set, the new name wins.

### CI environment

| Deprecated                                              | Use instead              |
|---------------------------------------------------------|--------------------------|
| The `ONTRACK_SCM_ISSUES` variable of the CI environment | `YONTRACK_CI_SCM_ISSUES` |

The [CI configuration](../configuration/ci-config.md) reads the issue service of a project —
`serviceId//serviceName` — from its `issueServiceIdentifier`, else from the
`YONTRACK_CI_SCM_ISSUES` variable of the CI environment, else from `ONTRACK_SCM_ISSUES`. A project
whose issue service is taken from `ONTRACK_SCM_ISSUES` is counted as `ONTRACK_SCM_ISSUES`, with
`surface="env"`.
