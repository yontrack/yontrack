package net.nemerosa.ontrack.extension.support.client

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

/**
 * The default client converters, with Jackson 2 for JSON.
 *
 * Spring Framework 7 registers the Jackson 3 JSON converter by default as soon as Jackson 3 is on
 * the classpath, which it always is under Spring Boot 4 (Flyway, Spring Data Elasticsearch and
 * the Elasticsearch client bring it). The REST clients of Yontrack exchange Jackson 2 types --
 * `com.fasterxml.jackson.databind.JsonNode`, Kotlin classes read through the Jackson 2 Kotlin
 * module -- which Jackson 3 cannot read.
 *
 * `MappingJackson2HttpMessageConverter()` builds its mapper the way the default `RestTemplate`
 * of Spring Framework 6 did, so the clients behave as they did before Spring Boot 4.
 *
 * To be removed by the Jackson 3 migration (#1843).
 */
@Suppress("DEPRECATION")
fun jackson2ClientMessageConverters(): List<HttpMessageConverter<*>> =
    HttpMessageConverters.forClient()
        .registerDefaults()
        .withJsonConverter(MappingJackson2HttpMessageConverter())
        .build()
        .toList()

/**
 * A `RestTemplateBuilder` whose JSON converter is Jackson 2. Every REST client of Yontrack
 * must be built from it rather than from `RestTemplateBuilder()` -- see
 * [jackson2ClientMessageConverters].
 */
fun jackson2RestTemplateBuilder(): RestTemplateBuilder =
    RestTemplateBuilder().messageConverters(jackson2ClientMessageConverters())
