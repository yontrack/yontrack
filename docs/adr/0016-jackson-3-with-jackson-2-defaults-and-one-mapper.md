# Jackson 3 with Jackson 2 defaults and one mapper

Yontrack 6.0 moves from Jackson 2 (`com.fasterxml.jackson`) to Jackson 3 (`tools.jackson`), the
follow-up that [ADR 0015](0015-boot-4-before-jackson-3.md) left for after Spring Boot 4 (#1843).
Three decisions shape it.

## The JSON does not change: Jackson 2 defaults, written out

Jackson 3 changes a dozen defaults: properties sorted alphabetically, dates written as ISO strings
rather than timestamps, a `null` refused for a primitive, trailing tokens refused, the
properties without a view left out of a view, and more. Yontrack stores JSON — entity properties,
notification records, audit entries, settings — and compares some of it as text in SQL: the
notification records and the ingestion payloads are ordered and filtered on `->>'timestamp'`. A
changed default is a changed storage format, or a changed API.

So `ObjectMapperFactory` builds from `JsonMapper.builderWithJackson2Defaults()` and then writes
every setting out anyway, including the ones which `builderWithJackson2Defaults` does not set
(`DEFAULT_VIEW_INCLUSION`, `COMBINE_UNICODE_SURROGATES_IN_UTF8`, `ESCAPE_FORWARD_SLASHES`). The custom `JDK*` date
serializers stay, although Jackson 3 handles `java.time` by itself: they write the format the
stored JSON already has. Characterization tests, written on Jackson 2 and passing unchanged on
Jackson 3, pin the result — `ObjectMapperFactoryCharacterizationTest`, a notification record and
structure types as 5.x stores them, the output of the GraphQL `JSON` scalar, and the JSON a client
reads over HTTP.

Adopting a Jackson 3 default is a decision of its own, and none is taken here. The ad-hoc mappers
— the YAML ones, the JSON file types of auto-versioning, the REST clients — follow the same rule
with `configureForJackson2()` or `builderWithJackson2Defaults()`.

## One mapper configuration

5.x had several: `ObjectMapperFactory` for storage and GraphQL, a default Jackson 2 mapper in the
MVC converters, and Spring Boot's auto-configured one in the Elasticsearch client. They wrote a
`LocalDateTime` three ways — `"2025-11-04T09:12:30.123400Z"`, `[2025,11,4,9,12,30,123400000]` and
`"2025-11-04T09:12:30.1234"`.

The `JsonMapper` bean is now `ObjectMapperFactory.create()` (`JsonMapperConfiguration`), which
Spring Boot's own then backs off from. The MVC converters (`WebConfig`) and the Elasticsearch
client (`ElasticSearchConfiguration` and the metrics export, through `Jackson3JsonpMapper`) use
it. The REST payloads
therefore change: a date in a REST answer is now the string the rest of Yontrack writes, and the
migration page says so. No search document holds a date, and the metrics documents are converted
by `ObjectMapperFactory` before they reach the client, so the index content does not change.

The REST *clients* are the exception. Third-party APIs grow fields, so `restTemplateBuilder()`
keeps the lenient mapper Spring Framework 6 gave them — unknown properties ignored — on the
Jackson 2 defaults, rather than the storage mapper which rejects unknown properties.

## No Jackson 2 left

There is no `com.fasterxml.jackson.core`, `databind`, `datatype`, `dataformat` or `module`
reference in any source, main or test: `NoJackson2GuardTest` fails on a new one.
`com.fasterxml.jackson.annotation` stays, since Jackson 3 keeps the annotations under their old
package.

`spring-boot-jackson2` is gone, and so is Jackson 2 from the runtime. The libraries were moved to
their Jackson 3 versions: json-path 3 (`Jackson3JsonProvider`), json-schema-validator 3 (tests
only), the KDSL along with the rest, since it inherits Jackson from `ontrack-json`. Two had no
Jackson 3 version:

* **jjwt** has no Jackson 3 module. The GitHub App JWT is written by `JwtJsonSerializer`, a small
  jjwt `Serializer` over Jackson 3, and `jjwt-jackson` is no longer a dependency.
* **The Elasticsearch client** still depends on Jackson 2, for its `JacksonJsonpMapper` only. A
  component metadata rule in the root build removes `jackson-core` and `jackson-databind` from
  its dependencies, wherever it comes from — Spring Data Elasticsearch brings it too.

## What the compiler does not catch

Most of the move is a rename the compiler checks. Two changes of meaning are not:

* Jackson 3's `JsonNode` has a member `map(Function)`, which maps the node itself. In Kotlin a
  member wins over an extension, so `node.map { ... }` — the `Iterable.map` of Jackson 2 — still
  compiles when the lambda fits, and silently stops iterating. Every such call became
  `node.values().map { ... }`; the migration found them by listing the calls to
  `JsonNode.map` in the bytecode.
* The node accessors are strict: `stringValue()` (the old `textValue()`) and `intValue()` throw on
  a node of another type, `asText()` throws on an object or an array where it returned `""`, and
  gives `""` for a JSON `null` where it gave `"null"`. Where the lenient result was relied upon,
  the call says so (`stringValueOpt().orElse(null)`, `isValueNode` checks).
