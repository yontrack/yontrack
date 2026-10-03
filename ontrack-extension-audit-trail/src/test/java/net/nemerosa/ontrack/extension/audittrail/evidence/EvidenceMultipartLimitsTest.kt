package net.nemerosa.ontrack.extension.audittrail.evidence

import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import kotlin.test.assertEquals

/**
 * The multipart limits of the instance, raised so that an evidence up to its maximum size gets
 * through, and never lowered.
 */
class EvidenceMultipartLimitsTest {

    private fun mb(n: Long) = DataSize.ofMegabytes(n)

    @Test
    fun `Spring Boot defaults raised to the evidence maximum size`() {
        assertEquals(
            EvidenceMultipartLimits(maxFileSize = mb(50), maxRequestSize = mb(51)),
            EvidenceMultipartLimits(maxFileSize = mb(1), maxRequestSize = mb(10)).raisedFor(mb(50)),
        )
    }

    @Test
    fun `Higher limits are kept`() {
        assertEquals(
            EvidenceMultipartLimits(maxFileSize = mb(200), maxRequestSize = mb(300)),
            EvidenceMultipartLimits(maxFileSize = mb(200), maxRequestSize = mb(300)).raisedFor(mb(50)),
        )
    }

    @Test
    fun `Unlimited sizes are kept`() {
        assertEquals(
            EvidenceMultipartLimits(maxFileSize = DataSize.ofBytes(-1), maxRequestSize = DataSize.ofBytes(-1)),
            EvidenceMultipartLimits(maxFileSize = DataSize.ofBytes(-1), maxRequestSize = DataSize.ofBytes(-1)).raisedFor(mb(50)),
        )
    }

    @Test
    fun `Request size raised alone to hold the evidence and its fields`() {
        assertEquals(
            EvidenceMultipartLimits(maxFileSize = mb(100), maxRequestSize = mb(51)),
            EvidenceMultipartLimits(maxFileSize = mb(100), maxRequestSize = mb(10)).raisedFor(mb(50)),
        )
    }
}
