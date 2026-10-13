package net.nemerosa.ontrack.extension.github.app

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

/**
 * Exporting rate limits of the GitHub App configurations as metrics.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("GitHub Rate limit metrics")
@APIDescription("Metrics related to the rate limits of the GitHub configurations.")
object GitHubAppRateLimitMetricsNames {

    object Core {
        @APIDescription("Core limit of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_core_limit = "ontrack_extension_github_ratelimit_core_limit"

        @APIDescription("Core remaining of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_core_remaining =
            "ontrack_extension_github_ratelimit_core_remaining"

        @APIDescription("Core used of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_core_used = "ontrack_extension_github_ratelimit_core_used"
    }

    object GraphQL {
        @APIDescription("GraphQL limit of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_graphql_limit = "ontrack_extension_github_ratelimit_graphql_limit"

        @APIDescription("GraphQL remaining of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_graphql_remaining =
            "ontrack_extension_github_ratelimit_graphql_remaining"

        @APIDescription("GraphQL used of the GitHub configuration. Read from GitHub every minute by every replica: the replicas report the same value - aggregate it with max, not sum.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.GAUGE,
            tags = [
                MetricsMeterTag("configuration", "Name of the configuration")
            ]
        )
        const val ontrack_extension_github_ratelimit_graphql_used = "ontrack_extension_github_ratelimit_graphql_used"
    }

    object Search {
        @APIDescription("Number of times the search limit was exceeded, on this replica - sum it over the replicas.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag("repository", "Name of the repository")
            ]
        )
        const val ontrack_extension_github_ratelimit_search_exceeded =
            "ontrack_extension_github_ratelimit_search_exceeded"
    }

}