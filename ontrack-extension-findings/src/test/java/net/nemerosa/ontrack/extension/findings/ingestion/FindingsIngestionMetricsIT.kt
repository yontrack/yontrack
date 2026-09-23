package net.nemerosa.ontrack.extension.findings.ingestion

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.findings.metrics.FindingsMetrics
import net.nemerosa.ontrack.extension.findings.report.FindingsReportFormatException
import net.nemerosa.ontrack.extension.findings.report.FindingsReportUnsupportedFormatException
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Metrics of the ingestion of the reports.
 */
@AsAdminTest
class FindingsIngestionMetricsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `An ingested report is timed by format, and its count of findings recorded`() {
        project {
            branch {
                val vs = findingsStamp()
                val timeBefore = timerCount("findings")
                val countBefore = summaryCount("findings")
                val totalBefore = summaryTotal("findings")
                scan(
                    vs,
                    format = "findings",
                    // Three entries, two findings: the same finding twice
                    report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [
                        ${entry("CVE-1")},
                        ${entry("CVE-1")},
                        ${entry("CVE-2")}
                    ]}""",
                )
                assertEquals(timeBefore + 1, timerCount("findings"))
                assertEquals(countBefore + 1, summaryCount("findings"))
                assertEquals(totalBefore + 2.0, summaryTotal("findings"))
            }
        }
    }

    @Test
    fun `A report which cannot be read is not measured`() {
        project {
            branch {
                val vs = findingsStamp()
                val timeBefore = timerCount("findings")
                val countBefore = summaryCount("findings")
                assertThrows<FindingsReportFormatException> {
                    scan(vs, format = "findings", report = """{"findings": "not an array"}""")
                }
                assertEquals(timeBefore, timerCount("findings"))
                assertEquals(countBefore, summaryCount("findings"))
            }
        }
    }

    @Test
    fun `An unsupported format is not measured, and creates no meter for its name`() {
        project {
            branch {
                val vs = findingsStamp()
                assertThrows<FindingsReportUnsupportedFormatException> {
                    scan(vs, format = "unknown-format", report = """{}""")
                }
                assertEquals(null, meterRegistry.find(FindingsMetrics.ingestion).tag(FindingsMetrics.Tags.FORMAT, "unknown-format").timer())
                assertEquals(null, meterRegistry.find(FindingsMetrics.ingestionFindings).tag(FindingsMetrics.Tags.FORMAT, "unknown-format").summary())
            }
        }
    }

    private fun timerCount(format: String): Long =
        meterRegistry.find(FindingsMetrics.ingestion).tag(FindingsMetrics.Tags.FORMAT, format).timer()?.count() ?: 0

    private fun summaryCount(format: String): Long =
        meterRegistry.find(FindingsMetrics.ingestionFindings).tag(FindingsMetrics.Tags.FORMAT, format).summary()?.count() ?: 0

    private fun summaryTotal(format: String): Double =
        meterRegistry.find(FindingsMetrics.ingestionFindings).tag(FindingsMetrics.Tags.FORMAT, format).summary()?.totalAmount() ?: 0.0

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, format: String, report: String): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = format,
                report = report.parseAsJson(),
            )
        )

    private fun entry(externalId: String): String =
        """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "Title of $externalId"}"""
}
