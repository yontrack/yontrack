package net.nemerosa.ontrack.extension.kubernetes

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.OAuthTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Kubernetes client, for the cluster Yontrack runs in, and for it only.
 *
 * The configuration is **in-cluster only**: the API server is the one named by the
 * `KUBERNETES_SERVICE_HOST` / `KUBERNETES_SERVICE_PORT` variables that the kubelet sets in every
 * pod, and the identity is the service account of the pod, read from its mounted token. No
 * kubeconfig file, no context, no `KUBERNETES_MASTER` or other variable of the fabric8 automatic
 * configuration is looked at: whatever a developer machine or an image happens to carry, Yontrack
 * never talks to another cluster.
 *
 * The client is **lazy**: it is only created by the first call made through it. Enabling the
 * extension outside a cluster does not fail the start-up of Yontrack (nor a Spring test context);
 * it fails the first post-processing instead, with the reason.
 */
@Configuration
@ConditionalOnProperty(name = [KubernetesConfigProperties.ENABLED], havingValue = "true")
class KubernetesClientConfiguration {

    private val logger = LoggerFactory.getLogger(KubernetesClientConfiguration::class.java)

    /**
     * Created on first use only: inject it with `@Lazy` at the injection point too, or Spring
     * creates it while creating the bean that needs it. Closed on shutdown, if ever created.
     */
    @Bean(destroyMethod = "close")
    @Lazy
    fun kubernetesClient(): KubernetesClient {
        val config = inClusterConfig()
        logger.info("Kubernetes client: API server at {}, namespace {}", config.masterUrl, config.namespace)
        return KubernetesClientBuilder().withConfig(config).build()
    }

    companion object {

        /** Where the kubelet mounts the token, the CA and the namespace of the service account. */
        val SERVICE_ACCOUNT_DIR: Path = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount")

        /**
         * Configuration of a client for the cluster the current pod runs in.
         *
         * @param env Reads an environment variable (parameter for tests)
         * @param serviceAccountDir Directory of the service account files (parameter for tests)
         * @throws IllegalStateException Not running in a Kubernetes pod
         */
        fun inClusterConfig(
            env: (String) -> String? = System::getenv,
            serviceAccountDir: Path = SERVICE_ACCOUNT_DIR,
        ): Config {
            val host = env("KUBERNETES_SERVICE_HOST")
            val port = env("KUBERNETES_SERVICE_PORT")
            if (host.isNullOrBlank() || port.isNullOrBlank()) {
                throw IllegalStateException(
                    "Yontrack does not run in a Kubernetes cluster: KUBERNETES_SERVICE_HOST and " +
                            "KUBERNETES_SERVICE_PORT are not set. The Kubernetes extension only works in-cluster " +
                            "(${KubernetesConfigProperties.ENABLED})."
                )
            }
            val tokenFile = serviceAccountDir.resolve("token")
            val caFile = serviceAccountDir.resolve("ca.crt")
            val namespaceFile = serviceAccountDir.resolve("namespace")
            if (!Files.isRegularFile(tokenFile)) {
                throw IllegalStateException(
                    "No service account token at $tokenFile: the pod of Yontrack must mount the token " +
                            "of its service account (automountServiceAccountToken)."
                )
            }
            // IPv6 address of the API server
            val hostPart = if (host.contains(':')) "[$host]" else host
            // Config.empty() skips the automatic configuration (kubeconfig, system properties,
            // environment variables): every value is set here
            val config = Config.empty()
            config.masterUrl = "https://$hostPart:$port"
            if (Files.isRegularFile(caFile)) {
                config.caCertFile = caFile.toString()
            }
            // Bound service account tokens are rotated by the kubelet: read the file on every
            // request rather than once
            config.oauthTokenProvider = ServiceAccountTokenProvider(tokenFile)
            if (Files.isRegularFile(namespaceFile)) {
                config.namespace = Files.readString(namespaceFile).trim()
            }
            return config
        }
    }

    /**
     * Reads the projected token of the service account, which the kubelet rotates.
     */
    private class ServiceAccountTokenProvider(private val tokenFile: Path) : OAuthTokenProvider {
        override fun getToken(): String = Files.readString(tokenFile).trim()
    }
}
