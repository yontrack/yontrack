package net.nemerosa.ontrack.kdsl.connector.support

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

/**
 * A `RestTemplateBuilder` whose JSON converter is Jackson 2.
 *
 * Spring Framework 7 registers the Jackson 3 JSON converter by default as soon as Jackson 3 is on
 * the classpath, and the KDSL exchanges Jackson 2 types. Same as `jackson2RestTemplateBuilder` in
 * `ontrack-extension-support`, which the KDSL does not depend on.
 *
 * To be removed by the Jackson 3 migration (#1843).
 */
@Suppress("DEPRECATION")
fun jackson2RestTemplateBuilder(): RestTemplateBuilder =
    RestTemplateBuilder().messageConverters(
        HttpMessageConverters.forClient()
            .registerDefaults()
            .withJsonConverter(MappingJackson2HttpMessageConverter())
            .build()
    )
