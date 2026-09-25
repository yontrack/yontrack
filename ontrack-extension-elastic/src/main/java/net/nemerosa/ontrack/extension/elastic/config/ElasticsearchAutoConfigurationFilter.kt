package net.nemerosa.ontrack.extension.elastic.config

import net.nemerosa.ontrack.extension.elastic.metrics.ElasticMetricsConfigProperties
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment

/**
 * Elasticsearch is used only by the export of the metrics. Unless this export is enabled
 * (`ontrack.extension.elastic.metrics.enabled=true`), the Elasticsearch auto-configurations of
 * Spring Boot are left out: no client, no connection, no health indicator. Yontrack then needs no
 * Elasticsearch to start.
 *
 * When the export is enabled, Spring Boot configures the client from `spring.elasticsearch.*` as
 * usual, which is what the `MAIN` target of the export uses.
 *
 * Registered in `META-INF/spring.factories`, so that it applies wherever this extension is on the
 * classpath.
 */
class ElasticsearchAutoConfigurationFilter : AutoConfigurationImportFilter, EnvironmentAware {

    companion object {
        /**
         * Packages of the Elasticsearch auto-configurations of Spring Boot
         */
        private val PACKAGES = listOf(
            "org.springframework.boot.elasticsearch.",
            "org.springframework.boot.data.elasticsearch.",
        )
    }

    private var enabled: Boolean = false

    override fun setEnvironment(environment: Environment) {
        enabled = environment.getProperty(
            "${ElasticMetricsConfigProperties.ELASTIC_METRICS_PREFIX}.enabled",
            Boolean::class.java,
            false
        )
    }

    override fun match(
        autoConfigurationClasses: Array<out String?>,
        autoConfigurationMetadata: AutoConfigurationMetadata,
    ): BooleanArray = BooleanArray(autoConfigurationClasses.size) { index ->
        val name = autoConfigurationClasses[index]
        enabled || name == null || PACKAGES.none { name.startsWith(it) }
    }

}
