package net.nemerosa.ontrack.extension.findings.license

import net.nemerosa.ontrack.extension.findings.license.FindingsLicensedFeatureProvider.Companion.FEATURE_NATIVE_FORMATS
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Native scanner formats, with and without their licensed feature.
 */
class FindingsNativeFormatsLicenseIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var testLicenseService: TestLicenseService

    @Autowired
    private lateinit var licenseControlService: LicenseControlService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Test
    fun `The native formats are a licensed feature, enabled by the development licence`() {
        val feature = licenseControlService.getLicensedFeatures(testLicenseService.license)
            .single { it.id == FEATURE_NATIVE_FORMATS }
        assertEquals("Native scanner formats", feature.name)
        assertTrue(feature.enabled)
        assertTrue(licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS))
        testLicenseService.withoutFeature(FEATURE_NATIVE_FORMATS) {
            assertFalse(licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS))
        }
    }

    @Test
    fun `With the feature, a SARIF report is ingested into findings, and no secret is stored`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val run = assertNoUserError(
                            validate(vs, format = "sarif", kind = "SECRETS", report = sample("trivy")),
                            "validateBuildWithFindings"
                        ).path("validationRun")
                        assertEquals(
                            mapOf(
                                "levels" to mapOf("CRITICAL" to 2, "HIGH" to 1, "MEDIUM" to 1, "LOW" to 1),
                                "unknown" to 0,
                                "accepted" to 0,
                            ).asJson(),
                            run.path("data").path("data")
                        )

                        val findings = findingRepository.findFindingsByProject(project.id())
                        assertEquals(5, findings.size)
                        assertTrue(findings.all { it.scanner == "trivy" && it.kind == FindingKind.SECRETS })
                        val openssl = findings.single { it.externalId == "CVE-2023-5363" }
                        assertEquals("library/debian", openssl.location)
                        assertEquals("openssl: Incorrect cipher key and IV length processing", openssl.title)
                        assertEquals("https://avd.aquasec.com/nvd/cve-2023-5363", openssl.url)
                        assertEquals(FindingSeverity.HIGH, openssl.maxSeverity)
                        val observations = findingRepository.findObservationsByValidationRun(run.path("id").asInt())
                        assertEquals(5, observations.size)
                        assertEquals(
                            "security-severity=7.5",
                            observations.single { it.findingId == openssl.id }.rawSeverity
                        )

                        // The secret is nowhere
                        val stored = listOf(findings.asJson(), observations.asJson(), run).joinToString("\n")
                        assertFalse("AKIAIOSFODNN7EXAMPLE" in stored)
                        assertFalse("wJalrXUtnFEMI" in stored)
                    }
                }
            }
        }
    }

    @Test
    fun `Without the feature, a SARIF report is rejected naming the feature, and no run is created`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        testLicenseService.withoutFeature(FEATURE_NATIVE_FORMATS) {
                            val node = validate(vs, format = "sarif", kind = "CODE", report = sample("codeql"))
                            assertUserError(
                                node,
                                "validateBuildWithFindings",
                                message = "Findings report format `sarif` needs the licensed feature \"Native scanner formats\" " +
                                        "(extension.findings.native-formats), which the current licence does not allow. " +
                                        "The neutral format `findings` needs no licence."
                            )
                        }
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                        assertTrue(findingRepository.findFindingsByProject(project.id()).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `Without the feature, stored findings stay readable and keep resolving through neutral posts`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    // Native input while licensed
                    build {
                        assertNoUserError(
                            validate(vs, format = "sarif", kind = "CODE", report = sample("codeql")),
                            "validateBuildWithFindings"
                        )
                    }
                    // The licence lapses
                    testLicenseService.withoutFeature(FEATURE_NATIVE_FORMATS) {
                        val stored = findingRepository.findFindingsByProject(project.id())
                        assertEquals(4, stored.size)
                        assertTrue(stored.all { it.resolvedAt == null })
                        build {
                            // The neutral format needs no licence: the issues fixed in the code are resolved
                            val run = assertNoUserError(
                                validate(
                                    vs,
                                    format = "findings",
                                    kind = "CODE",
                                    scanner = "codeql",
                                    report = """{"findings": []}""",
                                ),
                                "validateBuildWithFindings"
                            ).path("validationRun")
                            assertEquals("PASSED", run.path("validationRunStatuses").first().path("statusID").path("id").asText())
                        }
                        val findings = findingRepository.findFindingsByProject(project.id())
                        assertEquals(4, findings.size)
                        assertNotNull(findings.single { it.externalId == "java/sql-injection" }.resolvedAt)
                    }
                }
            }
        }
    }

    private fun sample(name: String): String = TestUtils.resourceJson("/sarif/$name.sarif").toString()

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Build.validate(
        vs: ValidationStamp,
        format: String,
        kind: String,
        report: String,
        scanner: String? = null,
    ): JsonNode = run(
        """
            mutation Validate(
                ${'$'}project: String!,
                ${'$'}branch: String!,
                ${'$'}build: String!,
                ${'$'}validation: String!,
                ${'$'}format: String!,
                ${'$'}scanner: String,
                ${'$'}kind: FindingKind,
                ${'$'}report: JSON!,
            ) {
                validateBuildWithFindings(input: {
                    project: ${'$'}project,
                    branch: ${'$'}branch,
                    build: ${'$'}build,
                    validation: ${'$'}validation,
                    format: ${'$'}format,
                    scanner: ${'$'}scanner,
                    kind: ${'$'}kind,
                    report: ${'$'}report,
                }) {
                    validationRun {
                        id
                        data {
                            data
                        }
                        validationRunStatuses {
                            statusID {
                                id
                            }
                        }
                    }
                    errors {
                        message
                        exception
                    }
                }
            }
        """,
        mapOf(
            "project" to vs.project.name,
            "branch" to vs.branch.name,
            "build" to name,
            "validation" to vs.name,
            "format" to format,
            "scanner" to scanner,
            "kind" to kind,
            "report" to report.parseAsJson(),
        )
    )
}
