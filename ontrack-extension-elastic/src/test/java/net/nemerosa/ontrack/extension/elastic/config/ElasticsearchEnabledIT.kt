package net.nemerosa.ontrack.extension.elastic.config

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import net.nemerosa.ontrack.extension.elastic.metrics.ElasticMetricsClient
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.elasticsearch.health.ElasticsearchRestClientHealthIndicator
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/**
 * With the export of the metrics enabled, Spring Boot's Elasticsearch client, read from
 * `spring.elasticsearch.*` and used by the `MAIN` target, and its health indicator are there.
 *
 * Same properties as the other ITs of the metrics export, to share their context.
 */
@TestPropertySource(
    properties = [
        "ontrack.extension.elastic.metrics.enabled=true",
        "ontrack.extension.elastic.metrics.index.immediate=true",
    ]
)
class ElasticsearchEnabledIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `Elasticsearch client and health indicator when the metrics export is enabled`() {
        assertEquals(1, applicationContext.getBeanNamesForType(Rest5Client::class.java).size)
        assertEquals(1, applicationContext.getBeanNamesForType(ElasticsearchRestClientHealthIndicator::class.java).size)
        assertEquals(1, applicationContext.getBeanNamesForType(ElasticMetricsClient::class.java).size)
    }

}
