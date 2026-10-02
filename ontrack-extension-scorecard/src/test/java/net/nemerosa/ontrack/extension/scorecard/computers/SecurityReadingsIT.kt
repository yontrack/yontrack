package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
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
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `security.maturity`, computed by the engine on scans posted as findings, backdated.
 */
class SecurityReadingsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

    private val now = Time.now.withNano(0)

    private fun Project.maturity(set: ReadingSet = NoEstateReadingSet): Reading =
        readingEngine.computeProject(set, this)!!.single { it.key == ReadingKeys.SECURITY_MATURITY }

    /**
     * Security stamp, failing on a CRITICAL finding
     */
    private fun Branch.scanStamp(name: String = "scan"): ValidationStamp =
        validationStamp(
            name = name,
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            ),
        )

    /**
     * Posts a scan of a new build of the branch, some days ago, reporting a finding of the given
     * severity, or no finding at all.
     */
    private fun Branch.scan(
        stamp: ValidationStamp,
        kind: FindingKind,
        daysAgo: Long,
        severity: String? = null,
    ): ValidationRun {
        val time: LocalDateTime = now.minusDays(daysAgo)
        val findings = severity?.let {
            """{"externalId": "CVE-${stamp.name}-$kind", "location": "pkg:maven/org.x/y", "severity": "$it", "title": "Some finding"}"""
        } ?: ""
        return findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = stamp.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "$kind", "findings": [$findings]}""".parseAsJson(),
                dateTime = time,
            )
        ).run
    }

    @Test
    fun `No scan reads 0, measured`() {
        asAdmin {
            project {
                branch("main") {
                    validationStamp("lint")
                }
                val maturity = maturity()
                assertEquals(ReadingBasis.MEASURED, maturity.basis)
                assertNull(maturity.unknownReason)
                assertEquals(0.0, maturity.value)
                assertEquals(0, maturity.details.path("count").asInt())
                assertEquals("ALL_BRANCHES", maturity.details.path("scope").path("kind").asText())
            }
        }
    }

    @Test
    fun `The maturity climbs rung by rung with the scans of the branches in scope`() {
        asAdmin {
            project {
                testBranchModelMatcherProvider.projects += name
                val feature = branch("feature-x")
                val master = branch("master")
                val featureScan = feature.scanStamp()
                val masterScan = master.scanStamp()

                // A fresh scan out of the branch model: not read
                feature.scan(featureScan, FindingKind.IMAGE, daysAgo = 1)
                assertEquals(0.0, maturity().value)

                // Reported: a scan in the window, older than the freshness of the settings (7 days)
                master.scan(masterScan, FindingKind.IMAGE, daysAgo = 30, severity = "HIGH")
                assertEquals(1.0, maturity().value)

                // Covered: a fresh scan, clean
                master.scan(masterScan, FindingKind.CODE, daysAgo = 2)
                maturity().let { maturity ->
                    assertEquals(2.0, maturity.value)
                    assertEquals(2, maturity.details.path("count").asInt())
                    assertEquals(listOf("CODE").asJson(), maturity.details.path("freshKinds"))
                    assertEquals(7, maturity.details.path("freshnessDays").asInt())
                    assertEquals("BRANCH_MODEL", maturity.details.path("scope").path("kind").asText())
                }

                // Gating: the security stamp is required by a promotion level
                master.promotionLevel("GOLD").let { gold ->
                    setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(
                            validationStamps = emptyList(),
                            include = "sc.*",
                            exclude = "",
                            promotionLevels = emptyList(),
                        )
                    )
                }
                maturity().let { maturity ->
                    assertEquals(3.0, maturity.value)
                    assertEquals(listOf("scan").asJson(), maturity.details.path("requiredStamps"))
                }
            }
        }
    }

    @Test
    fun `A scan which failed its thresholds in the window is gating`() {
        asAdmin {
            project {
                branch("main") {
                    val scan = scanStamp()
                    scan(scan, FindingKind.IMAGE, daysAgo = 3)
                    assertEquals(2.0, project.maturity().value)
                    // Over the failure threshold
                    scan(scan, FindingKind.IMAGE, daysAgo = 2, severity = "CRITICAL")
                    project.maturity().let { maturity ->
                        assertEquals(3.0, maturity.value)
                        assertEquals(1, maturity.details.path("failedScans").asInt())
                    }
                }
            }
        }
    }

    @Test
    fun `An estate expects its kinds of scan, fresher than its freshness`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                branch("main") {
                    val scan = scanStamp()
                    scan(scan, FindingKind.IMAGE, daysAgo = 3)
                    scan(scan, FindingKind.CODE, daysAgo = 10)
                }
                val imageAndCode = estate(
                    label,
                    security = EstateSecurity(
                        expectedKinds = listOf(FindingKind.IMAGE, FindingKind.CODE),
                        freshnessDays = 14,
                    ),
                )
                val imageAndDast = estate(
                    label,
                    security = EstateSecurity(expectedKinds = listOf(FindingKind.IMAGE, FindingKind.DAST)),
                )
                val freshCode = estate(
                    label,
                    security = EstateSecurity(expectedKinds = listOf(FindingKind.CODE), freshnessDays = 5),
                )

                // No estate: some scan fresher than 7 days
                assertEquals(2.0, maturity().value)
                // Both kinds fresher than 14 days
                assertEquals(2.0, maturity(EstateReadingSet(imageAndCode)).value)
                // No DAST scan
                maturity(EstateReadingSet(imageAndDast)).let { maturity ->
                    assertEquals(1.0, maturity.value)
                    assertEquals(listOf("DAST").asJson(), maturity.details.path("missingKinds"))
                    assertEquals(listOf("IMAGE", "DAST").asJson(), maturity.details.path("expectedKinds"))
                    // The freshness of the settings
                    assertEquals(7, maturity.details.path("freshnessDays").asInt())
                }
                // The code scan is older than 5 days
                assertEquals(1.0, maturity(EstateReadingSet(freshCode)).value)
            }
        }
    }

    @Test
    fun `A scan posted before its kind was recorded has the kinds of the findings it observed`() {
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                branch("main") {
                    val scan = scanStamp()
                    val run = scan(scan, FindingKind.DEPENDENCIES, daysAgo = 1, severity = "LOW")
                    // As a run posted before 6.0
                    namedParameterJdbcTemplate.update(
                        """
                            UPDATE VALIDATION_RUN_DATA
                            SET DATA = DATA - CAST('kind' AS TEXT)
                            WHERE VALIDATION_RUN = :id
                        """.trimIndent(),
                        mapOf("id" to run.id())
                    )
                    val data = namedParameterJdbcTemplate.queryForObject(
                        "SELECT DATA FROM VALIDATION_RUN_DATA WHERE VALIDATION_RUN = :id",
                        mapOf("id" to run.id()),
                        String::class.java
                    )!!.parseAsJson()
                    assertTrue(data.path("kind").isMissingNode, "The kind is not recorded on the run")
                }
                val estate = estate(
                    label,
                    security = EstateSecurity(expectedKinds = listOf(FindingKind.DEPENDENCIES)),
                )
                assertEquals(2.0, maturity(EstateReadingSet(estate)).value)
            }
        }
    }
}
