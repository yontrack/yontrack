package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.fabric8.mockwebserver.Context
import io.fabric8.mockwebserver.MockWebServer
import io.fabric8.mockwebserver.ServerRequest
import io.fabric8.mockwebserver.ServerResponse
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runner against the fabric8 mock server, in CRUD mode mixed with expectations: the mock server
 * runs no pod, so the tests set the status of the Job and create its pod themselves, from the
 * `onStarted` callback (which the runner calls synchronously before it polls) or from expectations.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class KubernetesJobRunnerImplTest {

    private lateinit var server: KubernetesMockServer
    private lateinit var client: KubernetesClient
    private lateinit var properties: KubernetesConfigProperties

    @BeforeEach
    fun before() {
        val responses = mutableMapOf<ServerRequest, Queue<ServerResponse>>()
        server = KubernetesMockServer(Context(), MockWebServer(), responses, KubernetesMixedDispatcher(responses), false)
        server.init()
        // Own client: default namespace, and no retry, so that an error of the API is seen as is
        val config = Config.empty()
        config.masterUrl = server.url("/")
        config.isTrustCerts = true
        config.namespace = CLIENT_NAMESPACE
        config.requestRetryBackoffLimit = 0
        client = KubernetesClientBuilder().withConfig(config).build()
        properties = KubernetesConfigProperties().apply {
            job.pollInterval = Duration.ofMillis(5)
            job.logTailLines = 5
        }
    }

    @AfterEach
    fun after() {
        client.close()
        server.destroy()
    }

    private fun runner(
        deadlineGrace: Duration = Duration.ofSeconds(10),
    ) = KubernetesJobRunnerImpl(
        properties = properties,
        client = client,
        cleanupTimeout = Duration.ofSeconds(5),
        deadlineGrace = deadlineGrace,
    )

    // ---------------------------------------------------------------------------------------------
    // Success
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Job complete, with the Secret owned by the Job in the same namespace`() {
        val order = uuid()
        val manifests = manifests(order)
        var started: Pair<String, String>? = null
        runner().run(manifests) { namespace, job ->
            started = namespace to job
            completeJob(namespace, job)
        }
        assertEquals(CLIENT_NAMESPACE to jobName(order), started)

        val job = client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get()
        assertNotNull(job, "The Job is left to its TTL")
        val secret = client.secrets().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get()
        assertNotNull(secret, "Secret created in the namespace of the Job")
        val owner = secret.metadata.ownerReferences.single()
        assertEquals("batch/v1", owner.apiVersion)
        assertEquals("Job", owner.kind)
        assertEquals(jobName(order), owner.name)
        assertEquals(job.metadata.uid, owner.uid)
    }

    @Test
    fun `Namespace from the properties rather than the client`() {
        properties.namespace = "jobs"
        val order = uuid()
        var started: Pair<String, String>? = null
        runner().run(manifests(order)) { namespace, job ->
            started = namespace to job
            completeJob(namespace, job)
        }
        assertEquals("jobs" to jobName(order), started)
        assertNotNull(client.batch().v1().jobs().inNamespace("jobs").withName(jobName(order)).get())
        assertNotNull(client.secrets().inNamespace("jobs").withName(jobName(order)).get())
        assertNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get())
    }

    // ---------------------------------------------------------------------------------------------
    // Leftovers
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Leftover Job of the same order deleted before the creation, Job of another order untouched`() {
        val order = uuid()
        val other = uuid()
        // Leftover of the same order, under another name to tell it from the new one
        client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).resource(
            JobBuilder(manifests(order).job).editMetadata().withName("leftover").endMetadata().build()
        ).create()
        // Leftover Secret of the same order, with the same name as the new one
        client.secrets().inNamespace(CLIENT_NAMESPACE).resource(
            SecretBuilder(manifests(order).credentialsSecret)
                .editMetadata().addToLabels("old", "true").endMetadata()
                .build()
        ).create()
        // Job of another order
        client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).resource(manifests(other).job).create()

        runner().run(manifests(order)) { namespace, job -> completeJob(namespace, job) }

        val jobs = client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).list().items.map { it.metadata.name }.toSet()
        assertEquals(setOf(jobName(order), jobName(other)), jobs)
        val secret = client.secrets().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get()
        assertNull(secret.metadata.labels["old"], "The leftover Secret has been replaced")
    }

    // ---------------------------------------------------------------------------------------------
    // Creation failures
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `API failure at the creation of the Job is retryable`() {
        server.expect().post().withPath("/apis/batch/v1/namespaces/$CLIENT_NAMESPACE/jobs")
            .andReturn(500, "Boom").once()
        var started = false
        val ex = assertFailsWith<KubernetesPostProcessingTransientException> {
            runner().run(manifests(uuid())) { _, _ -> started = true }
        }
        assertIs<AutoVersioningRetryableException>(ex)
        assertFalse(started)
    }

    @Test
    fun `API failure at the creation of the Secret is retryable and deletes the Job`() {
        val order = uuid()
        server.expect().post().withPath("/api/v1/namespaces/$CLIENT_NAMESPACE/secrets")
            .andReturn(503, "Unavailable").once()
        var started = false
        assertFailsWith<KubernetesPostProcessingTransientException> {
            runner().run(manifests(order)) { _, _ -> started = true }
        }
        assertFalse(started)
        assertNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get())
    }

    @Test
    fun `A refusal of the API at the creation of the Job is not retryable`() {
        server.expect().post().withPath("/apis/batch/v1/namespaces/$CLIENT_NAMESPACE/jobs")
            .andReturn(403, "Forbidden").once()
        val ex = assertFailsWith<KubernetesPostProcessingConfigException> {
            runner().run(manifests(uuid())) { _, _ -> }
        }
        assertFalse(ex is AutoVersioningRetryableException, "A missing Role is not fixed by a retry")
    }

    @Test
    fun `A refusal of the API at the creation of the Secret is not retryable and deletes the Job`() {
        val order = uuid()
        server.expect().post().withPath("/api/v1/namespaces/$CLIENT_NAMESPACE/secrets")
            .andReturn(403, "Forbidden").once()
        assertFailsWith<KubernetesPostProcessingConfigException> {
            runner().run(manifests(order)) { _, _ -> }
        }
        assertNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get())
    }

    @Test
    fun `No namespace at all is not retryable`() {
        val config = Config.empty()
        config.masterUrl = server.url("/")
        config.isTrustCerts = true
        KubernetesClientBuilder().withConfig(config).build().use { noNamespaceClient ->
            val runner = KubernetesJobRunnerImpl(properties, noNamespaceClient, Duration.ofSeconds(5), Duration.ofSeconds(10))
            assertFailsWith<KubernetesPostProcessingConfigException> {
                runner.run(manifests(uuid())) { _, _ -> }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Step failures
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Failure in the clone step`() {
        stepFailure(
            step = KubernetesJobConstants.STEP_CLONE,
            exitCode = 128,
            clone = terminated("clone", 128),
            run = waiting("run", "PodInitializing"),
            push = waiting("push", "PodInitializing"),
        )
    }

    @Test
    fun `Failure in the run step`() {
        stepFailure(
            step = KubernetesJobConstants.STEP_RUN,
            exitCode = 2,
            clone = terminated("clone", 0),
            run = terminated("run", 2),
            push = waiting("push", "PodInitializing"),
        )
    }

    @Test
    fun `Failure in the push step`() {
        stepFailure(
            step = KubernetesJobConstants.STEP_PUSH,
            exitCode = 1,
            clone = terminated("clone", 0),
            run = terminated("run", 0),
            push = terminated("push", 1),
        )
    }

    private fun stepFailure(
        step: String,
        exitCode: Int,
        clone: ContainerStatus,
        run: ContainerStatus,
        push: ContainerStatus,
    ) {
        val order = uuid()
        val pod = "${jobName(order)}-abcde"
        server.expect().get()
            .withPath("/api/v1/namespaces/$CLIENT_NAMESPACE/pods/$pod/log?pretty=false&container=$step&tailLines=5")
            .andReturn(200, "line 1\nline 2\n")
            .always()
        val ex = assertFailsWith<KubernetesPostProcessingFailureException> {
            runner().run(manifests(order)) { namespace, job ->
                createPod(namespace, job, pod, listOf(clone, run), listOf(push))
                failJob(namespace, job, "BackoffLimitExceeded", "Job has reached the specified backoff limit")
            }
        }
        assertEquals(CLIENT_NAMESPACE, ex.namespace)
        assertEquals(jobName(order), ex.job)
        assertEquals(step, ex.step)
        assertEquals(exitCode, ex.exitCode)
        assertFalse(ex.timedOut)
        assertEquals("line 1\nline 2\n", ex.logs)
        assertTrue(ex.message!!.contains("failed in the `$step` step with exit code $exitCode"), ex.message)
        // Left to its TTL, so that an admin can look
        assertNotNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get())
    }

    @Test
    fun `Unreadable logs give empty logs`() {
        val order = uuid()
        server.expect().get()
            .withPath("/api/v1/namespaces/$CLIENT_NAMESPACE/pods/${jobName(order)}-abcde/log?pretty=false&container=run&tailLines=5")
            .andReturn(500, "Boom")
            .always()
        val ex = assertFailsWith<KubernetesPostProcessingFailureException> {
            runner().run(manifests(order)) { namespace, job ->
                createPod(
                    namespace, job, "${job}-abcde",
                    listOf(terminated("clone", 0), terminated("run", 3)),
                    listOf(waiting("push", "PodInitializing")),
                )
                failJob(namespace, job, "BackoffLimitExceeded", null)
            }
        }
        assertEquals(KubernetesJobConstants.STEP_RUN, ex.step)
        assertEquals(3, ex.exitCode)
        assertEquals("", ex.logs)
    }

    // ---------------------------------------------------------------------------------------------
    // Timeouts
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Timeout of the Job, with the last step seen running`() {
        val order = uuid()
        val job = jobName(order)
        val ex = assertFailsWith<KubernetesPostProcessingFailureException> {
            runner().run(manifests(order)) { namespace, name ->
                createPod(
                    namespace, name, "$name-abcde",
                    listOf(terminated("clone", 0), waiting("run", "ImagePullBackOff", "Back-off pulling image")),
                    listOf(waiting("push", "PodInitializing")),
                )
                // Then the deadline fires, and Kubernetes deletes the pod
                failJob(namespace, name, "DeadlineExceeded", "Job was active longer than specified deadline")
                // ... but the first reading of the runner sees it still running, with its pod
                // stuck in the run step (registered last: the helpers above read the Job too)
                server.expect().get().withPath("/apis/batch/v1/namespaces/$CLIENT_NAMESPACE/jobs/$job")
                    .andReply(200) { runningJob(order) }
                    .once()
            }
        }
        assertTrue(ex.timedOut)
        assertNull(ex.exitCode)
        assertEquals(KubernetesJobConstants.STEP_RUN, ex.step)
        assertTrue(ex.logs.contains("ImagePullBackOff"), ex.logs)
        assertTrue(ex.message!!.contains("timed out in the `run` step"), ex.message)
    }

    @Test
    fun `Own deadline of the runner deletes the Job`() {
        val order = uuid()
        val ex = assertFailsWith<KubernetesPostProcessingFailureException> {
            runner(deadlineGrace = Duration.ofMillis(200)).run(manifests(order, timeoutSeconds = 0)) { namespace, name ->
                createPod(
                    namespace, name, "$name-abcde",
                    listOf(terminated("clone", 0), running("run")),
                    listOf(waiting("push", "PodInitializing")),
                )
            }
        }
        assertTrue(ex.timedOut)
        assertNull(ex.exitCode)
        assertEquals(KubernetesJobConstants.STEP_RUN, ex.step)
        assertNull(
            client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get(),
            "The Job is deleted so that it cannot push later"
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Polling
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `API error while polling is tolerated`() {
        val order = uuid()
        val job = jobName(order)
        runner().run(manifests(order)) { namespace, name ->
            completeJob(namespace, name)
            // The first three readings of the runner fail (registered last: the helper reads the Job too)
            server.expect().get().withPath("/apis/batch/v1/namespaces/$CLIENT_NAMESPACE/jobs/$job")
                .andReturn(500, "Boom")
                .times(3)
        }
        // The three errors have been served to the runner: the Job reads normally again
        assertNotNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(job).get())
    }

    @Test
    fun `Interruption while polling deletes the Job and restores the interrupt flag`() {
        val order = uuid()
        var interrupted = false
        val thread = Thread {
            try {
                runner().run(manifests(order)) { _, _ -> Thread.currentThread().interrupt() }
            } catch (_: KubernetesPostProcessingFailureException) {
                interrupted = Thread.currentThread().isInterrupted
            }
        }
        thread.start()
        thread.join(10_000)
        assertTrue(interrupted, "Interrupt flag restored")
        assertNull(client.batch().v1().jobs().inNamespace(CLIENT_NAMESPACE).withName(jobName(order)).get())
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private fun uuid() = UUID.randomUUID().toString()

    private fun jobName(order: String) = KubernetesJobConstants.jobName(order)

    private fun manifests(order: String, timeoutSeconds: Long = 60) = KubernetesJobManifests(
        job = JobBuilder()
            .withNewMetadata()
            .withName(jobName(order))
            .addToLabels(KubernetesJobConstants.LABEL_ORDER, order)
            .endMetadata()
            .withNewSpec()
            .withBackoffLimit(0)
            .withActiveDeadlineSeconds(timeoutSeconds)
            .withNewTemplate()
            .withNewSpec()
            .withRestartPolicy("Never")
            .addNewInitContainer().withName("clone").withImage("alpine/git").endInitContainer()
            .addNewInitContainer().withName("run").withImage("busybox").endInitContainer()
            .addNewContainer().withName("push").withImage("alpine/git").endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build(),
        credentialsSecret = SecretBuilder()
            .withNewMetadata()
            .withName(jobName(order))
            .addToLabels(KubernetesJobConstants.LABEL_ORDER, order)
            .endMetadata()
            .addToStringData("password", "secret")
            .build(),
        timeoutSeconds = timeoutSeconds,
    )

    private fun runningJob(order: String): Job =
        JobBuilder(manifests(order).job)
            .editMetadata().withNamespace(CLIENT_NAMESPACE).endMetadata()
            .withNewStatus().withActive(1).endStatus()
            .build()

    private fun completeJob(namespace: String, name: String) {
        setJobCondition(namespace, name, "Complete", null, null)
    }

    private fun failJob(namespace: String, name: String, reason: String, message: String?) {
        setJobCondition(namespace, name, "Failed", reason, message)
    }

    private fun setJobCondition(namespace: String, name: String, type: String, reason: String?, message: String?) {
        client.batch().v1().jobs().inNamespace(namespace).withName(name).edit { job ->
            JobBuilder(job)
                .withNewStatus()
                .addToConditions(
                    JobConditionBuilder()
                        .withType(type)
                        .withStatus("True")
                        .withReason(reason)
                        .withMessage(message)
                        .build()
                )
                .endStatus()
                .build()
        }
    }

    private fun createPod(
        namespace: String,
        job: String,
        pod: String,
        initContainerStatuses: List<ContainerStatus>,
        containerStatuses: List<ContainerStatus>,
    ) {
        val uid = client.batch().v1().jobs().inNamespace(namespace).withName(job).get().metadata.uid
        client.pods().inNamespace(namespace).resource(
            PodBuilder()
                .withNewMetadata()
                .withName(pod)
                .addToLabels(KubernetesJobRunnerImpl.LABEL_JOB_NAME, job)
                .addNewOwnerReference()
                .withApiVersion("batch/v1").withKind("Job").withName(job).withUid(uid)
                .endOwnerReference()
                .endMetadata()
                .withNewStatus()
                .withInitContainerStatuses(initContainerStatuses)
                .withContainerStatuses(containerStatuses)
                .endStatus()
                .build()
        ).create()
    }

    private fun terminated(name: String, exitCode: Int) = ContainerStatusBuilder()
        .withName(name)
        .withNewState().withNewTerminated().withExitCode(exitCode).endTerminated().endState()
        .build()

    private fun waiting(name: String, reason: String, message: String? = null) = ContainerStatusBuilder()
        .withName(name)
        .withNewState().withNewWaiting().withReason(reason).withMessage(message).endWaiting().endState()
        .build()

    private fun running(name: String) = ContainerStatusBuilder()
        .withName(name)
        .withNewState().withNewRunning().endRunning().endState()
        .build()

    companion object {
        private const val CLIENT_NAMESPACE = "yontrack"
    }
}
