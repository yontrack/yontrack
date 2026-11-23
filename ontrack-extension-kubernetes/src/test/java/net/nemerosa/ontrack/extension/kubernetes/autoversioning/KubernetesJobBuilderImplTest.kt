package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.Quantity
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.extension.scm.service.SCMCloneCredentials
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KubernetesJobBuilderImplTest {

    private val uuid = "0F9C4F8E-2D7A-4C1B-9E3F-1A2B3C4D5E6F"
    private val jobName = "yontrack-av-0f9c4f8e-2d7a-4c1b-9e3f-1a2b3c4d5e6f"

    private fun properties(code: KubernetesConfigProperties.() -> Unit = {}) =
        KubernetesConfigProperties().apply(code)

    private fun request(
        orderUuid: String = uuid,
        dockerImage: String = "registry.example.com/tools/node:22",
        dockerCommand: String = "npm install --package-lock-only",
        commitMessage: String = "Post-processing for 2.0.0",
        env: List<KubernetesPostProcessingEnv> = emptyList(),
        secrets: List<KubernetesPostProcessingSecret> = emptyList(),
        timeoutSeconds: Int? = null,
        cloneAccess: SCMCloneAccess = SCMCloneAccess(
            url = "https://github.com/org/repo.git",
            credentials = SCMCloneCredentials(username = "x-access-token", password = "s3cr3t-token"),
        ),
    ) = KubernetesJobRequest(
        orderUuid = orderUuid,
        dockerImage = dockerImage,
        dockerCommand = dockerCommand,
        commitMessage = commitMessage,
        env = env,
        secrets = secrets,
        timeoutSeconds = timeoutSeconds,
        version = "2.0.0",
        upgradeBranch = "feature/auto-upgrade-dep-2.0.0",
        repositoryUri = "https://github.com/org/repo",
        project = "my-project",
        branch = "main",
        cloneAccess = cloneAccess,
    )

    private fun build(
        properties: KubernetesConfigProperties = properties(),
        request: KubernetesJobRequest = request(),
    ) = KubernetesJobBuilderImpl(properties).build(request)

    private fun KubernetesJobManifests.containers(): List<Container> =
        job.spec.template.spec.initContainers + job.spec.template.spec.containers

    private fun KubernetesJobManifests.step(name: String): Container =
        containers().single { it.name == name }

    private fun KubernetesJobManifests.credentialsVolumeName(): String =
        job.spec.template.spec.volumes.single { it.secret?.secretName == credentialsSecret.metadata.name }.name

    // ------------------------------------------------------------------------------------------
    // Shape

    @Test
    fun `containers in order with their images`() {
        val manifests = build()
        val spec = manifests.job.spec.template.spec
        assertEquals(listOf("clone", "run"), spec.initContainers.map { it.name })
        assertEquals(listOf("push"), spec.containers.map { it.name })
        assertEquals(KubernetesJobConstants.STEPS, manifests.containers().map { it.name })
        assertEquals(KubernetesConfigProperties.DEFAULT_HARNESS_IMAGE, manifests.step("clone").image)
        assertEquals("registry.example.com/tools/node:22", manifests.step("run").image)
        assertEquals(KubernetesConfigProperties.DEFAULT_HARNESS_IMAGE, manifests.step("push").image)
    }

    @Test
    fun `harness image from the properties`() {
        val manifests = build(properties { harness.image = "registry.example.com/git:1" })
        assertEquals("registry.example.com/git:1", manifests.step("clone").image)
        assertEquals("registry.example.com/git:1", manifests.step("push").image)
    }

    @Test
    fun `job and secret names, no namespace, no owner reference`() {
        val manifests = build()
        assertEquals(jobName, manifests.job.metadata.name)
        assertEquals(jobName, manifests.credentialsSecret.metadata.name)
        assertNull(manifests.job.metadata.namespace)
        assertNull(manifests.credentialsSecret.metadata.namespace)
        assertTrue(manifests.job.metadata.ownerReferences.isNullOrEmpty())
        assertTrue(manifests.credentialsSecret.metadata.ownerReferences.isNullOrEmpty())
    }

    @Test
    fun labels() {
        val manifests = build()
        val expected = mapOf(
            KubernetesJobConstants.LABEL_ORDER to uuid,
            KubernetesJobConstants.LABEL_MANAGED_BY to KubernetesJobConstants.LABEL_MANAGED_BY_VALUE,
        )
        listOf(
            manifests.job.metadata.labels,
            manifests.job.spec.template.metadata.labels,
            manifests.credentialsSecret.metadata.labels,
        ).forEach { labels ->
            expected.forEach { (key, value) -> assertEquals(value, labels[key], key) }
        }
    }

    @Test
    fun `job settings`() {
        val manifests = build(properties {
            job.ttl = Duration.ofMinutes(20)
            job.imagePullSecrets = listOf("pull-one", "pull-two")
        })
        val spec = manifests.job.spec
        assertEquals(0, spec.backoffLimit)
        assertEquals(1200, spec.ttlSecondsAfterFinished)
        val pod = spec.template.spec
        assertEquals("Never", pod.restartPolicy)
        assertEquals(false, pod.automountServiceAccountToken)
        assertEquals(listOf("pull-one", "pull-two"), pod.imagePullSecrets.map { it.name })
    }

    @Test
    fun `no image pull secrets by default`() {
        assertTrue(build().job.spec.template.spec.imagePullSecrets.isNullOrEmpty())
    }

    // ------------------------------------------------------------------------------------------
    // Timeout

    @Test
    fun `timeout defaults to the job timeout`() {
        val manifests = build(properties { job.timeout = Duration.ofMinutes(7) })
        assertEquals(420L, manifests.timeoutSeconds)
        assertEquals(420L, manifests.job.spec.activeDeadlineSeconds)
    }

    @Test
    fun `explicit timeout`() {
        val manifests = build(request = request(timeoutSeconds = 300))
        assertEquals(300L, manifests.timeoutSeconds)
        assertEquals(300L, manifests.job.spec.activeDeadlineSeconds)
    }

    @Test
    fun `explicit timeout capped by the max timeout`() {
        val manifests = build(
            properties { job.maxTimeout = Duration.ofMinutes(15) },
            request(timeoutSeconds = 3600),
        )
        assertEquals(900L, manifests.timeoutSeconds)
        assertEquals(900L, manifests.job.spec.activeDeadlineSeconds)
    }

    @Test
    fun `default timeout capped by the max timeout`() {
        val manifests = build(properties {
            job.timeout = Duration.ofMinutes(20)
            job.maxTimeout = Duration.ofMinutes(15)
        })
        assertEquals(900L, manifests.timeoutSeconds)
    }

    // ------------------------------------------------------------------------------------------
    // Security

    @Test
    fun `pod security context`() {
        val manifests = build(properties {
            job.runAsUser = 2000
            job.runAsGroup = 3000
            job.fsGroup = 4000
        })
        val context = manifests.job.spec.template.spec.securityContext
        assertEquals(2000L, context.runAsUser)
        assertEquals(3000L, context.runAsGroup)
        assertEquals(4000L, context.fsGroup)
        assertEquals(true, context.runAsNonRoot)
        assertEquals("RuntimeDefault", context.seccompProfile.type)
    }

    @Test
    fun `container security context`() {
        build().containers().forEach { container ->
            val context = assertNotNull(container.securityContext, container.name)
            assertEquals(false, context.allowPrivilegeEscalation, container.name)
            assertEquals(listOf("ALL"), context.capabilities.drop, container.name)
            assertTrue(context.capabilities.add.isNullOrEmpty(), container.name)
            assertFalse(context.privileged ?: false, container.name)
        }
    }

    @Test
    fun `run step mounts the work tree only`() {
        val manifests = build()
        val run = manifests.step("run")
        val workTree = manifests.step("clone").volumeMounts.single {
            it.mountPath == KubernetesHarnessScripts.WORK_TREE_PATH
        }.name
        assertEquals(listOf(workTree), run.volumeMounts.map { it.name })
        assertEquals(KubernetesHarnessScripts.WORK_TREE_PATH, run.volumeMounts.single().mountPath)
        assertEquals(KubernetesHarnessScripts.WORK_TREE_PATH, run.workingDir)
    }

    @Test
    fun `run step mounts neither the credentials nor the Git directory`() {
        val manifests = build()
        val run = manifests.step("run")
        val mounted = run.volumeMounts.map { it.name }
        assertFalse(manifests.credentialsVolumeName() in mounted)
        assertFalse(KubernetesHarnessScripts.VOLUME_GIT_DIR in mounted)
        assertFalse(KubernetesHarnessScripts.VOLUME_HARNESS_HOME in mounted)
        assertTrue(run.volumeMounts.none { it.mountPath.startsWith(KubernetesHarnessScripts.GIT_DIR_VOLUME_PATH) })
    }

    @Test
    fun `run step env does not reference the credentials secret`() {
        val manifests = build(
            request = request(
                secrets = listOf(KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN")),
            )
        )
        val run = manifests.step("run")
        assertTrue(run.env.none { it.valueFrom?.secretKeyRef?.name == manifests.credentialsSecret.metadata.name })
        assertTrue(run.envFrom.isNullOrEmpty())
    }

    @Test
    fun `harness steps mount the work tree, the Git directory and the credentials`() {
        val manifests = build()
        listOf("clone", "push").forEach { step ->
            val mounts = manifests.step(step).volumeMounts.associate { it.name to it.mountPath }
            assertEquals(KubernetesHarnessScripts.WORK_TREE_PATH, mounts[KubernetesHarnessScripts.VOLUME_WORK_TREE], step)
            assertEquals(KubernetesHarnessScripts.GIT_DIR_VOLUME_PATH, mounts[KubernetesHarnessScripts.VOLUME_GIT_DIR], step)
            assertEquals(KubernetesHarnessScripts.CREDENTIALS_PATH, mounts[manifests.credentialsVolumeName()], step)
            val credentialsMount = manifests.step(step).volumeMounts.single { it.name == manifests.credentialsVolumeName() }
            assertEquals(true, credentialsMount.readOnly, step)
        }
    }

    @Test
    fun `volumes of the pod`() {
        val manifests = build()
        val volumes = manifests.job.spec.template.spec.volumes.associateBy { it.name }
        assertNotNull(volumes[KubernetesHarnessScripts.VOLUME_WORK_TREE]?.emptyDir)
        assertNotNull(volumes[KubernetesHarnessScripts.VOLUME_GIT_DIR]?.emptyDir)
        assertNotNull(volumes[KubernetesHarnessScripts.VOLUME_HARNESS_HOME]?.emptyDir)
        assertEquals(jobName, volumes[KubernetesHarnessScripts.VOLUME_CREDENTIALS]?.secret?.secretName)
    }

    // ------------------------------------------------------------------------------------------
    // Credentials

    @Test
    fun `credentials secret with username and password`() {
        val secret = build().credentialsSecret
        assertEquals("Opaque", secret.type)
        assertEquals(
            mapOf(
                KubernetesHarnessScripts.CREDENTIALS_USERNAME_KEY to "x-access-token",
                KubernetesHarnessScripts.CREDENTIALS_PASSWORD_KEY to "s3cr3t-token",
            ),
            secret.decoded(),
        )
    }

    @Test
    fun `credentials secret empty for an anonymous access`() {
        val secret = build(
            request = request(cloneAccess = SCMCloneAccess("https://github.com/org/repo.git", null))
        ).credentialsSecret
        assertEquals(jobName, secret.metadata.name)
        assertEquals("Opaque", secret.type)
        assertEquals(emptyMap(), secret.decoded())
    }

    private fun io.fabric8.kubernetes.api.model.Secret.decoded(): Map<String, String> =
        (stringData ?: emptyMap()) +
                (data ?: emptyMap()).mapValues { (_, value) -> String(Base64.getDecoder().decode(value)) }

    @Test
    fun `credentials appear in no container`() {
        val manifests = build()
        manifests.containers().forEach { container ->
            val texts = container.command.orEmpty() + container.args.orEmpty() +
                    container.env.orEmpty().flatMap { listOf(it.name, it.value.orEmpty()) }
            texts.forEach { text ->
                assertFalse("s3cr3t-token" in text, container.name)
                assertFalse("x-access-token" in text, container.name)
            }
            assertTrue(container.envFrom.isNullOrEmpty(), container.name)
            assertTrue(
                container.env.orEmpty().none { it.valueFrom?.secretKeyRef?.name == jobName },
                container.name,
            )
        }
    }

    @Test
    fun `clone URL is passed as an env var without credentials`() {
        val manifests = build()
        listOf("clone", "push").forEach { step ->
            val env = manifests.step(step).env.associate { it.name to it.value }
            assertEquals("https://github.com/org/repo.git", env[KubernetesHarnessScripts.ENV_CLONE_URL], step)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Harness

    @Test
    fun `harness steps run constant scripts through sh`() {
        val manifests = build()
        assertEquals(listOf("sh", "-c", KubernetesHarnessScripts.CLONE_SCRIPT), manifests.step("clone").command)
        assertEquals(listOf("sh", "-c", KubernetesHarnessScripts.PUSH_SCRIPT), manifests.step("push").command)
        assertTrue(manifests.step("clone").args.isNullOrEmpty())
        assertTrue(manifests.step("push").args.isNullOrEmpty())
    }

    @Test
    fun `script text is the same whatever the request`() {
        val a = build()
        val b = build(
            properties { git.userName = "Someone"; git.userEmail = "someone@example.com" },
            request(
                orderUuid = UUID.randomUUID().toString(),
                commitMessage = "\$(rm -rf /) `id` \"'",
                cloneAccess = SCMCloneAccess("https://gitlab.example.com/a/b.git; id", SCMCloneCredentials("u", "p")),
            ),
        )
        listOf("clone", "push").forEach { step ->
            assertEquals(a.step(step).command, b.step(step).command, step)
        }
    }

    @Test
    fun `dynamic values reach the harness as env vars`() {
        val manifests = build(
            properties { git.userName = "Bot"; git.userEmail = "bot@example.com" },
            request(commitMessage = "Message \$(id)"),
        )
        val clone = manifests.step("clone").env.associate { it.name to it.value }
        assertEquals("feature/auto-upgrade-dep-2.0.0", clone[KubernetesHarnessScripts.ENV_UPGRADE_BRANCH])
        assertEquals(KubernetesHarnessScripts.HARNESS_HOME_PATH, clone["HOME"])
        // No system nor global Git configuration in the harness
        assertEquals("1", clone["GIT_CONFIG_NOSYSTEM"])
        assertEquals("/dev/null", clone["GIT_CONFIG_GLOBAL"])
        assertEquals(KubernetesHarnessScripts.GIT_DIR, clone[KubernetesHarnessScripts.ENV_GIT_DIR])
        assertEquals(KubernetesHarnessScripts.WORK_TREE_PATH, clone[KubernetesHarnessScripts.ENV_WORK_TREE])
        assertEquals(KubernetesHarnessScripts.CREDENTIALS_PATH, clone[KubernetesHarnessScripts.ENV_CREDENTIALS_DIR])
        val push = manifests.step("push").env.associate { it.name to it.value }
        assertEquals("feature/auto-upgrade-dep-2.0.0", push[KubernetesHarnessScripts.ENV_UPGRADE_BRANCH])
        assertEquals("Message \$(id)", push[KubernetesHarnessScripts.ENV_COMMIT_MESSAGE])
        assertEquals("Bot", push["GIT_AUTHOR_NAME"])
        assertEquals("bot@example.com", push["GIT_AUTHOR_EMAIL"])
        assertEquals("Bot", push["GIT_COMMITTER_NAME"])
        assertEquals("bot@example.com", push["GIT_COMMITTER_EMAIL"])
    }

    // ------------------------------------------------------------------------------------------
    // Run step

    @Test
    fun `run step command`() {
        val run = build().step("run")
        assertEquals(listOf("sh", "-c", "npm install --package-lock-only"), run.command)
        assertTrue(run.args.isNullOrEmpty())
    }

    @Test
    fun `injected variables`() {
        val env = build().step("run").env.associate { it.name to it.value }
        assertEquals("2.0.0", env["VERSION"])
        assertEquals("feature/auto-upgrade-dep-2.0.0", env["UPGRADE_BRANCH"])
        assertEquals("https://github.com/org/repo", env["REPOSITORY_URI"])
        assertEquals("my-project", env["PROJECT"])
        assertEquals("main", env["BRANCH"])
    }

    @Test
    fun `env and secrets mapping`() {
        val run = build(
            request = request(
                env = listOf(KubernetesPostProcessingEnv("NODE_ENV", "production")),
                secrets = listOf(KubernetesPostProcessingSecret("npm", "token", "NPM_TOKEN")),
            )
        ).step("run")
        assertEquals("production", run.env.single { it.name == "NODE_ENV" }.value)
        val secret = run.env.single { it.name == "NPM_TOKEN" }
        assertNull(secret.value)
        assertEquals("npm", secret.valueFrom.secretKeyRef.name)
        assertEquals("token", secret.valueFrom.secretKeyRef.key)
    }

    @Test
    fun `injected variables win over env`() {
        val run = build(
            request = request(
                env = listOf(
                    KubernetesPostProcessingEnv("VERSION", "hacked"),
                    KubernetesPostProcessingEnv("PROJECT", "hacked"),
                    KubernetesPostProcessingEnv("OTHER", "kept"),
                ),
            )
        ).step("run")
        assertEquals(run.env.map { it.name }.distinct(), run.env.map { it.name })
        val env = run.env.associate { it.name to it.value }
        assertEquals("2.0.0", env["VERSION"])
        assertEquals("my-project", env["PROJECT"])
        assertEquals("kept", env["OTHER"])
    }

    @Test
    fun `injected variables win over secrets`() {
        val run = build(
            request = request(
                secrets = listOf(
                    KubernetesPostProcessingSecret("s", "k", "BRANCH"),
                    KubernetesPostProcessingSecret("s", "k", "UPGRADE_BRANCH"),
                ),
            )
        ).step("run")
        assertEquals(run.env.map { it.name }.distinct(), run.env.map { it.name })
        val branch = run.env.single { it.name == "BRANCH" }
        assertEquals("main", branch.value)
        assertNull(branch.valueFrom)
        assertEquals("feature/auto-upgrade-dep-2.0.0", run.env.single { it.name == "UPGRADE_BRANCH" }.value)
    }

    @Test
    fun `no duplicate env names between env and secrets`() {
        val run = build(
            request = request(
                env = listOf(KubernetesPostProcessingEnv("TOKEN", "plain")),
                secrets = listOf(KubernetesPostProcessingSecret("s", "k", "TOKEN")),
            )
        ).step("run")
        assertEquals(run.env.map { it.name }.distinct(), run.env.map { it.name })
        assertEquals("s", run.env.single { it.name == "TOKEN" }.valueFrom?.secretKeyRef?.name)
    }

    @Test
    fun `run step resources`() {
        val run = build(properties {
            job.resources.requests.cpu = "250m"
            job.resources.requests.memory = "512Mi"
            job.resources.limits.cpu = "2"
            job.resources.limits.memory = "2Gi"
        }).step("run")
        assertEquals(mapOf("cpu" to Quantity("250m"), "memory" to Quantity("512Mi")), run.resources.requests)
        assertEquals(mapOf("cpu" to Quantity("2"), "memory" to Quantity("2Gi")), run.resources.limits)
    }

    @Test
    fun `blank resource quantities are omitted`() {
        val run = build(properties {
            job.resources.requests.cpu = ""
            job.resources.requests.memory = "128Mi"
            job.resources.limits.cpu = " "
            job.resources.limits.memory = ""
        }).step("run")
        assertEquals(mapOf("memory" to Quantity("128Mi")), run.resources.requests)
        assertTrue(run.resources.limits.isNullOrEmpty())
    }

}
