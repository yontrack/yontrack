# Migration to V6

Yontrack 6 is a major release. This page lists what changes for the people who **deploy**
Yontrack and for the people who **write extensions** or use the **KDSL** client. Each 6.0 change
adds its own section.

## Spring Boot 4

Yontrack 6 runs on Spring Boot 4.1, Spring Framework 7, Spring Security 7 and Kotlin 2.3. JSON is
handled by Jackson 3: see [Jackson 3](#jackson-3).

### For deployers

* **Configuration properties** — no Yontrack or Spring property changes its name. The
  management server behaves as in 5.x: port `8800`, base path `/manage`, only `health`, `info`
  and `prometheus` exposed, the `account` end point off (see [Management port](../operations/management-port.md)).
* **Elasticsearch 9** — the export of the metrics to Elasticsearch, the only use of
  Elasticsearch left in Yontrack 6 (see [Search on Postgres](#search-on-postgres)), uses the 9.x
  Elasticsearch client, which talks to Elasticsearch 9: an installation exporting its metrics to
  an Elasticsearch 8 server must upgrade it. The `spring.elasticsearch.*` properties are unchanged.
* **Vault key store** — keys stored in Vault by Yontrack 5 are read as they are: their format does
  not change.
* **Custom JWT `typ`** — `ontrack.config.security.authorization.jwt.typ` still makes the API
  accept that `typ` header instead of the standard `JWT`. With it set, Yontrack no longer
  contacts the identity provider while starting up: the provider is reached on the first
  authenticated call, as it is without the setting.

### For extension authors

Spring Boot 4 splits its auto-configuration into one module per technology, and several classes
moved with it:

| Before (Spring Boot 3)                                                  | Now (Spring Boot 4)                                                             |
|-------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `org.springframework.boot.web.client.RestTemplateBuilder`              | `org.springframework.boot.restclient.RestTemplateBuilder`                       |
| `org.springframework.boot.actuate.health.Health` / `HealthIndicator`   | `org.springframework.boot.health.contributor.Health` / `HealthIndicator`        |
| `org.springframework.boot.actuate.health.HealthEndpoint`               | `org.springframework.boot.health.actuate.endpoint.HealthEndpoint`               |
| `org.springframework.boot.actuate.health.HealthComponent`              | `org.springframework.boot.health.actuate.endpoint.HealthDescriptor`             |
| `org.springframework.boot.autoconfigure.graphql.*`                     | `org.springframework.boot.graphql.autoconfigure.*`                              |
| `org.springframework.boot.autoconfigure.flyway.*`                      | `org.springframework.boot.flyway.autoconfigure.*`                               |
| `org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties` | `org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchProperties` |
| `org.springframework.boot.web.context.WebServerApplicationContext`     | `org.springframework.boot.web.server.context.WebServerApplicationContext`       |

`ontrack-extension-support` exposes `spring-boot-restclient` and `spring-boot-health` as API
dependencies, so an extension gets both through it. The starters were renamed as well:
`spring-boot-starter-web` is `spring-boot-starter-webmvc`, `spring-boot-starter-aop` is
`spring-boot-starter-aspectj`, and `spring-boot-starter-oauth2-resource-server` is
`spring-boot-starter-security-oauth2-resource-server`.

Other breaks:

* **REST clients** — build them from `restTemplateBuilder()`, or give them
  `clientMessageConverters()`, both in `net.nemerosa.ontrack.extension.support.client`, rather
  than from `RestTemplateBuilder()` or `RestTemplate()`. Their JSON mapper behaves as the one of
  the 5.x clients — unknown properties ignored, properties written in their declaration order —
  where the default one of Spring Framework 7 has the Jackson 3 defaults.
  `RestTemplateProvider` already does it.
* **Elasticsearch** — the low-level client bean is a `Rest5Client`
  (`co.elastic.clients.transport.rest5_client.low_level`), no longer an
  `org.elasticsearch.client.RestClient`; and it exists only when the export of the metrics to
  Elasticsearch is enabled (see [Search on Postgres](#search-on-postgres)).
* **Null-safety** — Spring Framework 7 and graphql-java 25 annotate their APIs with JSpecify, and
  Kotlin enforces it:
    * `getForObject<T>()`, `postForObject<T>()` and the other `RestOperations` extensions return
      `T?` and take a non-null `T`: write `getForObject<Foo>(...)!!` where `getForObject<Foo>(...)`
      used to be enough, and `getForObject<Foo>(...)` where it was `getForObject<Foo?>(...)`;
    * `JdbcTemplate.queryForList(sql, String::class.java)` returns a `List<String?>`;
    * `DataFetchingEnvironment.getArgument<T>()` and `getSource<T>()` require a non-null `T`;
    * `RestTemplateBuilder.basicAuthentication(...)` takes non-null credentials.
* **Tests** — the JUnit Jupiter tests run on JUnit 6, and the JUnit 4 ones on its vintage engine.

### For KDSL users

The KDSL builds its HTTP client with `spring-boot-restclient`; a program which built its own
`RestTemplate` alongside it should build it from
`net.nemerosa.ontrack.kdsl.connector.support.restTemplateBuilder()`, for the reason given
above.

## Search on Postgres

Yontrack 6 searches in its Postgres database, and no longer needs Elasticsearch. See
[Search index](../operations/search-index.md) for how it works and how to operate it.

### For deployers

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
* The `POST /rest/search/index/type/{type}` and `POST /rest/search/index/reset` endpoints are
  unchanged, and act on the Postgres search documents. An unknown type now answers with an error.

### For extension authors

`SearchIndexer`, `SearchIndexService`, `SearchIndexUtils` and `SearchItem` are gone, and
`ontrack-model` no longer exposes `elasticsearch-java`. An extension contributing to the search
implements `SearchDocumentIndexer` and writes its documents through `SearchDocumentService`; see
the developer guide, `doc/dev-guide/search-indexer.md`, in the Yontrack repository.
`SearchService.indexInit()` is gone as well: there is nothing to initialize.

### For API clients

The GraphQL `search(query, types, offset, size, perType)` query replaces
`search(token, type, offset, size)`, which is deprecated and kept until 7.0.

## Jackson 3

Yontrack 6 reads and writes JSON with Jackson 3 (`tools.jackson`) instead of Jackson 2
(`com.fasterxml.jackson`). The JSON it stores does not change.

### For deployers

Nothing: the data stored by Yontrack 5 is read as it is.

### For REST API clients

The dates in the answers of the REST API (`/rest/...`) are written as the rest of Yontrack writes
them — as a UTC timestamp string, `"2025-11-04T09:12:30.123400Z"` — where Yontrack 5 wrote them as
an array of numbers, `[2025,11,4,9,12,30,123400000]`. The GraphQL API does not change.

### For extension authors

Replace `com.fasterxml.jackson` by `tools.jackson` in the imports, except for the annotations,
which Jackson 3 keeps in `com.fasterxml.jackson.annotation`. `JacksonException` replaces
`JsonProcessingException` and is unchecked; several classes and methods were renamed
(`JsonSerializer` → `ValueSerializer`, `TextNode` → `StringNode`, `fields()` → `properties()`, …).
The [Jackson 3 migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
lists them. Build a mapper with `ObjectMapperFactory.create()`, or `builder()` to add to it, rather
than with Jackson's own defaults, and REST clients with `restTemplateBuilder()` or
`clientMessageConverters()` (see above).
`ObjectMapperFactory.create(viewClass)` is gone: write with `create().writerWithView(viewClass)`. Two
changes compile and still break:

* **`JsonNode.map`** — in Kotlin, `node.map { ... }` now calls the new `JsonNode.map(Function)`
  member, which maps the node itself, instead of iterating over its elements. Write
  `node.values().map { ... }`.
* **Strict accessors** — `stringValue()` (formerly `textValue()`) and `intValue()` throw on a node of
  another type, and `asText()` throws on an object or an array; `asText()` of a JSON `null` is `""`,
  no longer `"null"`.

### For KDSL users

`JsonNode` in the KDSL API is now `tools.jackson.databind.JsonNode`.

## Java 25

Yontrack 6 is built for and runs on **JDK 25**, an LTS release. Yontrack 5 ran on JDK 21.

### For deployers

* **Docker image** — the `nemerosa/ontrack` image now runs on `azul/zulu-openjdk-alpine:25`.
  Nothing changes for an installation that runs the image.
* **Running the JAR yourself** — the minimum runtime is now JDK 25: the classes are compiled for
  it, and an older JVM refuses to load them.
* **Runtime warnings** — JDK 25 warns about libraries (Netty, Kotlin coroutines, …) which still
  use `sun.misc.Unsafe` memory access or restricted native methods. These warnings are expected
  and harmless.

### For extension authors

An extension must be compiled with a JDK 25 toolchain, since it compiles against Yontrack
classes which target JDK 25.
