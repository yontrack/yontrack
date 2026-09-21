package net.nemerosa.ontrack.extension.support.client

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper

/**
 * The client converters of the REST clients of Yontrack.
 *
 * Their JSON mapper keeps the Jackson 2 defaults, as the default `RestTemplate` of Spring Framework 6
 * configured them, so that what the clients send and accept does not change with Jackson 3: unknown
 * properties are ignored, as the payloads of third-party APIs grow fields over time, and a JSON view
 * does not include the unannotated properties. The Jackson modules found on the classpath (Kotlin)
 * are registered by the converter itself.
 */
fun clientMessageConverters(): List<HttpMessageConverter<*>> =
    HttpMessageConverters.forClient()
        .registerDefaults()
        .withJsonConverter(clientJsonConverter())
        .build()
        .toList()

/**
 * A `RestTemplateBuilder` with the [clientMessageConverters]. Every REST client of Yontrack is built
 * from it rather than from `RestTemplateBuilder()`, whose JSON mapper has the Jackson 3 defaults.
 */
fun restTemplateBuilder(): RestTemplateBuilder =
    RestTemplateBuilder().messageConverters(clientMessageConverters())

private fun clientJsonConverter() = JacksonJsonHttpMessageConverter(
    JsonMapper.builderWithJackson2Defaults()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
)
