package net.nemerosa.ontrack.extension.github.ingestion

import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.github.ingestion.payload.GitHubIngestionHookSignatureMismatchException
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStorage
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookSignatureCheckResult
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventPreprocessingCheck
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventProcessor
import net.nemerosa.ontrack.extension.github.ingestion.queue.IngestionHookQueue
import net.nemerosa.ontrack.extension.github.ingestion.settings.GitHubIngestionSettings
import net.nemerosa.ontrack.extension.github.ingestion.settings.GitHubIngestionSettingsMissingTokenException
import net.nemerosa.ontrack.it.MockAuthenticationStorageService
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.TokensService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IngestionHookControllerTest {

    private lateinit var tokensService: TokensService

    @BeforeEach
    fun setup() {
        tokensService = mockk(relaxed = true)
        every { tokensService.useTokenForSecurityContext(any(), any()) } returns true
    }

    @Test
    fun `Storage and queuing`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)

        val ingestionEventProcessor = mockIngestionEventProcessor()
        every { ingestionEventProcessor.preProcessingCheck(any()) } returns IngestionEventPreprocessingCheck.TO_BE_PROCESSED
        every { ingestionEventProcessor.getPayloadSource(any()) } returns "test-source"

        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        var storedPayload: IngestionHookPayload? = null
        var queuedPayload: IngestionHookPayload? = null

        every { storage.store(any(), "test-source") } answers {
            storedPayload = this.arg(0)
        }

        every { queue.queue(any()) } answers {
            queuedPayload = this.arg(0)
        }

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "workflow_run")

        controller.hook(
            body = body,
            gitHubDelivery = headers.gitHubDelivery,
            gitHubEvent = headers.gitHubEvent,
            gitHubHookID = headers.gitHubHookID,
            gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
            gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
            signature = "",
            configuration = null,
        )

        assertEquals(body.parseAsJson(), storedPayload?.payload)
        assertEquals(body.parseAsJson(), queuedPayload?.payload)

        // The token of the hook authenticates a webhook
        verify { tokensService.useTokenForSecurityContext(any(), ActorVia.WEBHOOK) }
        // The payload carries the actor across the queue
        assertEquals(authenticationStorageService.getActor(), storedPayload?.actor)
        assertEquals(authenticationStorageService.getActor(), queuedPayload?.actor)
    }

    @Test
    fun `Disabling the ingestion`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>()

        val ingestionEventProcessor = mockIngestionEventProcessor()
        every { ingestionEventProcessor.preProcessingCheck(any()) } returns IngestionEventPreprocessingCheck.TO_BE_PROCESSED

        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(enabled = false),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "workflow_run")

        assertFailsWith<GitHubIngestionHookDisabledException> {
            controller.hook(
                body = body,
                gitHubDelivery = headers.gitHubDelivery,
                gitHubEvent = headers.gitHubEvent,
                gitHubHookID = headers.gitHubHookID,
                gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
                gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
                signature = "",
                configuration = null,
            )
        }
    }

    @Test
    fun `Excluding a repository`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)

        val ingestionEventProcessor = mockIngestionEventProcessor()
        every { ingestionEventProcessor.preProcessingCheck(any()) } returns IngestionEventPreprocessingCheck.TO_BE_PROCESSED

        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(
                    repositoryExcludes = IngestionHookFixtures.sampleRepository,
                ),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        var storedPayload: IngestionHookPayload? = null
        var queuedPayload: IngestionHookPayload? = null

        every { storage.store(any(), any()) } answers {
            storedPayload = this.arg(0)
        }

        every { queue.queue(any()) } answers {
            queuedPayload = this.arg(0)
        }

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "workflow_run")

        controller.hook(
            body = body,
            gitHubDelivery = headers.gitHubDelivery,
            gitHubEvent = headers.gitHubEvent,
            gitHubHookID = headers.gitHubHookID,
            gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
            gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
            signature = "",
            configuration = null,
        )

        assertNull(storedPayload?.payload)
        assertNull(queuedPayload?.payload)
    }

    @Test
    fun `Missing token for the signature`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>()
        val ingestionEventProcessor = mockIngestionEventProcessor()
        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(IngestionHookSignatureCheckResult.MISSING_TOKEN),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "workflow_run")

        assertFailsWith<GitHubIngestionSettingsMissingTokenException> {
            controller.hook(
                body = body,
                gitHubDelivery = headers.gitHubDelivery,
                gitHubEvent = headers.gitHubEvent,
                gitHubHookID = headers.gitHubHookID,
                gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
                gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
                signature = "",
                configuration = null,
            )
        }
    }

    @Test
    fun `Wrong signature`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)
        val ingestionEventProcessor = mockIngestionEventProcessor()
        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(IngestionHookSignatureCheckResult.MISMATCH),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "workflow_run")

        assertFailsWith<GitHubIngestionHookSignatureMismatchException> {
            controller.hook(
                body = body,
                gitHubDelivery = headers.gitHubDelivery,
                gitHubEvent = headers.gitHubEvent,
                gitHubHookID = headers.gitHubHookID,
                gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
                gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
                signature = "",
                configuration = null,
            )
        }
    }

    @Test
    fun `No storage nor queuing for a ping`() {
        val storage = mockk<IngestionHookPayloadStorage>()
        val queue = mockk<IngestionHookQueue>()
        val authenticationStorageService = MockAuthenticationStorageService()
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)
        val ingestionEventProcessor = mockIngestionEventProcessor("ping")
        every { ingestionEventProcessor.preProcessingCheck(any()) } returns IngestionEventPreprocessingCheck.IGNORED

        val controller =
            IngestionHookController(
                queue = queue,
                storage = storage,
                ingestionHookSignatureService = MockIngestionHookSignatureService(),
                meterRegistry = meterRegistry,
                cachedSettingsService = mockSettings(),
                tokensService = tokensService,
                ingestionEventProcessors = listOf(ingestionEventProcessor),
                authenticationStorageService = authenticationStorageService,
                securityService = MockSecurityService(),
            )

        var storedPayload: IngestionHookPayload? = null
        var queuedPayload: IngestionHookPayload? = null

        every { storage.store(any(), any()) } answers {
            storedPayload = this.arg(0)
        }

        every { queue.queue(any()) } answers {
            queuedPayload = this.arg(0)
        }

        val body = IngestionHookFixtures.sampleWorkflowRunJsonPayload().format()
        val headers = IngestionHookFixtures.payloadHeaders(event = "ping")

        controller.hook(
            body = body,
            gitHubDelivery = headers.gitHubDelivery,
            gitHubEvent = headers.gitHubEvent,
            gitHubHookID = headers.gitHubHookID,
            gitHubHookInstallationTargetID = headers.gitHubHookInstallationTargetID,
            gitHubHookInstallationTargetType = headers.gitHubHookInstallationTargetType,
            signature = "",
            configuration = null,
        )

        assertNull(storedPayload?.payload)
        assertNull(queuedPayload?.payload)
    }

    private fun mockIngestionEventProcessor(event: String = "workflow_run"): IngestionEventProcessor {
        val ingestionEventProcessor = mockk<IngestionEventProcessor>()
        every { ingestionEventProcessor.event } returns event
        return ingestionEventProcessor
    }

    private fun mockSettings(
        enabled: Boolean = true,
        repositoryIncludes: String = GitHubIngestionSettings.DEFAULT_REPOSITORY_INCLUDES,
        repositoryExcludes: String = GitHubIngestionSettings.DEFAULT_REPOSITORY_EXCLUDES,
    ): CachedSettingsService {
        val cachedSettingsService = mockk<CachedSettingsService>()
        val settings = GitHubIngestionSettings(
            enabled = enabled,
            token = "token",
            repositoryIncludes = repositoryIncludes,
            repositoryExcludes = repositoryExcludes,
        )
        every { cachedSettingsService.getCachedSettings(GitHubIngestionSettings::class.java) } returns settings
        return cachedSettingsService
    }

}