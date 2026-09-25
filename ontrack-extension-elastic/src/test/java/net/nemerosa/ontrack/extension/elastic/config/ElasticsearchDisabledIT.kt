package net.nemerosa.ontrack.extension.elastic.config

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client
import net.nemerosa.ontrack.extension.elastic.metrics.ElasticMetricsClient
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.elasticsearch.health.ElasticsearchRestClientHealthIndicator
import org.springframework.context.ApplicationContext
import kotlin.test.assertTrue

/**
 * The export of the metrics is disabled by default: nothing of Elasticsearch is in the context —
 * no client, no auto-configuration of Spring Boot, no health indicator.
 */
class ElasticsearchDisabledIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `No Elasticsearch client nor health indicator when the metrics export is disabled`() {
        assertTrue(applicationContext.getBeanNamesForType(Rest5Client::class.java).isEmpty())
        assertTrue(applicationContext.getBeanNamesForType(ElasticsearchRestClientHealthIndicator::class.java).isEmpty())
        assertTrue(applicationContext.getBeanNamesForType(ElasticMetricsClient::class.java).isEmpty())
    }

}
