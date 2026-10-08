package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.estates.EstateSecurity
import net.nemerosa.ontrack.extension.scorecard.estates.EstatesTestSupport
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `security.remediationTime` and `security.overdue`, computed by the engine on findings posted by
 * backdated scans.
 */
class SecurityRemediationReadingsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

    private val now = Time.now.withNano(0)

    private val day = 86400.0

    private fun Project.readings(set: ReadingSet = NoEstateReadingSet): Map<String, Reading> =
        readingEngine.computeProject(set, this)!!.associateBy { it.key }

    private fun Project.remediationTime(set: ReadingSet = NoEstateReadingSet): Reading =
        readings(set).getValue(ReadingKeys.SECURITY_REMEDIATION_TIME)

    private fun Project.overdue(set: ReadingSet = NoEstateReadingSet): Reading =
        readings(set).getValue(ReadingKeys.SECURITY_OVERDUE)

    /**
     * Branch with a security stamp, `scan`
     */
    private fun Project.scannedBranch(name: String = "main"): Branch =
        branch(name).apply {
            validationStamp(
                name = "scan",
                validationDataTypeConfig = findingsValidationDataType.config(
                    CHMLValidationDataTypeConfig(
                        warningLevel = CHMLLevel(CHML.HIGH, 1),
                        failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                    )
                ),
            )
        }

    /**
     * A finding of a scan, in the neutral format
     */
    private fun finding(
        cve: String,
        severity: String,
        location: String = "pkg:maven/org.x/y@1.0.0",
        accepted: Boolean = false,
    ): String {
        val acceptance = if (accepted) {
            """, "acceptance": {"statement": "Not reachable", "source": ".trivyignore"}"""
        } else {
            ""
        }
        return """{"externalId": "$cve", "location": "$location", "severity": "$severity", "title": "$cve"$acceptance}"""
    }

    /**
     * Posts a scan of a new build of the branch, some days ago, reporting the given findings.
     */
    private fun Branch.scan(daysAgo: Long, vararg findings: String) {
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = "scan",
                format = "findings",
                report = """{"scanner": "trivy", "kind": "DEPENDENCIES", "findings": [${findings.joinToString(",")}]}""".parseAsJson(),
                dateTime = now.minusDays(daysAgo),
            )
        )
    }

    @Test
    fun `No finding reads NO_SAMPLES for the remediation time and NO_TARGET for the overdue findings`() {
        asAdmin {
            project {
                scannedBranch()
                remediationTime().let { reading ->
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
                    assertEquals(0, reading.details.path("count").asInt())
                    assertEquals(0, reading.details.path("accepted").asInt())
                    assertEquals("ALL_BRANCHES", reading.details.path("scope").path("kind").asText())
                }
                overdue().let { reading ->
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_TARGET, reading.unknownReason)
                    assertEquals(0, reading.details.path("openCritical").asInt())
                }
            }
        }
    }

    @Test
    fun `A version bump which leaves the finding in place is not a remediation`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                val estate = estate(label, security = EstateSecurity(criticalTargetDays = 7, highTargetDays = 14))
                val main = scannedBranch()
                main.scan(30, finding("CVE-1", "HIGH", location = "pkg:maven/org.x/y@1.0.0"))
                // The package is bumped, still vulnerable: the finding stays, at its versionless location
                main.scan(20, finding("CVE-1", "HIGH", location = "pkg:maven/org.x/y@1.1.0"))
                remediationTime().let { reading ->
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
                }
                // Open for 30 days, over the HIGH target of 14 days
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(1.0, reading.value)
                    assertEquals(1, reading.details.path("overdueHigh").asInt())
                    assertEquals(0, reading.details.path("overdueCritical").asInt())
                    assertEquals(14, reading.details.path("highTargetDays").asInt())
                }

                // The vulnerability is fixed
                main.scan(10)
                remediationTime().let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(20 * day, reading.value)
                    assertEquals(1, reading.details.path("count").asInt())
                }
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(0.0, reading.value)
                    assertEquals(0, reading.details.path("openHigh").asInt())
                }
            }
        }
    }

    @Test
    fun `Median remediation time of the CRITICAL and HIGH findings resolved in the window`() {
        asAdmin {
            project {
                scannedBranch().apply {
                    // Resolved before the window (90 days by default)
                    scan(200, finding("CVE-OLD", "CRITICAL"))
                    scan(150)
                    scan(
                        60,
                        finding("CVE-1", "CRITICAL"),
                        finding("CVE-2", "HIGH"),
                        finding("CVE-3", "HIGH"),
                        finding("CVE-MEDIUM", "MEDIUM"),
                    )
                    // CVE-1 fixed after 10 days
                    scan(50, finding("CVE-2", "HIGH"), finding("CVE-3", "HIGH"), finding("CVE-MEDIUM", "MEDIUM"))
                    // CVE-2 fixed after 20 days, CVE-MEDIUM too, not read
                    scan(40, finding("CVE-3", "HIGH"))
                    // CVE-3 fixed after 30 days
                    scan(30)
                }
                remediationTime().let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(20 * day, reading.value)
                    assertEquals(3, reading.details.path("count").asInt())
                    assertEquals(10 * day, reading.details.path("min").asDouble())
                    assertEquals(30 * day, reading.details.path("max").asDouble())
                }
            }
        }
    }

    @Test
    fun `A reopened finding gives one sample per fix, the time it stayed fixed left out`() {
        asAdmin {
            project {
                scannedBranch().apply {
                    scan(60, finding("CVE-1", "HIGH"))
                    // Fixed after 10 days
                    scan(50)
                    // Back 10 days later
                    scan(40, finding("CVE-1", "HIGH"))
                    // Fixed again after 5 days
                    scan(35)
                }
                remediationTime().let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(2, reading.details.path("count").asInt())
                    assertEquals(5 * day, reading.details.path("min").asDouble())
                    assertEquals(10 * day, reading.details.path("max").asDouble())
                    assertEquals(7.5 * day, reading.value)
                }
            }
        }
    }

    @Test
    fun `A fix applied on several branches gives one sample`() {
        asAdmin {
            project {
                val main = scannedBranch("main")
                val release = scannedBranch("release-2.4")
                main.scan(30, finding("CVE-1", "CRITICAL"))
                release.scan(28, finding("CVE-1", "CRITICAL"))
                // Fixed on main, still exposed on the release branch
                main.scan(20)
                release.scan(15)
                remediationTime().let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(1, reading.details.path("count").asInt())
                    assertEquals(15 * day, reading.value)
                }
            }
        }
    }

    @Test
    fun `The age of a reopened finding runs from its reopening`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                val estate = estate(label, security = EstateSecurity(highTargetDays = 14))
                scannedBranch().apply {
                    scan(60, finding("CVE-1", "HIGH"))
                    scan(50)
                    // Reopened 10 days ago: within the HIGH target of 14 days
                    scan(10, finding("CVE-1", "HIGH"))
                }
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(ReadingBasis.MEASURED, reading.basis)
                    assertEquals(0.0, reading.value)
                    assertEquals(1, reading.details.path("openHigh").asInt())
                }
                // Over a HIGH target of 7 days, overdue since its reopening
                val strict = estate(label, security = EstateSecurity(highTargetDays = 7))
                overdue(EstateReadingSet(strict)).let { reading ->
                    assertEquals(1.0, reading.value)
                    assertEquals(1, reading.details.path("overdueHigh").asInt())
                    assertTrue(
                        reading.details.path("overdueSince").asText().startsWith(now.minusDays(10).toLocalDate().toString())
                    )
                }
            }
        }
    }

    @Test
    fun `Accepted findings are neither open nor resolved, counted apart`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                val estate = estate(label, security = EstateSecurity(criticalTargetDays = 1))
                scannedBranch().apply {
                    scan(
                        10,
                        finding("CVE-ACCEPTED", "CRITICAL", accepted = true),
                        finding("CVE-OPEN", "CRITICAL"),
                    )
                }
                remediationTime().let { reading ->
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
                    assertEquals(1, reading.details.path("accepted").asInt())
                }
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(1.0, reading.value)
                    assertEquals(1, reading.details.path("openCritical").asInt())
                    assertEquals(1, reading.details.path("accepted").asInt())
                    // No HIGH target: the HIGH findings are not judged
                    assertTrue(reading.details.path("overdueHigh").isNull)
                }
            }
        }
    }

    @Test
    fun `NO_TARGET with no estate, and with an estate without any remediation target`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                val estate = estate(label, security = EstateSecurity(freshnessDays = 3))
                scannedBranch().scan(30, finding("CVE-1", "CRITICAL"))
                overdue().let { reading ->
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_TARGET, reading.unknownReason)
                    assertNull(reading.value)
                    // What is open is still said
                    assertEquals(1, reading.details.path("openCritical").asInt())
                }
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(ReadingUnknownReason.NO_TARGET, reading.unknownReason)
                }
            }
        }
    }

    @Test
    fun `Only the branches in scope keep a finding open`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                testBranchModelMatcherProvider.projects += name
                val estate = estate(label, security = EstateSecurity(criticalTargetDays = 7))
                // Out of the branch model
                scannedBranch("feature-x").scan(30, finding("CVE-FEATURE", "CRITICAL"))
                scannedBranch("master").scan(30, finding("CVE-MASTER", "CRITICAL"))
                overdue(EstateReadingSet(estate)).let { reading ->
                    assertEquals(1.0, reading.value)
                    assertEquals(1, reading.details.path("openCritical").asInt())
                    assertEquals("BRANCH_MODEL", reading.details.path("scope").path("kind").asText())
                }
            }
        }
    }
}
