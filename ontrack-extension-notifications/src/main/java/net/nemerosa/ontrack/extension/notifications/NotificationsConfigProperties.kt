package net.nemerosa.ontrack.extension.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = NotificationsConfigProperties.PREFIX)
@APIName("Notifications configuration")
@APIDescription("General configuration for the notifications.")
class NotificationsConfigProperties {

    @APIDescription("Are the notifications enabled?")
    var enabled: Boolean = true

    /**
     * Processing configuration
     */
    var processing: ProcessingProperties = ProcessingProperties()

    /**
     * Mail configuration
     */
    var mail = MailProperties()

    /**
     * HTTP calls configuration
     */
    @APIDescription("HTTP calls of the notifications")
    var http = HttpProperties()

    /**
     * HTTP calls configuration properties
     */
    class HttpProperties {
        /**
         * Mock deployment API
         */
        @APIDescription("Mock deployment API")
        var mock = HttpMockProperties()
    }

    /**
     * Mock deployment API, the target of the `http` channel in tests and in the demo.
     *
     * Nested rather than a flat `mockEnabled`, so that the generated documentation publishes a
     * usable environment variable: the doc generator uppercases a camel-case field without
     * splitting it, and only the dots of a nested property become underscores.
     */
    class HttpMockProperties {
        @APIDescription(
            "Enables the mock deployment API under `/extension/notifications/http/mock`, a testing aid " +
                    "for the `http` notification channel: `POST deployments` starts a deployment, which " +
                    "`GET deployments/{id}` shows RUNNING for a few seconds, then SUCCEEDED, or FAILED when " +
                    "the POST asked for it. Disabled by default; always enabled in the `dev` profile. Enable it " +
                    "only on demonstration or test instances. It is stateless, and works with several replicas."
        )
        var enabled: Boolean = false
    }

    /**
     * Mail configuration properties
     */
    class MailProperties {
        @APIDescription("From address for the email notifications")
        var from = "no-reply@localhost"
    }

    /**
     * Processing configuration
     */
    class ProcessingProperties {
        /**
         * Queuing configuration
         */
        var queue: ProcessingQueuingProperties = ProcessingQueuingProperties()
    }

    /**
     * Configuration for queuing
     */
    class ProcessingQueuingProperties {
        /**
         * Default number of queues for the event listener
         */
        @APIDescription("Default number of queues for the event listener")
        var listenerQueues: Int = 5
        /**
         * Default number of queues for the processing
         */
        @APIDescription("Default number of queues for the processing")
        var processingQueues: Int = 5
    }

    companion object {
        /**
         * Prefix for the properties
         */
        const val PREFIX = "ontrack.config.extension.notifications"

        /**
         * Property switching the mock deployment API on, for `@ConditionalOnProperty`.
         */
        const val HTTP_MOCK_ENABLED = "$PREFIX.http.mock.enabled"
    }

}