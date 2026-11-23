package net.nemerosa.ontrack.extension.kubernetes

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Static configuration of the Kubernetes extension.
 *
 * Every knob is here and nowhere else - no settings page, no CasC context: these properties are a
 * security boundary, set by whoever deploys Yontrack (ADR 0024).
 *
 * The properties are nested, never flat camel-case ones at the root, so that the generated
 * documentation publishes usable environment variables.
 */
@Component
@ConfigurationProperties(prefix = KubernetesConfigProperties.PREFIX)
@APIName("Kubernetes configuration")
@APIDescription(
    "Configuration of the Kubernetes extension, which runs auto-versioning post-processing as " +
            "Kubernetes Jobs in the cluster Yontrack runs in."
)
class KubernetesConfigProperties {

    @APIDescription(
        "Enables the Kubernetes extension. Disabled by default: no Kubernetes client is created and " +
                "`kubernetes` is an unknown auto-versioning post-processing."
    )
    var enabled: Boolean = false

    @APIDescription(
        "Namespace where the Jobs and their Secrets are created. When blank, the namespace Yontrack " +
                "itself runs in."
    )
    var namespace: String = ""

    @APIDescription("Harness: the `clone` and `push` steps of a Job")
    var harness = HarnessProperties()

    @APIDescription("Identity of the commit made by the harness")
    var git = GitProperties()

    @APIDescription("Allow-lists. Empty lists deny everything.")
    var allowed = AllowedProperties()

    @APIDescription("Settings applied to every Job")
    var job = JobProperties()

    class HarnessProperties {
        @APIDescription(
            "Image of the `clone` and `push` steps. It must provide `sh` and `git`, and run as a " +
                    "non-root user."
        )
        var image: String = DEFAULT_HARNESS_IMAGE
    }

    class GitProperties {
        @APIDescription("Name of the author of the commit made by the harness")
        var userName: String = "Yontrack"

        @APIDescription("Email of the author of the commit made by the harness")
        var userEmail: String = "no-reply@yontrack.invalid"
    }

    class AllowedProperties {
        @APIDescription(
            "Patterns that the `dockerImage` of a post-processing must match: `*` stands for any " +
                    "sequence of characters, and the whole image name must match one of the patterns. " +
                    "Empty by default, which denies every image."
        )
        var images: List<String> = emptyList()

        @APIDescription(
            "Names of the Kubernetes Secrets that a post-processing may reference. Empty by " +
                    "default, which denies every Secret."
        )
        var secrets: List<String> = emptyList()
    }

    class JobProperties {
        @APIDescription("Time given to a Job when the post-processing configuration sets no `timeoutSeconds`")
        var timeout: Duration = Duration.ofMinutes(10)

        @APIDescription(
            "Cap on the `timeoutSeconds` of a post-processing configuration. Keep it well under one " +
                    "hour: the token of a GitHub App is fetched when the Job is created and lasts one hour."
        )
        var maxTimeout: Duration = Duration.ofMinutes(30)

        @APIDescription("How long a finished Job is kept before Kubernetes deletes it (`ttlSecondsAfterFinished`)")
        var ttl: Duration = Duration.ofHours(1)

        @APIDescription("Interval between two readings of the status of a running Job")
        var pollInterval: Duration = Duration.ofSeconds(5)

        @APIDescription("Number of log lines of the failing step kept in the auto-versioning audit")
        var logTailLines: Int = 100

        @APIDescription("Names of the image pull secrets applied to every Job")
        var imagePullSecrets: List<String> = emptyList()

        @APIDescription("User ID that the three steps of a Job run as. Never 0.")
        var runAsUser: Long = 1000

        @APIDescription("Group ID that the three steps of a Job run as")
        var runAsGroup: Long = 1000

        @APIDescription("Group ID owning the volumes of a Job")
        var fsGroup: Long = 1000

        @APIDescription("Resources of the `run` step, the one running the image of the post-processing")
        var resources = ResourcesProperties()
    }

    class ResourcesProperties {
        @APIDescription("Requested resources")
        var requests = ResourceQuantitiesProperties(cpu = "100m", memory = "256Mi")

        @APIDescription("Resource limits")
        var limits = ResourceQuantitiesProperties(cpu = "1", memory = "1Gi")
    }

    class ResourceQuantitiesProperties(
        @APIDescription("CPU, as a Kubernetes quantity. Blank for none.")
        var cpu: String = "",
        @APIDescription("Memory, as a Kubernetes quantity. Blank for none.")
        var memory: String = "",
    )

    companion object {
        /** Prefix of the properties */
        const val PREFIX = "ontrack.extension.kubernetes"

        /** Property switching the extension on, for `@ConditionalOnProperty`. */
        const val ENABLED = "$PREFIX.enabled"

        /** Property naming the allowed images, for the message of a refusal. */
        const val ALLOWED_IMAGES = "$PREFIX.allowed.images"

        /** Property naming the allowed Secrets, for the message of a refusal. */
        const val ALLOWED_SECRETS = "$PREFIX.allowed.secrets"

        /** Stock Git image of the harness, pinned. */
        const val DEFAULT_HARNESS_IMAGE = "alpine/git:v2.49.1"
    }

}
