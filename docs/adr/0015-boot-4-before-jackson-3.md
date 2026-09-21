# Spring Boot 4 lands on Jackson 2, and Jackson 3 follows as its own change

Spring Boot 4 makes Jackson 3 (`tools.jackson`) its JSON library, and keeps Jackson 2 through
`spring-boot-jackson2`, a compatibility module which is deprecated but present in 4.1. Yontrack
6.0 moves to Spring Boot 4 on that module first (#1788) and migrates to Jackson 3 afterwards
(#1843), rather than doing both at once.

The two together would be one diff over 682 files in 43 modules: every `JsonNode`, every
`ObjectMapper`, the whole `ontrack-json` toolkit and the KDSL, on top of the Spring Boot move
itself. A change that size cannot be reviewed, and when a behaviour changes it cannot be
bisected to either half. Separately, the Spring Boot move is limited to what Spring Boot 4 forces,
and the Jackson migration is a mechanical rename behind a green build. The risk of relying on a
deprecated module is bounded by time: #1843 blocks the 6.0 release, so no release ships on it.

## What running Jackson 2 under Spring Boot 4 takes

Jackson 3 cannot be kept off the classpath — Flyway 12, Spring Data Elasticsearch 6.1, Spring
Vault 4 and the Elasticsearch 9 client depend on it — and Spring picks it over Jackson 2 wherever
it detects a JSON library. Every such place that exchanges Jackson 2 types is therefore pinned to Jackson 2
explicitly:

* the REST clients are built from `jackson2RestTemplateBuilder()` (`ontrack-extension-support`,
  and its twin in the KDSL): the default converters of a `RestTemplate` would read with Jackson 3;
* the Elasticsearch client gets a `JacksonJsonpMapper` over the Jackson 2 `ObjectMapper` which
  `spring-boot-jackson2` auto-configures, as Spring Boot 3 did: Spring Boot 4 would give it a
  Jackson 3 mapper, which writes a Jackson 2 `JsonNode` as a bean;
* the Vault key store hands Spring Vault 4 plain maps, converted with Jackson 2 on the Yontrack
  side: Spring Vault 4 maps payloads with Jackson 3 as soon as it is present;
* the MVC converters, which the GraphQL HTTP handler also uses, were already set explicitly to
  Jackson 2 by `WebConfig`, and stay so.

Each of them carries a test which fails if Jackson 3 takes over, and each points at #1843, which
removes them.
