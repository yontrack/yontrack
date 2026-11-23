package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.DeletionPropagation
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Runs a post-processing Job to its end, by polling the Kubernetes API.
 *
 * Nothing is kept in memory between two calls (ADR 0021): the only link between an
 * auto-versioning order and its Job is the [KubernetesJobConstants.LABEL_ORDER] label, which is
 * how a leftover Job of the same order is found and deleted.
 *
 * @param cleanupTimeout How long to wait for leftover Jobs to be gone
 * @param deadlineGrace Added to the timeout of the Job for the runner's own deadline, since the
 * `activeDeadlineSeconds` of the Job should fire first
 */
@Component
@ConditionalOnProperty(name = [KubernetesConfigProperties.ENABLED], havingValue = "true")
class KubernetesJobRunnerImpl internal constructor(
    private val properties: KubernetesConfigProperties,
    private val client: KubernetesClient,
    private val cleanupTimeout: Duration,
    private val deadlineGrace: Duration,
) : KubernetesJobRunner {

    @Autowired
    constructor(
        properties: KubernetesConfigProperties,
        @Lazy client: KubernetesClient,
    ) : this(properties, client, DEFAULT_CLEANUP_TIMEOUT, DEFAULT_DEADLINE_GRACE)

    private val logger = LoggerFactory.getLogger(KubernetesJobRunnerImpl::class.java)

    override fun run(
        manifests: KubernetesJobManifests,
        onStarted: (namespace: String, job: String) -> Unit,
    ) {
        val jobName = manifests.job.metadata?.name
            ?: throw IllegalStateException("The Job of a post-processing must have a name")
        val orderUuid = manifests.job.metadata?.labels?.get(KubernetesJobConstants.LABEL_ORDER)
            ?: throw IllegalStateException("The Job of a post-processing must have the ${KubernetesJobConstants.LABEL_ORDER} label")

        val (namespace, job) = start(manifests, jobName, orderUuid)
        onStarted(namespace, jobName)
        poll(namespace, job, manifests.timeoutSeconds)
    }

    // ---------------------------------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------------------------------

    /**
     * Cleanup, then creation of the Job and of its Secret. Any failure is transient: nothing ran.
     */
    private fun start(manifests: KubernetesJobManifests, jobName: String, orderUuid: String): Pair<String, Job> {
        var namespace: String? = null
        var created: Job? = null
        try {
            namespace = properties.namespace.takeIf { it.isNotBlank() } ?: client.namespace
            ?: throw IllegalStateException("No namespace is configured and Yontrack's own namespace is unknown")

            deleteLeftovers(namespace, orderUuid, manifests)

            val job = JobBuilder(manifests.job)
                .editMetadata().withNamespace(namespace).endMetadata()
                .build()
            logger.info("[kubernetes] Creating Job {}/{} for order {}", namespace, jobName, orderUuid)
            created = client.batch().v1().jobs().inNamespace(namespace).resource(job).create()
            val jobUid = created.metadata?.uid
                ?: throw IllegalStateException("The created Job $namespace/$jobName has no uid")

            // The Secret is owned by the Job, so that it is garbage-collected with it.
            // `blockOwnerDeletion` stays off: setting it needs an extra permission on the Job
            // finalizers, and nothing waits on the Secret anyway.
            val secret = SecretBuilder(manifests.credentialsSecret)
                .editMetadata()
                .withNamespace(namespace)
                .withOwnerReferences(
                    OwnerReferenceBuilder()
                        .withApiVersion("batch/v1")
                        .withKind("Job")
                        .withName(jobName)
                        .withUid(jobUid)
                        .build()
                )
                .endMetadata()
                .build()
            logger.debug("[kubernetes] Creating the credentials Secret of the Job {}/{}", namespace, jobName)
            client.secrets().inNamespace(namespace).resource(secret).create()

            return namespace to created
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            created?.let { deleteJobQuietly(namespace!!, jobName) }
            throw KubernetesPostProcessingTransientException(e)
        } catch (e: Exception) {
            if (e.isInterruption()) {
                Thread.currentThread().interrupt()
            }
            logger.warn("[kubernetes] Could not create the Job {} in {}: {}", jobName, namespace, e.message)
            // The Job is there without its Secret: delete it, so that nothing runs
            if (created != null && namespace != null) {
                deleteJobQuietly(namespace, jobName)
            }
            if (e.isRetryable()) {
                throw KubernetesPostProcessingTransientException(e)
            } else {
                // Running the order again would fail the same way: not in a cluster, no
                // namespace, a missing Role, a manifest the API refuses
                throw KubernetesPostProcessingConfigException(
                    "Kubernetes post processing job could not be created: ${e.rootMessage()}"
                )
            }
        }
    }

    /**
     * Deletes every Job of the same order, waits until they are gone, then deletes the Secret
     * which has the same name as the new one.
     */
    private fun deleteLeftovers(namespace: String, orderUuid: String, manifests: KubernetesJobManifests) {
        val jobs = client.batch().v1().jobs().inNamespace(namespace)
        val leftovers = jobs.withLabel(KubernetesJobConstants.LABEL_ORDER, orderUuid).list().items
        leftovers.forEach { leftover ->
            val name = leftover.metadata.name
            logger.info("[kubernetes] Deleting the leftover Job {}/{} of order {}", namespace, name, orderUuid)
            // Foreground: the Job is only gone once its pods are
            jobs.withName(name).withPropagationPolicy(DeletionPropagation.FOREGROUND).delete()
        }
        val deadline = System.nanoTime() + cleanupTimeout.toNanos()
        while (leftovers.isNotEmpty() &&
            jobs.withLabel(KubernetesJobConstants.LABEL_ORDER, orderUuid).list().items.isNotEmpty()
        ) {
            if (System.nanoTime() > deadline) {
                throw LeftoverCleanupTimeoutException(
                    "Leftover Jobs of order $orderUuid in $namespace still there after $cleanupTimeout"
                )
            }
            Thread.sleep(properties.job.pollInterval.toMillis())
        }
        // Owned by the old Job, the old Secret may not be collected yet - or it was orphaned by a
        // Job deleted by hand. Always deleted: deleting a missing Secret is not an error.
        manifests.credentialsSecret.metadata?.name?.let { secretName ->
            client.secrets().inNamespace(namespace).withName(secretName).delete()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Polling
    // ---------------------------------------------------------------------------------------------

    /**
     * What was last seen of the pod of the Job, for a timeout, when the pod is usually gone.
     */
    private class Observation {
        var step: String? = null
        var waiting: String? = null
    }

    private fun poll(namespace: String, job: Job, timeoutSeconds: Long) {
        val jobName = job.metadata.name
        val jobUid = job.metadata.uid
        val deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).plus(deadlineGrace).toNanos()
        val observation = Observation()
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException()
                }
                val current: Job? = try {
                    client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get()
                        ?: throw KubernetesPostProcessingFailureException(
                            namespace = namespace,
                            job = jobName,
                            step = observation.step,
                            exitCode = null,
                            timedOut = false,
                            logs = "The Job was deleted before it finished.",
                        )
                } catch (e: KubernetesPostProcessingFailureException) {
                    throw e
                } catch (e: Exception) {
                    if (e.isInterruption()) throw InterruptedException()
                    // A Job which runs must not fail because the API hiccups: keep polling until
                    // the deadline
                    logger.warn("[kubernetes] Could not read the Job {}/{}: {}", namespace, jobName, e.message)
                    null
                }
                if (current != null) {
                    val conditions = current.status?.conditions ?: emptyList()
                    if (conditions.any { it.type == "Complete" && it.status == "True" }) {
                        logger.info("[kubernetes] Job {}/{} complete", namespace, jobName)
                        return
                    }
                    val failed = conditions.firstOrNull { it.type == "Failed" && it.status == "True" }
                    if (failed != null) {
                        logger.info(
                            "[kubernetes] Job {}/{} failed: {} {}",
                            namespace, jobName, failed.reason, failed.message
                        )
                        throw failure(
                            namespace, jobName, jobUid, observation,
                            timedOut = failed.reason == "DeadlineExceeded",
                            reason = failed.message,
                        )
                    }
                    observe(namespace, jobName, jobUid, observation)
                }
                if (System.nanoTime() > deadline) {
                    logger.info(
                        "[kubernetes] Job {}/{} still running past its deadline: deleting it",
                        namespace, jobName
                    )
                    val error = failure(namespace, jobName, jobUid, observation, timedOut = true, reason = null)
                    deleteJobQuietly(namespace, jobName)
                    throw error
                }
                Thread.sleep(properties.job.pollInterval.toMillis())
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.info("[kubernetes] Interrupted while waiting for the Job {}/{}: deleting it", namespace, jobName)
            deleteJobQuietly(namespace, jobName)
            throw KubernetesPostProcessingFailureException(
                namespace = namespace,
                job = jobName,
                step = observation.step,
                exitCode = null,
                timedOut = false,
                logs = "Yontrack was interrupted while waiting for the Job, which has been deleted.",
            )
        }
    }

    /**
     * Remembers the step the pod of the Job is at, and why it waits if it is stuck. Best effort.
     */
    private fun observe(namespace: String, jobName: String, jobUid: String?, observation: Observation) {
        val pod = try {
            findPod(namespace, jobName, jobUid)
        } catch (e: Exception) {
            if (e.isInterruption()) throw InterruptedException()
            logger.debug("[kubernetes] Could not read the pod of the Job {}/{}: {}", namespace, jobName, e.message)
            null
        } ?: return
        val statuses = pod.stepStatuses()
        // Current step: the first one not terminated successfully
        val currentStep = KubernetesJobConstants.STEPS.firstOrNull { step ->
            statuses[step]?.state?.terminated?.exitCode != 0
        } ?: return
        observation.step = currentStep
        val waiting = statuses[currentStep]?.state?.waiting
        observation.waiting = waiting
            ?.takeIf { it.reason != null && it.reason !in NORMAL_WAITING_REASONS }
            ?.let { w -> listOfNotNull(w.reason, w.message?.takeIf { it.isNotBlank() }).joinToString(": ") }
    }

    /**
     * Builds the failure of a Job: the failing step and the tail of its logs when its pod is
     * still there, what was last seen of it otherwise.
     */
    private fun failure(
        namespace: String,
        jobName: String,
        jobUid: String?,
        observation: Observation,
        timedOut: Boolean,
        reason: String?,
    ): KubernetesPostProcessingFailureException {
        val pod = try {
            findPod(namespace, jobName, jobUid)
        } catch (e: Exception) {
            logger.debug("[kubernetes] Could not read the pod of the Job {}/{}: {}", namespace, jobName, e.message)
            null
        }
        val statuses = pod?.stepStatuses() ?: emptyMap()
        val failing = KubernetesJobConstants.STEPS.firstNotNullOfOrNull { step ->
            statuses[step]?.state?.terminated?.exitCode
                ?.takeIf { it != 0 }
                ?.let { step to it }
        }
        val step = failing?.first ?: observation.step
        val exitCode = if (timedOut) null else failing?.second
        val logs = pod?.takeIf { step != null }?.let { readLogs(namespace, it.metadata.name, step!!) } ?: ""
        val text = logs.ifBlank {
            listOfNotNull(
                observation.waiting?.let { "Step `$step` was waiting: $it" },
                reason?.takeIf { it.isNotBlank() },
            ).joinToString("\n")
        }
        return KubernetesPostProcessingFailureException(
            namespace = namespace,
            job = jobName,
            step = step,
            exitCode = exitCode,
            timedOut = timedOut,
            logs = text,
        )
    }

    /**
     * The pod of the Job. `backoffLimit` is 0, so there is one; a pod of an older Job of the same
     * name (owned by another uid) is ignored.
     */
    private fun findPod(namespace: String, jobName: String, jobUid: String?): Pod? =
        client.pods().inNamespace(namespace).withLabel(LABEL_JOB_NAME, jobName).list().items
            .filter { pod ->
                val owners = pod.metadata?.ownerReferences
                jobUid == null || owners.isNullOrEmpty() || owners.any { it.uid == jobUid }
            }
            .maxByOrNull { it.metadata?.creationTimestamp ?: "" }

    private fun Pod.stepStatuses(): Map<String, ContainerStatus> =
        ((status?.initContainerStatuses ?: emptyList()) + (status?.containerStatuses ?: emptyList()))
            .associateBy { it.name }

    /**
     * Last lines of the log of a step. Best effort: empty when they cannot be read.
     */
    private fun readLogs(namespace: String, podName: String, step: String): String =
        try {
            client.pods().inNamespace(namespace).withName(podName)
                .inContainer(step)
                .tailingLines(properties.job.logTailLines)
                .log ?: ""
        } catch (e: Exception) {
            logger.debug("[kubernetes] Could not read the logs of {}/{}[{}]: {}", namespace, podName, step, e.message)
            ""
        }

    private fun deleteJobQuietly(namespace: String, jobName: String) {
        try {
            client.batch().v1().jobs().inNamespace(namespace).withName(jobName)
                .withPropagationPolicy(DeletionPropagation.BACKGROUND)
                .delete()
        } catch (e: Exception) {
            logger.warn("[kubernetes] Could not delete the Job {}/{}: {}", namespace, jobName, e.message)
        }
    }

    /**
     * Is a failure to create the Job worth running the order again? Yes for the Kubernetes API
     * not answering or failing, for a leftover slow to go, and for an interruption; no for what
     * the API refuses for good (authentication, authorisation, validation) and for a client
     * which cannot even be built.
     */
    private fun Throwable.isRetryable(): Boolean {
        val chain = generateSequence(this) { it.cause }.toList()
        if (isInterruption() || chain.any { it is LeftoverCleanupTimeoutException }) return true
        val apiError = chain.filterIsInstance<KubernetesClientException>().firstOrNull() ?: return false
        return apiError.code !in PERMANENT_API_CODES
    }

    private fun Throwable.rootMessage(): String =
        generateSequence(this) { it.cause }.last().let { it.message ?: it::class.java.name }

    private class LeftoverCleanupTimeoutException(message: String) : RuntimeException(message)

    private fun Throwable.isInterruption(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is InterruptedException || it is java.io.InterruptedIOException
        }

    companion object {
        val DEFAULT_CLEANUP_TIMEOUT: Duration = Duration.ofMinutes(1)
        val DEFAULT_DEADLINE_GRACE: Duration = Duration.ofMinutes(1)

        /** Answers of the API which the same request would get again. */
        private val PERMANENT_API_CODES = setOf(400, 401, 403, 404, 405, 422)

        /** Label set by Kubernetes on the pods of a Job. */
        const val LABEL_JOB_NAME = "job-name"

        /** Waiting reasons of a step which is just about to start. */
        private val NORMAL_WAITING_REASONS = setOf("PodInitializing", "ContainerCreating")
    }
}
