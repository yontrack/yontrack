package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Periods of the exposure of the findings, as kept by the ingestion of the scans.
 */
@AsAdminTest
class FindingsExposurePeriodsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var buildDisplayNameService: BuildDisplayNameService

    @Test
    fun `Discovered, fixed, reopened and fixed again, with the runs and the builds of each end`() {
        project {
            branch {
                val vs = findingsStamp()
                val discovered = scan(vs, "3.4.0", entry("CVE-1"))
                scan(vs, "3.4.1", entry("CVE-1"))
                val fixed = scan(vs, "3.4.2")
                scan(vs, "3.4.3")
                val reopened = scan(vs, "3.4.4", entry("CVE-1"))
                val fixedAgain = scan(vs, "3.4.5")

                val periods = findingRepository.findExposurePeriodsByFinding(project.finding("CVE-1").id)
                assertEquals(2, periods.size)
                val (first, second) = periods
                assertEquals(discovered.run.runTime, first.startedAt)
                assertEquals(discovered.run.id(), first.startedByValidationRunId)
                assertEquals("3.4.0", first.startedInBuild)
                assertEquals(fixed.run.runTime, first.endedAt)
                assertEquals(fixed.run.id(), first.endedByValidationRunId)
                assertEquals("3.4.2", first.endedInBuild)
                assertEquals(FindingResolutionReason.ABSENT, first.resolutionReason)

                assertEquals(reopened.run.id(), second.startedByValidationRunId)
                assertEquals("3.4.4", second.startedInBuild)
                assertEquals(fixedAgain.run.id(), second.endedByValidationRunId)
                assertEquals("3.4.5", second.endedInBuild)
                assertEquals(id(), second.branchId)
                assertEquals(vs.id(), second.validationStampId)
            }
        }
    }

    @Test
    fun `Periods of several findings in one batch, by finding, the oldest first`() {
        project {
            val main = branch()
            val release = branch()
            val mainScan = main.findingsStamp()
            val releaseScan = release.findingsStamp()
            main.scan(mainScan, null, entry("CVE-1"), entry("CVE-2"))
            release.scan(releaseScan, null, entry("CVE-1"))
            main.scan(mainScan, null, entry("CVE-2"))
            main.scan(mainScan, null, entry("CVE-1"), entry("CVE-2"))
            // Not asked for
            main.scan(mainScan, null, entry("CVE-3"))

            val cve1 = finding("CVE-1")
            val cve2 = finding("CVE-2")
            val periods = findingRepository.findExposurePeriodsByFindings(listOf(cve1.id, cve2.id))
            assertEquals(setOf(cve1.id, cve2.id), periods.map { it.findingId }.toSet())
            val cve1Periods = periods.filter { it.findingId == cve1.id }
            assertEquals(findingRepository.findExposurePeriodsByFinding(cve1.id), cve1Periods)
            assertEquals(3, cve1Periods.size)
            assertEquals(1, periods.count { it.findingId == cve2.id })
            assertEquals(emptyList(), findingRepository.findExposurePeriodsByFindings(emptyList()))
        }
    }

    @Test
    fun `A finding still reported keeps one open period, accepted or not`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, "1", entry("CVE-1"))
                scan(vs, "2", entry("CVE-1", accepted = true))
                scan(vs, "3", entry("CVE-1"))
                val period = findingRepository.findExposurePeriodsByFinding(project.finding("CVE-1").id).single()
                assertEquals("1", period.startedInBuild)
                assertNull(period.endedAt)
            }
        }
    }

    @Test
    fun `A build without display name is named by its name`() {
        project {
            branch {
                val vs = findingsStamp()
                val result = scan(vs, null, entry("CVE-1"))
                val period = findingRepository.findExposurePeriodsByFinding(project.finding("CVE-1").id).single()
                assertEquals(result.run.build.name, period.startedInBuild)
            }
        }
    }

    @Test
    fun `The scan of one stamp ends only its own periods`() {
        project {
            branch {
                val vs1 = findingsStamp()
                val vs2 = findingsStamp()
                scan(vs1, "1", entry("CVE-1"))
                scan(vs2, "1", entry("CVE-1"))
                scan(vs1, "2")
                val periods = findingRepository.findExposurePeriodsByFinding(project.finding("CVE-1").id)
                    .associateBy { it.validationStampId }
                assertEquals("2", periods.getValue(vs1.id()).endedInBuild)
                assertNull(periods.getValue(vs2.id()).endedAt)
            }
        }
    }

    @Test
    fun `Purging the builds keeps the periods and the names of their builds`() {
        project {
            branch {
                val vs = findingsStamp()
                val discovered = scan(vs, "1.0.0", entry("CVE-1"))
                val fixed = scan(vs, "1.0.1")
                discovered.run.build.delete()
                fixed.run.build.delete()
                val period = findingRepository.findExposurePeriodsByFinding(project.finding("CVE-1").id).single()
                assertNull(period.startedByValidationRunId)
                assertEquals("1.0.0", period.startedInBuild)
                assertNull(period.endedByValidationRunId)
                assertEquals("1.0.1", period.endedInBuild)
                assertEquals(fixed.run.runTime, period.endedAt)
            }
        }
    }

    @Test
    fun `A deleted branch takes its periods with it`() {
        project {
            val feature = branch()
            val vs = feature.findingsStamp()
            feature.scan(vs, "1", entry("CVE-1"))
            val finding = finding("CVE-1")
            feature.delete()
            assertEquals(emptyList(), findingRepository.findExposurePeriodsByFinding(finding.id))
        }
    }

    @Test
    fun `The exposures older than their periods get one period each, without run nor build`() {
        project {
            branch {
                val vs = findingsStamp()
                val finding = findingRepository.insertFinding(
                    Finding(
                        id = 0,
                        projectId = project.id(),
                        scanner = "trivy",
                        externalId = "CVE-OLD",
                        location = "",
                        kind = net.nemerosa.ontrack.extension.findings.model.FindingKind.IMAGE,
                        title = "Old",
                        url = null,
                        firstSeen = t0,
                        lastSeen = t1,
                        resolvedAt = null,
                        maxSeverity = net.nemerosa.ontrack.extension.findings.model.FindingSeverity.HIGH,
                    )
                )
                findingRepository.saveExposures(
                    listOf(FindingExposure(finding.id, id(), vs.id(), since = t0, resolvedAt = t1, resolutionReason = FindingResolutionReason.ABSENT))
                )
                // Running the insertion of the migration again, for this exposure
                NamedParameterJdbcTemplate(dataSource).update(
                    """
                        INSERT INTO FINDING_EXPOSURE_PERIODS (FINDING_ID, BRANCH_ID, VALIDATION_STAMP_ID, STARTED_AT, ENDED_AT, RESOLUTION_REASON)
                        SELECT FINDING_ID, BRANCH_ID, VALIDATION_STAMP_ID, SINCE, RESOLVED_AT, RESOLUTION_REASON
                        FROM FINDING_EXPOSURES
                        WHERE FINDING_ID = :findingId
                    """.trimIndent(),
                    mapOf("findingId" to finding.id)
                )
                val period = findingRepository.findExposurePeriodsByFinding(finding.id).single()
                assertEquals(t0, period.startedAt)
                assertEquals(t1, period.endedAt)
                assertEquals(FindingResolutionReason.ABSENT, period.resolutionReason)
                assertNull(period.startedByValidationRunId)
                assertNull(period.startedInBuild)
                assertNull(period.endedInBuild)
            }
        }
    }

    private val t0 = LocalDateTime.of(2026, 9, 1, 10, 0)
    private val t1 = LocalDateTime.of(2026, 9, 3, 10, 0)

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, label: String?, vararg entries: String): FindingsIngestionResult {
        val build = build()
        if (label != null) {
            buildDisplayNameService.setDisplayName(build, label, override = true)
        }
        return findingsIngestionService.ingest(
            build = build,
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )
    }

    private fun entry(externalId: String, accepted: Boolean = false): String {
        val acceptance = if (accepted) {
            """, "acceptance": {"statement": "Not reachable", "source": ".trivyignore.yaml"}"""
        } else {
            ""
        }
        return """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "Title of $externalId"$acceptance}"""
    }

    private fun Project.finding(externalId: String): Finding =
        findingRepository.findFindingByKey(id(), "trivy", externalId, "pkg:maven/org.x/y")
            ?: error("Finding $externalId not found")
}
