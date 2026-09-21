package net.nemerosa.ontrack.extension.github.ingestion.payload

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.settings.GitHubIngestionSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DefaultIngestionHookSignatureServiceTest {

    private lateinit var service: IngestionHookSignatureService
    private lateinit var cachedSettingsService: CachedSettingsService
    private lateinit var ingestionConfigProperties: IngestionConfigProperties

    @BeforeEach
    fun before() {
        ingestionConfigProperties = IngestionConfigProperties()
        cachedSettingsService = mockk()
        service = DefaultIngestionHookSignatureService(cachedSettingsService, ingestionConfigProperties)
    }

    @Test
    fun `Signature OK`() {
        mockToken(IngestionHookFixtures.signatureTestToken)
        assertEquals(
            IngestionHookSignatureCheckResult.OK,
            service.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
            )
        )
    }

    @Test
    fun `Signature mismatch`() {
        mockToken("wrong-token")
        assertEquals(
            IngestionHookSignatureCheckResult.MISMATCH,
            service.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
            )
        )
    }

    @Test
    fun `Signature mismatch but signature check disabled`() {
        ingestionConfigProperties.hook.signature.disabled = true
        mockToken("wrong-token")
        assertEquals(
            IngestionHookSignatureCheckResult.OK,
            service.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
            )
        )
    }

    @Test
    fun `Signature token missing`() {
        mockToken("")
        assertEquals(
            IngestionHookSignatureCheckResult.MISSING_TOKEN,
            service.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
            )
        )
    }

    @Test
    fun `Signature token missing but signature is disabled`() {
        ingestionConfigProperties.hook.signature.disabled = true
        mockToken("")
        assertEquals(
            IngestionHookSignatureCheckResult.OK,
            service.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
            )
        )
    }

    private fun mockToken(token: String) {
        every { cachedSettingsService.getCachedSettings(GitHubIngestionSettings::class.java) } returns GitHubIngestionSettings(
            token = token,
            retentionDays = 30,
            orgProjectPrefix = false,
            indexationInterval = 30,
        )
    }

}