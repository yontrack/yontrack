package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindingsValidationRunMutationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    private val thresholds = CHMLValidationDataTypeConfig(
        warningLevel = CHMLLevel(CHML.HIGH, 1),
        failedLevel = CHMLLevel(CHML.CRITICAL, 1),
    )

    @Test
    fun `A neutral report creates the run with its counts and status, and the findings with their observations`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val node = validate(
                            vs = vs,
                            report = """
                                {
                                  "scanner": "trivy",
                                  "kind": "IMAGE",
                                  "findings": [
                                    {
                                      "externalId": "CVE-2021-44228",
                                      "location": "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1?type=jar",
                                      "severity": "CRITICAL",
                                      "rawSeverity": "CRITICAL (nvd)",
                                      "title": "Log4Shell",
                                      "url": "https://avd.aquasec.com/nvd/cve-2021-44228",
                                      "fixedVersion": "2.15.0"
                                    },
                                    {
                                      "externalId": "CVE-2023-0001",
                                      "location": "pkg:deb/debian/openssl@3.0.11",
                                      "severity": "HIGH",
                                      "title": "OpenSSL issue",
                                      "acceptance": {
                                        "statement": "Not reachable",
                                        "expiresAt": "2999-12-31",
                                        "source": ".trivyignore.yaml"
                                      }
                                    },
                                    {
                                      "externalId": "CVE-2023-0002",
                                      "location": "pkg:deb/debian/zlib@1.2",
                                      "severity": "UNKNOWN",
                                      "title": "Zlib issue"
                                    },
                                    {
                                      "externalId": "CVE-2023-0003",
                                      "location": "pkg:deb/debian/zlib@1.2",
                                      "severity": "LOW",
                                      "title": "Another zlib issue",
                                      "fixedVersion": null
                                    }
                                  ]
                                }
                            """,
                        )
                        val run = assertNoUserError(node, "validateBuildWithFindings").path("validationRun")
                        assertEquals(vs.name, run.path("validationStamp").path("name").asText())
                        assertEquals(
                            FindingsValidationDataType::class.java.name,
                            run.path("data").path("descriptor").path("id").asText()
                        )
                        assertEquals(
                            mapOf(
                                "levels" to mapOf("CRITICAL" to 1, "HIGH" to 0, "MEDIUM" to 0, "LOW" to 1),
                                "unknown" to 1,
                                "accepted" to 1,
                            ).asJson(),
                            run.path("data").path("data")
                        )
                        assertEquals("FAILED", run.lastStatus())
                        assertEquals("Security scan", run.path("validationRunStatuses").first().path("description").asText())
                        assertEquals(12, run.path("runInfo").path("runTime").asInt())

                        val runId = run.path("id").asInt()
                        val findings = findingRepository.findFindingsByProject(project.id())
                        assertEquals(4, findings.size)

                        val log4j = findings.single { it.externalId == "CVE-2021-44228" }
                        assertEquals("trivy", log4j.scanner)
                        assertEquals(FindingKind.IMAGE, log4j.kind)
                        assertEquals("pkg:maven/org.apache.logging.log4j/log4j-core", log4j.location)
                        assertEquals("Log4Shell", log4j.title)
                        assertEquals("https://avd.aquasec.com/nvd/cve-2021-44228", log4j.url)
                        assertEquals(FindingSeverity.CRITICAL, log4j.maxSeverity)
                        assertEquals(log4j.firstSeen, log4j.lastSeen)
                        assertNull(log4j.resolvedAt)

                        val observations = findingRepository.findObservationsByValidationRun(runId)
                        assertEquals(4, observations.size)
                        val log4jObservation = observations.single { it.findingId == log4j.id }
                        assertEquals(FindingSeverity.CRITICAL, log4jObservation.severity)
                        assertEquals("CRITICAL (nvd)", log4jObservation.rawSeverity)
                        assertEquals("2.14.1", log4jObservation.installedVersion)
                        assertEquals("2.15.0", log4jObservation.fixedVersion)
                        assertNull(log4jObservation.acceptance)
                        assertEquals(log4j.firstSeen, log4jObservation.time)

                        val openssl = findings.single { it.externalId == "CVE-2023-0001" }
                        assertEquals(
                            FindingAcceptance(
                                statement = "Not reachable",
                                expiresAt = LocalDate.of(2999, 12, 31),
                                source = ".trivyignore.yaml",
                            ),
                            observations.single { it.findingId == openssl.id }.acceptance
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A second scan updates the findings and adds observations`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        assertNoUserError(
                            validate(vs, report(entry("CVE-1", "pkg:maven/org.x/y@1.0", "MEDIUM", title = "Old title"))),
                            "validateBuildWithFindings"
                        )
                    }
                    build {
                        val run = assertNoUserError(
                            validate(
                                vs, report(
                                    entry("CVE-1", "pkg:maven/org.x/y@2.0", "HIGH", title = "New title"),
                                    entry("CVE-2", "pkg:maven/org.x/y@2.0", "LOW"),
                                )
                            ),
                            "validateBuildWithFindings"
                        ).path("validationRun")
                        assertEquals("WARNING", run.lastStatus())

                        val findings = findingRepository.findFindingsByProject(project.id())
                        assertEquals(listOf("CVE-1", "CVE-2"), findings.map { it.externalId })
                        val cve1 = findings.first()
                        assertEquals("pkg:maven/org.x/y", cve1.location)
                        assertEquals("New title", cve1.title)
                        assertEquals(FindingSeverity.HIGH, cve1.maxSeverity)
                        assertTrue(cve1.lastSeen >= cve1.firstSeen)

                        val observations = findingRepository.findObservationsByFinding(cve1.id)
                        assertEquals(listOf("2.0", "1.0"), observations.map { it.installedVersion })
                    }
                    build {
                        // A lower severity does not lower the maximum
                        assertNoUserError(
                            validate(vs, report(entry("CVE-1", "pkg:maven/org.x/y@3.0", "LOW"))),
                            "validateBuildWithFindings"
                        )
                        val cve1 = findingRepository.findFindingsByProject(project.id()).first()
                        assertEquals(FindingSeverity.HIGH, cve1.maxSeverity)
                    }
                }
            }
        }
    }

    @Test
    fun `Duplicates in a report are one finding and one observation`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val run = assertNoUserError(
                            validate(
                                vs, report(
                                    entry("CVE-1", "pkg:maven/org.x/y@1.0", "MEDIUM"),
                                    entry("CVE-1", "pkg:maven/org.x/y@2.0", "CRITICAL"),
                                )
                            ),
                            "validateBuildWithFindings"
                        ).path("validationRun")
                        assertEquals(1, run.path("data").path("data").path("levels").path("CRITICAL").asInt())
                        assertEquals(0, run.path("data").path("data").path("levels").path("MEDIUM").asInt())
                        val finding = findingRepository.findFindingsByProject(project.id()).single()
                        val observation = findingRepository.findObservationsByFinding(finding.id).single()
                        assertEquals("2.0", observation.installedVersion)
                    }
                }
            }
        }
    }

    @Test
    fun `The scanner and the kind can be given as arguments`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        assertNoUserError(
                            validate(
                                vs = vs,
                                report = """{"scanner": "zap", "findings": [${entry("10038", "", "MEDIUM")}]}""",
                                scanner = "zap-active",
                                kind = "DAST",
                            ),
                            "validateBuildWithFindings"
                        )
                        val finding = findingRepository.findFindingsByProject(project.id()).single()
                        assertEquals("zap-active", finding.scanner)
                        assertEquals(FindingKind.DAST, finding.kind)
                        assertEquals("", finding.location)
                    }
                }
            }
        }
    }

    @Test
    fun `A scan without findings passes`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val run = assertNoUserError(validate(vs, report()), "validateBuildWithFindings")
                            .path("validationRun")
                        assertEquals("PASSED", run.lastStatus())
                        assertTrue(findingRepository.findFindingsByProject(project.id()).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `An invalid report creates nothing`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val node = validate(
                            vs,
                            """{"scanner": "gitleaks", "kind": "SECRETS", "findings": [{"externalId": "aws", "location": "1", "severity": "HIGH", "title": "AWS", "secret": "AKIA"}]}"""
                        )
                        assertUserError(
                            node,
                            "validateBuildWithFindings",
                            message = "Invalid findings report (format `findings`): `findings[0].secret`: unknown field"
                        )
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                        assertTrue(findingRepository.findFindingsByProject(project.id()).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `An unsupported format creates nothing`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val node = validate(vs, report(entry("CVE-1", "", "HIGH")), format = "cyclonedx")
                        assertUserError(
                            node,
                            "validateBuildWithFindings",
                            message = "Findings report format `cyclonedx` is not supported. Supported formats: findings."
                        )
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `A value too long for the database is rejected`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        val node = validate(vs, report(entry("CVE-1", "x".repeat(1025), "HIGH")))
                        assertUserError(
                            node,
                            "validateBuildWithFindings",
                            message = "Invalid findings report (format `findings`): `findings[0].location`: must not be longer than 1024 characters"
                        )
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `A stamp of another data type rolls everything back`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(validationDataTypeConfig = chmlValidationDataType.config(thresholds))
                    build {
                        val node = validate(vs, report(entry("CVE-1", "", "HIGH")))
                        assertTrue(node.path("validateBuildWithFindings").path("errors").size() > 0)
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                        assertTrue(findingRepository.findFindingsByProject(project.id()).isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun `Posting findings needs the permission to create validation runs`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    build {
                        project.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                            validate(vs, report(entry("CVE-1", "", "HIGH"))) { query, variables ->
                                runWithError(
                                    query,
                                    variables,
                                    errorMessage = "Project function 'ValidationRunCreate' is not granted"
                                )
                            }
                        }
                        assertTrue(structureService.getValidationRunsForBuild(id, 0, 10).isEmpty())
                        assertTrue(findingRepository.findFindingsByProject(project.id()).isEmpty())
                    }
                }
            }
        }
    }

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(validationDataTypeConfig = findingsValidationDataType.config(thresholds))

    private fun Build.validate(
        vs: ValidationStamp,
        report: String,
        format: String = "findings",
        scanner: String? = null,
        kind: String? = null,
        onError: ((query: String, variables: Map<String, Any?>) -> Unit)? = null,
    ): JsonNode {
        val query = """
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
                        description: "Security scan",
                        runInfo: {runTime: 12},
                        format: ${'$'}format,
                        scanner: ${'$'}scanner,
                        kind: ${'$'}kind,
                        report: ${'$'}report,
                    }) {
                        validationRun {
                            id
                            runInfo {
                                runTime
                            }
                            validationStamp {
                                name
                            }
                            data {
                                descriptor {
                                    id
                                }
                                data
                            }
                            validationRunStatuses {
                                statusID {
                                    id
                                }
                                description
                            }
                        }
                        errors {
                            message
                            exception
                        }
                    }
                }
            """
        val variables = mapOf(
                "project" to vs.project.name,
                "branch" to vs.branch.name,
                "build" to name,
                "validation" to vs.name,
                "format" to format,
                "scanner" to scanner,
                "kind" to kind,
                "report" to report.parseAsJson(),
            )
        return if (onError != null) {
            onError(query, variables)
            NullNode.instance
        } else {
            run(query, variables)
        }
    }

    private fun report(vararg entries: String) =
        """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""

    private fun entry(externalId: String, location: String, severity: String, title: String = "Title of $externalId") =
        """{"externalId": "$externalId", "location": "$location", "severity": "$severity", "title": "$title"}"""

    private fun JsonNode.lastStatus(): String =
        path("validationRunStatuses").first().path("statusID").path("id").asText()
}
