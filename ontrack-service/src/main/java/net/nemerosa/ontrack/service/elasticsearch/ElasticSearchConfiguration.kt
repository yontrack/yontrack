package net.nemerosa.ontrack.service.elasticsearch

import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.elasticsearch.health.ElasticsearchRestClientHealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ElasticSearchConfiguration(
    val restClient: Rest5Client,
) {

    @Bean
    fun elasticsearchRestHealthIndicator(): ElasticsearchRestClientHealthIndicator =
        ElasticsearchRestClientHealthIndicator(restClient)

    /**
     * The search documents hold Jackson 2 types. Spring Boot 4 gives the Elasticsearch client a
     * Jackson 3 mapper as soon as Jackson 3 is on the classpath, which it always is; this restores
     * the mapper Spring Boot 3 configured, over the Jackson 2 `ObjectMapper` which
     * `spring-boot-jackson2` auto-configures.
     *
     * To be removed by the Jackson 3 migration (#1843).
     */
    @Bean
    fun jacksonJsonpMapper(objectMapper: ObjectMapper): JsonpMapper =
        JacksonJsonpMapper(objectMapper)

}
