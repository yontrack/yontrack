# Migration to V6

Yontrack 6 is a major release. This page lists what changes for the people who **deploy**
Yontrack and for the people who **write extensions** or use the **KDSL** client. Each 6.0 change
adds its own section.

## Spring Boot 4

Yontrack 6 runs on Spring Boot 4.1, Spring Framework 7, Spring Security 7 and Kotlin 2.3. JSON is
still handled by Jackson 2: the move to Jackson 3 is a separate 6.0 change.

### For deployers

* **Configuration properties** — no Yontrack or Spring property changes its name. The
  management server behaves as in 5.x: port `8800`, base path `/manage`, only `health`, `info`
  and `prometheus` exposed, the `account` end point off (see [Management port](../operations/management-port.md)).
* **Elasticsearch 9** — Yontrack now uses the 9.x Elasticsearch client, which talks to
  Elasticsearch 9. The Compose files ship Elasticsearch 9.2; an installation still on an
  Elasticsearch 8 server must upgrade it. The `spring.elasticsearch.*` properties are unchanged.
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

* **REST clients** — build them from `jackson2RestTemplateBuilder()`, or give them
  `jackson2ClientMessageConverters()`, both in `net.nemerosa.ontrack.extension.support.client`,
  rather than from `RestTemplateBuilder()` or `RestTemplate()`. Jackson 3 is always on the
  classpath under Spring Boot 4, and Spring Framework 7 then reads JSON with it by default — it
  cannot read a Jackson 2 `JsonNode` nor a Kotlin class through the Jackson 2 Kotlin module.
  `RestTemplateProvider` already does it.
* **Elasticsearch** — the low-level client bean is a `Rest5Client`
  (`co.elastic.clients.transport.rest5_client.low_level`), no longer an
  `org.elasticsearch.client.RestClient`.
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
`net.nemerosa.ontrack.kdsl.connector.support.jackson2RestTemplateBuilder()`, for the reason given
above.

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
