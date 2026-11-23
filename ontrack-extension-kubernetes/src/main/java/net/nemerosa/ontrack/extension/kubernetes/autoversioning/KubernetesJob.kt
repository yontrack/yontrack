package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.batch.v1.Job
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess

/**
 * Names shared by the builder of a Job and its runner.
 */
object KubernetesJobConstants {
    /** Label carrying the UUID of the auto-versioning order, on the Job, its pods and its Secret. */
    const val LABEL_ORDER = "yontrack.io/auto-versioning-order"

    /** Label marking everything created by this extension. */
    const val LABEL_MANAGED_BY = "app.kubernetes.io/managed-by"
    const val LABEL_MANAGED_BY_VALUE = "yontrack"

    /** Init container cloning the upgrade branch (harness image). */
    const val STEP_CLONE = "clone"

    /** Init container running the user's image on the work tree. */
    const val STEP_RUN = "run"

    /** Main container committing and pushing (harness image). */
    const val STEP_PUSH = "push"

    /** The three steps, in the order they run. */
    val STEPS = listOf(STEP_CLONE, STEP_RUN, STEP_PUSH)

    /** Name of the Job, and of its Secret, for an order. */
    fun jobName(orderUuid: String) = "yontrack-av-${orderUuid.lowercase()}"
}

/**
 * Everything the builder needs to know about one post-processing. All values are final: templates
 * are rendered and the allow-lists have been checked.
 *
 * @property orderUuid UUID of the auto-versioning order
 * @property dockerImage Image of the `run` step
 * @property dockerCommand Command of the `run` step, run through `sh -c`
 * @property commitMessage Message of the commit made by the `push` step
 * @property env Environment variables of the `run` step, values rendered
 * @property secrets Secret keys exposed to the `run` step as environment variables
 * @property timeoutSeconds Timeout asked by the post-processing configuration, `null` for the default
 * @property version Target version of the order (`VERSION`)
 * @property upgradeBranch Branch to clone and push to (`UPGRADE_BRANCH`)
 * @property repositoryUri URI of the repository (`REPOSITORY_URI`)
 * @property project Name of the Yontrack project being upgraded (`PROJECT`)
 * @property branch Name of the Yontrack branch being upgraded (`BRANCH`)
 * @property cloneAccess Clone URL and credentials for the harness
 */
data class KubernetesJobRequest(
    val orderUuid: String,
    val dockerImage: String,
    val dockerCommand: String,
    val commitMessage: String,
    val env: List<KubernetesPostProcessingEnv>,
    val secrets: List<KubernetesPostProcessingSecret>,
    val timeoutSeconds: Int?,
    val version: String,
    val upgradeBranch: String,
    val repositoryUri: String,
    val project: String,
    val branch: String,
    val cloneAccess: SCMCloneAccess,
)

/**
 * What is created in the cluster for one post-processing. Neither carries a namespace nor an
 * owner reference: the runner sets them.
 *
 * @property job The Job
 * @property credentialsSecret Secret holding the SCM credentials, mounted by the `clone` and
 * `push` steps only. Always there, even for an anonymous access, so that the Job has one shape.
 * The runner creates it right after the Job, owned by it.
 * @property timeoutSeconds Effective timeout, also the `activeDeadlineSeconds` of the Job
 */
class KubernetesJobManifests(
    val job: Job,
    val credentialsSecret: Secret,
    val timeoutSeconds: Long,
)

/**
 * Builds the manifests of a post-processing. A pure function of the static configuration and of
 * the request: no Kubernetes call.
 */
interface KubernetesJobBuilder {
    fun build(request: KubernetesJobRequest): KubernetesJobManifests
}

/**
 * Runs a Job to its end, synchronously.
 */
interface KubernetesJobRunner {

    /**
     * Deletes any leftover Job of the same order (same [KubernetesJobConstants.LABEL_ORDER]
     * label), creates the Job and its Secret, then polls until the Job succeeds, fails or times
     * out.
     *
     * @param manifests What to create
     * @param onStarted Called once the Job is created, with its namespace and its name
     * @throws KubernetesPostProcessingTransientException The Kubernetes API failed while the Job
     * was being created
     * @throws KubernetesPostProcessingConfigException The Job cannot be created, and running the
     * order again would not help: no cluster, no namespace, or a request that the API refuses
     * (missing `Role`, invalid manifest)
     * @throws KubernetesPostProcessingFailureException A step failed, or the Job timed out
     */
    fun run(
        manifests: KubernetesJobManifests,
        onStarted: (namespace: String, job: String) -> Unit,
    )
}
