package net.nemerosa.ontrack.extension.audittrail.evidence

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Number and size of the evidences per project, as gauges.
 */
class EvidenceMetricsIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var evidenceMetrics: EvidenceMetrics

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun gauge(name: String, project: Project): Double? =
        meterRegistry.find(name).tag(AuditTrailMetrics.Tags.PROJECT, project.name).gauge()?.value()

    @Test
    fun `Number and size of the evidences of a project which are not deleted`() {
        validationRun {
            val first = pdf()
            val second = png()
            val third = pdf()
            upload(first)
            upload(second, partType = "image/png")
            val deleted = upload(third)
            asAdmin { evidenceService.delete(deleted.id) }
            evidenceMetrics.refresh()
            assertEquals(2.0, gauge(AuditTrailMetrics.evidenceCount, project))
            assertEquals((first.size + second.size).toDouble(), gauge(AuditTrailMetrics.evidenceSize, project))
        }
    }

    @Test
    fun `A content shared by two evidences counts for each`() {
        validationRun {
            val content = pdf()
            upload(content)
            upload(content, fileName = "again.pdf")
            evidenceMetrics.refresh()
            assertEquals(2.0, gauge(AuditTrailMetrics.evidenceCount, project))
            assertEquals((2 * content.size).toDouble(), gauge(AuditTrailMetrics.evidenceSize, project))
        }
    }

    @Test
    fun `A project without evidence any longer has no gauge`() {
        validationRun {
            upload(pdf())
            evidenceMetrics.refresh()
            assertEquals(1.0, gauge(AuditTrailMetrics.evidenceCount, project))
            asAdmin { structureService.deleteValidationRun(this) }
            evidenceMetrics.refresh()
            assertNull(gauge(AuditTrailMetrics.evidenceCount, project))
            assertNull(gauge(AuditTrailMetrics.evidenceSize, project))
        }
    }
}
