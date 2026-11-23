package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.EnvVarBuilder
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.SecurityContext
import io.fabric8.kubernetes.api.model.SecurityContextBuilder
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Builds the Job of a post-processing and its credentials Secret. See [KubernetesHarnessScripts]
 * for the harness, and issue #2171 for the shape of the Job.
 */
@Component
@ConditionalOnProperty(
    name = [KubernetesConfigProperties.ENABLED],
    havingValue = "true",
    matchIfMissing = false,
)
class KubernetesJobBuilderImpl(
    private val properties: KubernetesConfigProperties,
) : KubernetesJobBuilder {

    override fun build(request: KubernetesJobRequest): KubernetesJobManifests {
        val name = KubernetesJobConstants.jobName(request.orderUuid)
        val labels = mapOf(
            KubernetesJobConstants.LABEL_ORDER to request.orderUuid,
            KubernetesJobConstants.LABEL_MANAGED_BY to KubernetesJobConstants.LABEL_MANAGED_BY_VALUE,
        )
        val timeoutSeconds = minOf(
            request.timeoutSeconds?.toLong() ?: properties.job.timeout.seconds,
            properties.job.maxTimeout.seconds,
        )

        val credentials = request.cloneAccess.credentials
        val secret = SecretBuilder()
            .withNewMetadata()
            .withName(name)
            .withLabels<String, String>(labels)
            .endMetadata()
            .withType("Opaque")
            .withStringData<String, String>(
                if (credentials != null) {
                    mapOf(
                        KubernetesHarnessScripts.CREDENTIALS_USERNAME_KEY to credentials.username,
                        KubernetesHarnessScripts.CREDENTIALS_PASSWORD_KEY to credentials.password,
                    )
                } else {
                    emptyMap()
                }
            )
            .build()

        val job = JobBuilder()
            .withNewMetadata()
            .withName(name)
            .withLabels<String, String>(labels)
            .endMetadata()
            .withNewSpec()
            .withBackoffLimit(0)
            .withActiveDeadlineSeconds(timeoutSeconds)
            .withTtlSecondsAfterFinished(properties.job.ttl.seconds.toInt())
            .withNewTemplate()
            .withNewMetadata()
            .withLabels<String, String>(labels)
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("Never")
            .withAutomountServiceAccountToken(false)
            .withImagePullSecrets(properties.job.imagePullSecrets.map { LocalObjectReference(it) })
            .withNewSecurityContext()
            .withRunAsUser(properties.job.runAsUser)
            .withRunAsGroup(properties.job.runAsGroup)
            .withFsGroup(properties.job.fsGroup)
            .withRunAsNonRoot(true)
            .withNewSeccompProfile()
            .withType("RuntimeDefault")
            .endSeccompProfile()
            .endSecurityContext()
            .withVolumes(volumes(name))
            .withInitContainers(
                harnessContainer(
                    KubernetesJobConstants.STEP_CLONE,
                    KubernetesHarnessScripts.CLONE_SCRIPT,
                    request
                ),
                runContainer(request),
            )
            .withContainers(
                harnessContainer(
                    KubernetesJobConstants.STEP_PUSH,
                    KubernetesHarnessScripts.PUSH_SCRIPT,
                    request
                ),
            )
            .endSpec()
            .endTemplate()
            .endSpec()
            .build()

        return KubernetesJobManifests(
            job = job,
            credentialsSecret = secret,
            timeoutSeconds = timeoutSeconds,
        )
    }

    private fun volumes(secretName: String): List<Volume> = listOf(
        VolumeBuilder().withName(KubernetesHarnessScripts.VOLUME_WORK_TREE).withNewEmptyDir().endEmptyDir().build(),
        VolumeBuilder().withName(KubernetesHarnessScripts.VOLUME_GIT_DIR).withNewEmptyDir().endEmptyDir().build(),
        VolumeBuilder().withName(KubernetesHarnessScripts.VOLUME_HARNESS_HOME).withNewEmptyDir().endEmptyDir()
            .build(),
        VolumeBuilder().withName(KubernetesHarnessScripts.VOLUME_CREDENTIALS)
            .withNewSecret()
            .withSecretName(secretName)
            .withDefaultMode(CREDENTIALS_FILE_MODE)
            .endSecret()
            .build(),
    )

    private fun harnessContainer(step: String, script: String, request: KubernetesJobRequest): Container =
        ContainerBuilder()
            .withName(step)
            .withImage(properties.harness.image)
            .withCommand("sh", "-c", script)
            .withEnv(
                env("HOME", KubernetesHarnessScripts.HARNESS_HOME_PATH),
                env("GIT_TERMINAL_PROMPT", "0"),
                // Command-line configuration only, whatever the harness image carries: a system or
                // global configuration could define a filter driver that a `.gitattributes`
                // planted by the `run` step would then trigger, next to the credentials
                env("GIT_CONFIG_NOSYSTEM", "1"),
                env("GIT_CONFIG_GLOBAL", "/dev/null"),
                env(KubernetesHarnessScripts.ENV_CLONE_URL, request.cloneAccess.url),
                env(KubernetesHarnessScripts.ENV_UPGRADE_BRANCH, request.upgradeBranch),
                env(KubernetesHarnessScripts.ENV_GIT_DIR, KubernetesHarnessScripts.GIT_DIR),
                env(KubernetesHarnessScripts.ENV_WORK_TREE, KubernetesHarnessScripts.WORK_TREE_PATH),
                env(KubernetesHarnessScripts.ENV_CREDENTIALS_DIR, KubernetesHarnessScripts.CREDENTIALS_PATH),
                env(KubernetesHarnessScripts.ENV_COMMIT_MESSAGE, request.commitMessage),
                env("GIT_AUTHOR_NAME", properties.git.userName),
                env("GIT_AUTHOR_EMAIL", properties.git.userEmail),
                env("GIT_COMMITTER_NAME", properties.git.userName),
                env("GIT_COMMITTER_EMAIL", properties.git.userEmail),
            )
            .withVolumeMounts(
                mount(KubernetesHarnessScripts.VOLUME_WORK_TREE, KubernetesHarnessScripts.WORK_TREE_PATH),
                mount(KubernetesHarnessScripts.VOLUME_GIT_DIR, KubernetesHarnessScripts.GIT_DIR_VOLUME_PATH),
                mount(KubernetesHarnessScripts.VOLUME_HARNESS_HOME, KubernetesHarnessScripts.HARNESS_HOME_PATH),
                mount(KubernetesHarnessScripts.VOLUME_CREDENTIALS, KubernetesHarnessScripts.CREDENTIALS_PATH, readOnly = true),
            )
            .withSecurityContext(containerSecurityContext())
            .build()

    /**
     * The user's step: the work tree only, never the credentials, the Git directory nor the
     * harness home.
     */
    private fun runContainer(request: KubernetesJobRequest): Container {
        val injected = listOf(
            env(INJECTED_VERSION, request.version),
            env(INJECTED_UPGRADE_BRANCH, request.upgradeBranch),
            env(INJECTED_REPOSITORY_URI, request.repositoryUri),
            env(INJECTED_PROJECT, request.project),
            env(INJECTED_BRANCH, request.branch),
        )
        // Later entries replace earlier ones of the same name: env, then secrets, then the
        // injected variables, which always win.
        val envVars = LinkedHashMap<String, EnvVar>()
        fun put(envVar: EnvVar) {
            envVars.remove(envVar.name)
            envVars[envVar.name] = envVar
        }
        request.env.forEach { put(env(it.name, it.value)) }
        request.secrets.forEach {
            put(
                EnvVarBuilder()
                    .withName(it.env)
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withName(it.secret)
                    .withKey(it.key)
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build()
            )
        }
        injected.forEach { put(it) }

        return ContainerBuilder()
            .withName(KubernetesJobConstants.STEP_RUN)
            .withImage(request.dockerImage)
            .withCommand("sh", "-c", request.dockerCommand)
            .withWorkingDir(KubernetesHarnessScripts.WORK_TREE_PATH)
            .withEnv(envVars.values.toList())
            .withVolumeMounts(
                mount(KubernetesHarnessScripts.VOLUME_WORK_TREE, KubernetesHarnessScripts.WORK_TREE_PATH),
            )
            .withResources(resources())
            .withSecurityContext(containerSecurityContext())
            .build()
    }

    private fun resources(): ResourceRequirements {
        fun quantities(q: KubernetesConfigProperties.ResourceQuantitiesProperties): Map<String, Quantity> =
            listOf("cpu" to q.cpu, "memory" to q.memory)
                .filter { (_, value) -> value.isNotBlank() }
                .associate { (key, value) -> key to Quantity(value.trim()) }

        val requests = quantities(properties.job.resources.requests)
        val limits = quantities(properties.job.resources.limits)
        return ResourceRequirementsBuilder()
            .apply { if (requests.isNotEmpty()) withRequests<String, Quantity>(requests) }
            .apply { if (limits.isNotEmpty()) withLimits<String, Quantity>(limits) }
            .build()
    }

    private fun containerSecurityContext(): SecurityContext =
        SecurityContextBuilder()
            .withAllowPrivilegeEscalation(false)
            .withNewCapabilities()
            .withDrop("ALL")
            .endCapabilities()
            .build()

    private fun env(name: String, value: String): EnvVar = EnvVar(name, value, null)

    private fun mount(volume: String, path: String, readOnly: Boolean = false): VolumeMount =
        VolumeMountBuilder()
            .withName(volume)
            .withMountPath(path)
            .apply { if (readOnly) withReadOnly(true) }
            .build()

    companion object {
        /** Credential files readable by their owner and the `fsGroup` only (0440). */
        private const val CREDENTIALS_FILE_MODE = 0b100_100_000

        const val INJECTED_VERSION = "VERSION"
        const val INJECTED_UPGRADE_BRANCH = "UPGRADE_BRANCH"
        const val INJECTED_REPOSITORY_URI = "REPOSITORY_URI"
        const val INJECTED_PROJECT = "PROJECT"
        const val INJECTED_BRANCH = "BRANCH"

        /** The variables always injected in the `run` step, which the configuration cannot override. */
        val INJECTED_VARIABLES = setOf(
            INJECTED_VERSION,
            INJECTED_UPGRADE_BRANCH,
            INJECTED_REPOSITORY_URI,
            INJECTED_PROJECT,
            INJECTED_BRANCH,
        )
    }
}
