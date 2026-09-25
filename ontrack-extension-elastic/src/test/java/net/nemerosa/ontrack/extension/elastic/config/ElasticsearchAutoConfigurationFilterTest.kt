package net.nemerosa.ontrack.extension.elastic.config

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals

class ElasticsearchAutoConfigurationFilterTest {

    private val candidates = arrayOf(
        "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration",
        "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration",
        "org.springframework.boot.elasticsearch.autoconfigure.health.ElasticsearchRestHealthContributorAutoConfiguration",
        "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        null,
    )

    private fun match(vararg properties: Pair<String, String>): List<Boolean> {
        val environment = MockEnvironment()
        properties.forEach { (name, value) -> environment.setProperty(name, value) }
        val filter = ElasticsearchAutoConfigurationFilter()
        filter.setEnvironment(environment)
        return filter.match(candidates, mockk<AutoConfigurationMetadata>()).toList()
    }

    @Test
    fun `Elasticsearch auto-configurations are filtered out by default`() {
        assertEquals(
            listOf(false, false, false, false, true, true),
            match()
        )
    }

    @Test
    fun `Elasticsearch auto-configurations are filtered out when the metrics export is disabled`() {
        assertEquals(
            listOf(false, false, false, false, true, true),
            match("ontrack.extension.elastic.metrics.enabled" to "false")
        )
    }

    @Test
    fun `Elasticsearch auto-configurations are kept when the metrics export is enabled`() {
        assertEquals(
            listOf(true, true, true, true, true, true),
            match("ontrack.extension.elastic.metrics.enabled" to "true")
        )
    }

}
