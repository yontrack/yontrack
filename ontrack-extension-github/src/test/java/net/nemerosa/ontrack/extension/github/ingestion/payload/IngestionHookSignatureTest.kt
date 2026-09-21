package net.nemerosa.ontrack.extension.github.ingestion.payload

import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngestionHookSignatureTest {

    @Test
    fun `Checking a signature`() {
        assertEquals(
            IngestionHookSignatureCheckResult.OK,
            IngestionHookSignature.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = IngestionHookFixtures.signatureTestSignature,
                token = IngestionHookFixtures.signatureTestToken,
            )
        )
    }

    @Test
    fun `Checking a signature with wrong signature`() {
        assertEquals(
            IngestionHookSignatureCheckResult.MISMATCH,
            IngestionHookSignature.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = "sha256=totally-wrong",
                token = IngestionHookFixtures.signatureTestToken,
            )
        )
    }

    @Test
    fun `Checking a signature with a fancy signature`() {
        assertEquals(
            IngestionHookSignatureCheckResult.MISMATCH,
            IngestionHookSignature.checkPayloadSignature(
                body = IngestionHookFixtures.signatureTestBody,
                signature = "totally-wrong",
                token = IngestionHookFixtures.signatureTestToken,
            )
        )
    }

}