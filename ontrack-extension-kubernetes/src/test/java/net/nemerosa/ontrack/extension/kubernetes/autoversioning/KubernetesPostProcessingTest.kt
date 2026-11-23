package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingMissingConfigException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException
import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.extension.scm.service.SCMCloneCredentials
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.node.NullNode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KubernetesPostProcessingTest {

    private lateinit var properties: KubernetesConfigProperties
    private lateinit var jobBuilder: KubernetesJobBuilder
    private lateinit var jobRunner: KubernetesJobRunner
    private lateinit var processing: KubernetesPostProcessing
    private lateinit var scm: SCM
    private lateinit var order: AutoVersioningOrder
    private lateinit var avTemplateRenderer: AutoVersioningTemplateRenderer
    private lateinit var manifests: KubernetesJobManifests

    private val cloneAccess = SCMCloneAccess(
        url = "https://github.com/org/repo.git",
        credentials = SCMCloneCredentials("x-access-token", "secret-token"),
    )

    @BeforeEach
    fun init() {
        properties = KubernetesConfigProperties().apply {
            allowed.images = listOf("registry.example.com/tools/*")
            allowed.secrets = listOf("npm-token")
        }
        manifests = mockk()
        jobBuilder = mockk()
        every { jobBuilder.build(any()) } returns manifests
        jobRunner = mockk()
        every { jobRunner.run(any(), any()) } answers {
            secondArg<(String, String) -> Unit>()("yontrack", "yontrack-av-uuid")
        }
        processing = KubernetesPostProcessing(
            extensionFeature = mockk(),
            kubernetesConfigProperties = properties,
            jobBuilder = jobBuilder,
            jobRunner = jobRunner,
        )

        scm = mockk()
        every { scm.type } returns "github"
        every { scm.engine } returns "github"
        every { scm.getCloneAccess() } returns cloneAccess

        val project = mockk<Project>()
        every { project.name } returns "my-project"
        val branch = mockk<Branch>()
        every { branch.name } returns "release-1.0"
        every { branch.project } returns project

        order = mockk()
        every { order.uuid } returns "UUID-1"
        every { order.targetVersion } returns "2.0.0"
        every { order.branch } returns branch
        every { order.defaultPath } returns AutoVersioningSourceConfigPath(path = "gradle.properties")

        // Identity renderer by default
        avTemplateRenderer = mockk()
        every { avTemplateRenderer.render(any(), PlainEventRenderer.INSTANCE) } answers { firstArg() }
    }

    // Identity

    @Test
    fun `ID and name`() {
        assertEquals("kubernetes", processing.id)
        assertEquals("Kubernetes job post processing", processing.name)
    }

    // Parsing

    @Test
    fun `Parsing a full configuration`() {
        val config = processing.parseAndValidate(
            """
                {
                    "dockerImage": "registry.example.com/tools/gradle:8",
                    "dockerCommand": "./gradlew resolveAndLockAll --write-locks",
                    "commitMessage": "Locks for ${'$'}{VERSION}",
                    "env": [
                        {"name": "GRADLE_OPTS", "value": "-Xmx1g"}
                    ],
                    "secrets": [
                        {"secret": "npm-token", "key": "token", "env": "NPM_TOKEN"}
                    ],
                    "timeoutSeconds": 600
                }
            """.trimIndent().parseAsJson()
        )
        assertEquals(
            KubernetesPostProcessingConfig(
                dockerImage = "registry.example.com/tools/gradle:8",
                dockerCommand = "./gradlew resolveAndLockAll --write-locks",
                commitMessage = "Locks for ${'$'}{VERSION}",
                env = listOf(KubernetesPostProcessingEnv("GRADLE_OPTS", "-Xmx1g")),
                secrets = listOf(KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN")),
                timeoutSeconds = 600,
            ),
            config
        )
    }

    @Test
    fun `Parsing a minimal configuration`() {
        val config = processing.parseAndValidate(
            """
                {
                    "dockerImage": "registry.example.com/tools/gradle:8",
                    "dockerCommand": "./gradlew build"
                }
            """.trimIndent().parseAsJson()
        )
        assertEquals(
            KubernetesPostProcessingConfig(
                dockerImage = "registry.example.com/tools/gradle:8",
                dockerCommand = "./gradlew build",
            ),
            config
        )
        assertNull(config.commitMessage)
        assertTrue(config.env.isEmpty())
        assertTrue(config.secrets.isEmpty())
        assertNull(config.timeoutSeconds)
    }

    @Test
    fun `Missing configuration`() {
        assertThrows<PostProcessingMissingConfigException> {
            processing.parseAndValidate(null)
        }
        assertThrows<PostProcessingMissingConfigException> {
            processing.parseAndValidate(NullNode.instance)
        }
    }

    // Validation

    private fun assertInvalid(json: String, vararg expected: String) {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            processing.parseAndValidate(json.parseAsJson())
        }
        expected.forEach {
            assertTrue(it in ex.message!!, "'$it' expected in: ${ex.message}")
        }
    }

    @Test
    fun `Blank image`() {
        assertInvalid("""{"dockerImage": " ", "dockerCommand": "make"}""", "dockerImage")
    }

    @Test
    fun `Blank command`() {
        assertInvalid("""{"dockerImage": "registry.example.com/tools/x", "dockerCommand": ""}""", "dockerCommand")
    }

    @Test
    fun `Invalid environment variable name`() {
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "env": [{"name": "1ABC", "value": "x"}]}""",
            "1ABC"
        )
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "env": [{"name": "A-B", "value": "x"}]}""",
            "A-B"
        )
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "env": [{"name": "", "value": "x"}]}""",
            "env"
        )
    }

    @Test
    fun `Invalid secret environment variable name`() {
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "secrets": [{"secret": "s", "key": "k", "env": "MY VAR"}]}""",
            "MY VAR"
        )
    }

    @Test
    fun `Blank secret name`() {
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "secrets": [{"secret": " ", "key": "k", "env": "VAR"}]}""",
            "secret"
        )
    }

    @Test
    fun `Blank secret key`() {
        assertInvalid(
            """{"dockerImage": "i", "dockerCommand": "c", "secrets": [{"secret": "s", "key": "", "env": "VAR"}]}""",
            "key"
        )
    }

    @Test
    fun `Timeout must be positive`() {
        assertInvalid("""{"dockerImage": "i", "dockerCommand": "c", "timeoutSeconds": 0}""", "timeoutSeconds")
        assertInvalid("""{"dockerImage": "i", "dockerCommand": "c", "timeoutSeconds": -10}""", "timeoutSeconds")
    }

    @Test
    fun `Valid names`() {
        processing.parseAndValidate(
            """
                {
                    "dockerImage": "i",
                    "dockerCommand": "c",
                    "env": [{"name": "_A1", "value": ""}, {"name": "lower_case", "value": "x"}],
                    "secrets": [{"secret": "s", "key": "k", "env": "Z_9"}],
                    "timeoutSeconds": 1
                }
            """.trimIndent().parseAsJson()
        )
    }

    // Allow-lists

    @Test
    fun `Image refused when the list of images is empty`() {
        properties.allowed.images = emptyList()
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config())
        }
        assertTrue(KubernetesConfigProperties.ALLOWED_IMAGES in ex.message!!, ex.message)
        assertNothingRun()
    }

    @Test
    fun `Image refused when matching no pattern`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config(dockerImage = "docker.io/library/alpine:3"))
        }
        assertTrue("docker.io/library/alpine:3" in ex.message!!, ex.message)
        assertTrue(KubernetesConfigProperties.ALLOWED_IMAGES in ex.message!!, ex.message)
        assertNothingRun()
    }

    @Test
    fun `The rendered image is checked against the allow-list`() {
        every {
            avTemplateRenderer.render("${'$'}{IMAGE}", PlainEventRenderer.INSTANCE)
        } returns "docker.io/evil:1"
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config(dockerImage = "${'$'}{IMAGE}"))
        }
        assertTrue("docker.io/evil:1" in ex.message!!, ex.message)
        assertNothingRun()
    }

    @Test
    fun `An image rendered as blank is refused`() {
        every {
            avTemplateRenderer.render("${'$'}{IMAGE}", PlainEventRenderer.INSTANCE)
        } returns " "
        assertThrows<KubernetesPostProcessingConfigException> {
            run(config(dockerImage = "${'$'}{IMAGE}"))
        }
        assertNothingRun()
    }

    @Test
    fun `Secret refused`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(
                config(
                    secrets = listOf(KubernetesPostProcessingSecret("db-password", "password", "DB_PASSWORD"))
                )
            )
        }
        assertTrue("db-password" in ex.message!!, ex.message)
        assertTrue(KubernetesConfigProperties.ALLOWED_SECRETS in ex.message!!, ex.message)
        assertNothingRun()
    }

    @Test
    fun `Secret refused when the list of Secrets is empty`() {
        properties.allowed.secrets = emptyList()
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config(secrets = listOf(KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN"))))
        }
        assertTrue(KubernetesConfigProperties.ALLOWED_SECRETS in ex.message!!, ex.message)
        assertNothingRun()
    }

    @Test
    fun `No Secret needs no allowed Secret`() {
        properties.allowed.secrets = emptyList()
        run(config())
        verify { jobRunner.run(manifests, any()) }
    }

    // Templating & request

    @Test
    fun `Templating of the image, command, commit message and environment values`() {
        every { avTemplateRenderer.render("registry.example.com/tools/gradle:${'$'}{JDK}", PlainEventRenderer.INSTANCE) } returns "registry.example.com/tools/gradle:jdk25"
        every { avTemplateRenderer.render("./update.sh ${'$'}${'$'}{VERSION}", PlainEventRenderer.INSTANCE) } returns "./update.sh ${'$'}{VERSION}"
        every { avTemplateRenderer.render("Locks for ${'$'}{VERSION}", PlainEventRenderer.INSTANCE) } returns "Locks for 2.0.0"
        every { avTemplateRenderer.render("${'$'}{sourceBuild.release}", PlainEventRenderer.INSTANCE) } returns "my-release"

        val request = run(
            config(
                dockerImage = "registry.example.com/tools/gradle:${'$'}{JDK}",
                dockerCommand = "./update.sh ${'$'}${'$'}{VERSION}",
                commitMessage = "Locks for ${'$'}{VERSION}",
                env = listOf(KubernetesPostProcessingEnv("RELEASE", "${'$'}{sourceBuild.release}")),
            )
        )

        assertEquals("registry.example.com/tools/gradle:jdk25", request.dockerImage)
        assertEquals("./update.sh ${'$'}{VERSION}", request.dockerCommand)
        assertEquals("Locks for 2.0.0", request.commitMessage)
        assertEquals(listOf(KubernetesPostProcessingEnv("RELEASE", "my-release")), request.env)
    }

    @Test
    fun `Default commit message`() {
        val request = run(config(commitMessage = null))
        assertEquals("Post processing for version change in gradle.properties for 2.0.0", request.commitMessage)
    }

    @Test
    fun `Blank commit message is defaulted`() {
        val request = run(config(commitMessage = " "))
        assertEquals("Post processing for version change in gradle.properties for 2.0.0", request.commitMessage)
    }

    @Test
    fun `Fields of the request`() {
        val secrets = listOf(KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN"))
        val request = run(config(secrets = secrets, timeoutSeconds = 300))
        assertEquals(
            KubernetesJobRequest(
                orderUuid = "UUID-1",
                dockerImage = "registry.example.com/tools/gradle:8",
                dockerCommand = "./gradlew build",
                commitMessage = "Post processing",
                env = emptyList(),
                secrets = secrets,
                timeoutSeconds = 300,
                version = "2.0.0",
                upgradeBranch = "feature/auto-upgrade-x-2.0.0",
                repositoryUri = "https://github.com/org/repo.git",
                project = "my-project",
                branch = "release-1.0",
                cloneAccess = cloneAccess,
            ),
            request
        )
        assertSame(cloneAccess, request.cloneAccess)
    }

    @Test
    fun `Reserved variables cannot be overridden by env or secrets`() {
        properties.allowed.secrets = listOf("npm-token")
        val request = run(
            config(
                env = listOf(
                    KubernetesPostProcessingEnv("VERSION", "0.0.1"),
                    KubernetesPostProcessingEnv("UPGRADE_BRANCH", "main"),
                    KubernetesPostProcessingEnv("REPOSITORY_URI", "x"),
                    KubernetesPostProcessingEnv("PROJECT", "x"),
                    KubernetesPostProcessingEnv("BRANCH", "x"),
                    KubernetesPostProcessingEnv("KEPT", "yes"),
                ),
                secrets = listOf(
                    KubernetesPostProcessingSecret("npm-token", "token", "VERSION"),
                    KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN"),
                ),
            )
        )
        assertEquals(listOf(KubernetesPostProcessingEnv("KEPT", "yes")), request.env)
        assertEquals(listOf(KubernetesPostProcessingSecret("npm-token", "token", "NPM_TOKEN")), request.secrets)
        assertEquals("2.0.0", request.version)
    }

    @Test
    fun `Namespace and job are reported through onPostProcessingInfo`() {
        val infos = mutableListOf<PostProcessingInfo>()
        run(config()) { infos += it }
        assertEquals(1, infos.size)
        assertEquals(mapOf("namespace" to "yontrack", "job" to "yontrack-av-uuid"), infos.first().data)
    }

    // SCM

    @Test
    fun `SCM without clone access`() {
        every { scm.getCloneAccess() } returns null
        every { scm.type } returns "mock"
        every { scm.engine } returns "mock-engine"
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config())
        }
        val message = ex.message!!
        assertTrue("mock" in message, message)
        assertTrue("mock-engine" in message, message)
        listOf("GitHub", "GitLab", "Bitbucket Server", "Bitbucket Cloud").forEach {
            assertTrue(it in message, "'$it' expected in: $message")
        }
        assertNothingRun()
    }

    @Test
    fun `Clone access is asked for after the validation`() {
        properties.allowed.images = emptyList()
        assertThrows<KubernetesPostProcessingConfigException> {
            run(config())
        }
        verify(exactly = 0) { scm.getCloneAccess() }
    }

    // Runner exceptions

    @Test
    fun `A transient failure of the runner is propagated unchanged and stays retryable`() {
        val transient = KubernetesPostProcessingTransientException(RuntimeException("API down"))
        every { jobRunner.run(any(), any()) } throws transient
        val ex = assertThrows<KubernetesPostProcessingTransientException> {
            run(config())
        }
        assertSame(transient, ex)
        assertIs<AutoVersioningRetryableException>(ex)
    }

    @Test
    fun `A failure of the job is propagated unchanged with an empty link`() {
        val failure = KubernetesPostProcessingFailureException(
            namespace = "yontrack",
            job = "yontrack-av-uuid-1",
            step = "run",
            exitCode = 2,
            timedOut = false,
            logs = "boom",
        )
        every { jobRunner.run(any(), any()) } throws failure
        val ex = assertThrows<KubernetesPostProcessingFailureException> {
            run(config())
        }
        assertSame(failure, ex)
        assertIs<PostProcessingFailureException>(ex)
        assertIs<BaseException>(ex)
        assertEquals("", ex.link)
    }

    @Test
    fun `A configuration error of the runner is propagated unchanged`() {
        val error = KubernetesPostProcessingConfigException("No namespace")
        every { jobRunner.run(any(), any()) } throws error
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            run(config())
        }
        assertSame(error, ex)
    }

    // Utilities

    private fun assertNothingRun() {
        verify(exactly = 0) { jobBuilder.build(any()) }
        verify(exactly = 0) { jobRunner.run(any(), any()) }
    }

    private fun config(
        dockerImage: String = "registry.example.com/tools/gradle:8",
        dockerCommand: String = "./gradlew build",
        commitMessage: String? = "Post processing",
        env: List<KubernetesPostProcessingEnv> = emptyList(),
        secrets: List<KubernetesPostProcessingSecret> = emptyList(),
        timeoutSeconds: Int? = null,
    ) = KubernetesPostProcessingConfig(
        dockerImage = dockerImage,
        dockerCommand = dockerCommand,
        commitMessage = commitMessage,
        env = env,
        secrets = secrets,
        timeoutSeconds = timeoutSeconds,
    )

    /**
     * Runs the post-processing and returns the request passed to the builder.
     */
    private fun run(
        config: KubernetesPostProcessingConfig,
        onPostProcessingInfo: (PostProcessingInfo) -> Unit = {},
    ): KubernetesJobRequest {
        val request = slot<KubernetesJobRequest>()
        every { jobBuilder.build(capture(request)) } returns manifests
        processing.postProcessing(
            config = config,
            autoVersioningOrder = order,
            repositoryURI = "https://github.com/org/repo.git",
            repository = "org/repo",
            upgradeBranch = "feature/auto-upgrade-x-2.0.0",
            scm = scm,
            avTemplateRenderer = avTemplateRenderer,
            onPostProcessingInfo = onPostProcessingInfo,
        )
        verify { jobRunner.run(manifests, any()) }
        return request.captured
    }

}
