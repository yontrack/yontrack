package net.nemerosa.ontrack.kdsl.connector.support

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper

/**
 * A `RestTemplateBuilder` whose JSON mapper keeps the Jackson 2 defaults, as the default
 * `RestTemplate` of Spring Framework 6 configured them. Same as `restTemplateBuilder` in
 * `ontrack-extension-support`, which the KDSL does not depend on.
 */
fun restTemplateBuilder(): RestTemplateBuilder =
    RestTemplateBuilder().messageConverters(
        HttpMessageConverters.forClient()
            .registerDefaults()
            .withJsonConverter(
                JacksonJsonHttpMessageConverter(
                    JsonMapper.builderWithJackson2Defaults()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                )
            )
            .build()
    )
