package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingMissingConfigException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import net.nemerosa.ontrack.extension.kubernetes.KubernetesExtensionFeature
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Post-processing running the user's image as a Kubernetes Job in the cluster Yontrack runs in.
 *
 * Only there when [KubernetesConfigProperties.ENABLED] is set: otherwise `kubernetes` is an
 * unknown post-processing.
 */
@Component
@ConditionalOnProperty(name = [KubernetesConfigProperties.ENABLED], havingValue = "true", matchIfMissing = false)
class KubernetesPostProcessing(
    extensionFeature: KubernetesExtensionFeature,
    private val kubernetesConfigProperties: KubernetesConfigProperties,
    private val jobBuilder: KubernetesJobBuilder,
    private val jobRunner: KubernetesJobRunner,
) : AbstractExtension(extensionFeature), PostProcessing<KubernetesPostProcessingConfig> {

    override val id: String = "kubernetes"

    override val name: String = "Kubernetes job post processing"

    override fun parseAndValidate(config: JsonNode?): KubernetesPostProcessingConfig =
        if (config != null && !config.isNull) {
            config.parse<KubernetesPostProcessingConfig>().apply { validate() }
        } else {
            throw PostProcessingMissingConfigException()
        }

    override fun postProcessing(
        config: KubernetesPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        // The configuration may not have gone through parseAndValidate
        config.validate()

        fun render(template: String) = avTemplateRenderer.render(template, PlainEventRenderer.INSTANCE)

        // Rendering
        val dockerImage = render(config.dockerImage).trim()
        if (dockerImage.isBlank()) {
            throw KubernetesPostProcessingConfigException(
                "The `dockerImage` of the Kubernetes post-processing is blank once rendered: `${config.dockerImage}`."
            )
        }
        val dockerCommand = render(config.dockerCommand)
        if (dockerCommand.isBlank()) {
            throw KubernetesPostProcessingConfigException(
                "The `dockerCommand` of the Kubernetes post-processing is blank once rendered: `${config.dockerCommand}`."
            )
        }
        val commitMessage = config.commitMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { render(it) }
            ?.takeIf { it.isNotBlank() }
            ?: autoVersioningOrder.defaultCommitMessage
        // Injected variables are not overridable
        val env = config.env
            .filter { it.name !in RESERVED_VARIABLES }
            .map { KubernetesPostProcessingEnv(name = it.name, value = render(it.value)) }
        val secrets = config.secrets.filter { it.env !in RESERVED_VARIABLES }

        // Allow-lists (audit errors)
        KubernetesAllowLists.checkImage(dockerImage, kubernetesConfigProperties.allowed.images)
        config.secrets.forEach {
            KubernetesAllowLists.checkSecret(it.secret, kubernetesConfigProperties.allowed.secrets)
        }

        // Clone access, as late as possible: a GitHub App token lasts one hour
        val cloneAccess = scm.getCloneAccess()
            ?: throw KubernetesPostProcessingConfigException(
                "The SCM of the branch (type `${scm.type}`, engine `${scm.engine}`) does not support the Kubernetes " +
                        "post-processing, which supports GitHub, GitLab, Bitbucket Server and Bitbucket Cloud only."
            )

        // Building the Job
        val manifests = jobBuilder.build(
            KubernetesJobRequest(
                orderUuid = autoVersioningOrder.uuid,
                dockerImage = dockerImage,
                dockerCommand = dockerCommand,
                commitMessage = commitMessage,
                env = env,
                secrets = secrets,
                timeoutSeconds = config.timeoutSeconds,
                version = autoVersioningOrder.targetVersion,
                upgradeBranch = upgradeBranch,
                repositoryUri = repositoryURI,
                project = autoVersioningOrder.branch.project.name,
                branch = autoVersioningOrder.branch.name,
                cloneAccess = cloneAccess,
            )
        )

        // Running the Job, synchronously; its exceptions are propagated unchanged
        jobRunner.run(manifests) { namespace, job ->
            onPostProcessingInfo(
                PostProcessingInfo(
                    data = mapOf(
                        "namespace" to namespace,
                        "job" to job,
                    )
                )
            )
        }
    }

    /**
     * Same default as the Jenkins post-processing, the only other one defaulting its commit message
     * in Yontrack.
     */
    private val AutoVersioningOrder.defaultCommitMessage: String
        get() = "Post processing for version change in ${defaultPath.paths.first()} for $targetVersion"

    private fun KubernetesPostProcessingConfig.validate() {
        if (dockerImage.isBlank()) {
            invalid("`dockerImage` is required.")
        }
        if (dockerCommand.isBlank()) {
            invalid("`dockerCommand` is required.")
        }
        env.forEach {
            if (!it.name.isValidEnvName()) {
                invalid("`env` contains an invalid environment variable name: `${it.name}`.")
            }
        }
        secrets.forEach {
            if (it.secret.isBlank()) {
                invalid("`secrets` contains an entry without a `secret` name (variable `${it.env}`).")
            }
            if (it.key.isBlank()) {
                invalid("`secrets` contains an entry without a `key` (Secret `${it.secret}`).")
            }
            if (!it.env.isValidEnvName()) {
                invalid("`secrets` contains an invalid environment variable name: `${it.env}`.")
            }
        }
        timeoutSeconds?.let {
            if (it <= 0) {
                invalid("`timeoutSeconds` must be greater than 0: $it.")
            }
        }
    }

    private fun invalid(message: String): Nothing =
        throw KubernetesPostProcessingConfigException("Invalid Kubernetes post-processing configuration: $message")

    private fun String.isValidEnvName() = ENV_NAME_REGEX.matches(this)

    companion object {
        private val ENV_NAME_REGEX = "[A-Za-z_][A-Za-z0-9_]*".toRegex()

        /**
         * Variables always injected in the `run` step, never overridden by the configuration.
         */
        val RESERVED_VARIABLES: Set<String> = KubernetesJobBuilderImpl.INJECTED_VARIABLES
    }

}
