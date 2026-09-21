package net.nemerosa.ontrack.service.elasticsearch

import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.jackson.Jackson3JsonpMapper
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import org.springframework.boot.elasticsearch.health.ElasticsearchRestClientHealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class ElasticSearchConfiguration(
    val restClient: Rest5Client,
) {

    @Bean
    fun elasticsearchRestHealthIndicator(): ElasticsearchRestClientHealthIndicator =
        ElasticsearchRestClientHealthIndicator(restClient)

    /**
     * The search documents are written with the one mapper configuration of Yontrack (ADR 0016).
     * Spring Boot would give the Elasticsearch client a mapper of its own, with the Jackson 3
     * defaults.
     */
    @Bean
    fun jacksonJsonpMapper(jsonMapper: JsonMapper): JsonpMapper =
        Jackson3JsonpMapper(jsonMapper)

}
