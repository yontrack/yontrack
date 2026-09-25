package net.nemerosa.ontrack.extension.elastic.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties(prefix = ElasticMetricsConfigProperties.ELASTIC_METRICS_PREFIX)
@Component
@APIName("ElasticSearch metrics configuration")
@APIDescription("Configuration of the export of metrics into ElasticSearch")
class ElasticMetricsConfigProperties {

    companion object {
        /**
         * Prefix for the Elastic metrics configuration.
         */
        const val ELASTIC_METRICS_PREFIX = "ontrack.extension.elastic.metrics"
    }

    @APIDescription("Is the export of metrics to Elastic enabled? Elasticsearch is used for nothing else: when disabled, the default, no Elasticsearch client is created and Spring Boot's Elasticsearch auto-configuration and health indicator are left out.")
    var enabled: Boolean = false

    @APIDescription("Must we trace the behaviour of the export of the metrics in the logs?")
    var debug: Boolean = false

    @APIDescription(
        """
            Defines where the Elastic metrics should be sent.
            
            Possible values are:
            * MAIN - When this option is selected, the ES instance defined
            by the `spring.elasticsearch.*` properties will be used.
            * CUSTOM -When this option is selected, the ES instance defined
            by the metrics properties will be used.
        """
    )
    var target: ElasticMetricsTarget = ElasticMetricsTarget.MAIN

    /**
     * Index properties
     */
    var index = IndexConfigProperties()

    @APIDescription(
        """
            If the `target` property is set to `CUSTOM`, the following properties
            will be used to setup the Elastic instance to use for the export
            of the metrics.
            
            See https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/elasticsearch/ElasticsearchProperties.html
            for the list of available properties.
            
            Example:
            
            ```
            ontrack.extension.elastic.metrics.custom.uris = http://localhost:9200
            ```
        """
    )
    var custom = ElasticsearchProperties()

    @Deprecated("Ontrack is now used the Java ES client and the compatibility mode is always enabled")
    @APIDescription(
        """
            Set to true to enable the API Compatibility mode when accessing a 8.x ES server.
            
            See https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.17/java-rest-high-compatibility.html
        """
    )
    var apiCompatibilityMode: Boolean = false

    @APIDescription("Set to false to disable the deletion of the index when performing a re-indexation")
    var allowDrop: Boolean = true

    /**
     * Index properties
     */
    class IndexConfigProperties {

        @APIDescription("Name of the index to contains all Ontrack metrics")
        var name = "ontrack_metrics"

        @APIDescription(
            """
                Flag to enable immediate re-indexation after items are added
                into the index (used mostly for testing. It should not be
                used in production.
                If set to true, this overrides the asynchronous processing
                of the metrics
            """
        )
        var immediate = false

    }

    /**
     * Queue configuration
     */
    var queue = QueueConfigProperties()

    /**
     * Queue configuration
     */
    class QueueConfigProperties {

        @APIDescription(
            """
                Maximum capacity for the queue.
                If the queue exceeds this capacity, new events will be on hold.
            """
        )
        var capacity: UInt = 1024U

        @APIDescription(
            """
                Bulk update capacity.
                When the number of metrics reaches this amount, the metrics
                are sent to Elastic.
            """
        )
        var buffer: UInt = 512U

        @APIDescription(
            """
                Every such interval, the current buffer of metrics is flushed
                to Elastic (expressed by default in minutes)
            """
        )
        @DurationUnit(ChronoUnit.MINUTES)
        var flushing: Duration = Duration.ofMinutes(1)

    }
}
