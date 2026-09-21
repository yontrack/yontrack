package net.nemerosa.ontrack.service.json

import net.nemerosa.ontrack.json.ObjectMapperFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import tools.jackson.databind.json.JsonMapper

/**
 * The one JSON mapper configuration (ADR 0016): the `JsonMapper` bean which Spring Boot would
 * otherwise auto-configure with its own defaults is the one of [ObjectMapperFactory]. The MVC
 * converters (`WebConfig`) and the Elasticsearch client (`ElasticSearchConfiguration`) use it, so
 * that the JSON Yontrack writes is the same whichever way it goes out.
 */
@Configuration
class JsonMapperConfiguration {

    @Bean
    @Primary
    fun jsonMapper(): JsonMapper = ObjectMapperFactory.create()

}
