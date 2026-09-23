package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionResult
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading the findings through GraphQL: `Project.findings`, `ValidationRun.findings`, the root
 * `findings(externalId)` and `finding(id)`, and the `Finding` type.
 */
class FindingsGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    private val today: LocalDate get() = Time.now.toLocalDate()

    @Test
    fun `Findings of a project, the most severe first, with their state`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    scan(
                        vs,
                        entry("CVE-LOW", severity = "LOW"),
                        entry("CVE-CRITICAL", severity = "CRITICAL", url = "https://cve/critical"),
                        entry("CVE-ACCEPTED", severity = "HIGH", acceptedUntil = today.plusDays(10)),
                    )
                }
                val findings = projectFindings(this).path("pageItems")
                assertEquals(
                    listOf("CVE-CRITICAL", "CVE-ACCEPTED", "CVE-LOW"),
                    findings.toList().map { it.path("externalId").asText() }
                )
                val critical = findings.first()
                assertEquals("trivy", critical.path("scanner").asText())
                assertEquals("IMAGE", critical.path("kind").asText())
                assertEquals("pkg:maven/org.x/y", critical.path("location").asText())
                assertEquals("Title of CVE-CRITICAL", critical.path("title").asText())
                assertEquals("https://cve/critical", critical.path("url").asText())
                assertEquals("CRITICAL", critical.path("maxSeverity").asText())
                assertEquals("OPEN", critical.path("state").asText())
                assertEquals(name, critical.path("project").path("name").asText())
                assertTrue(critical.path("firstSeen").asText().isNotBlank())
                assertTrue(critical.path("lastSeen").asText().isNotBlank())
                assertTrue(critical.path("resolvedAt").isNull)
                assertEquals("ACCEPTED", findings[1].path("state").asText())
            }
        }
    }

    @Test
    fun `Filtering the findings of a project on the severity, the scanner and the kind`() {
        asAdmin {
            project {
                branch {
                    val image = findingsStamp()
                    val dast = findingsStamp()
                    scan(image, entry("CVE-1", severity = "HIGH"), entry("CVE-2", severity = "LOW"))
                    scan(dast, entry("10038", severity = "HIGH", location = ""), scanner = "zap", kind = "DAST")
                }
                assertEquals(
                    setOf("CVE-1", "10038"),
                    projectFindingIds(this, """{severity: HIGH}""").toSet()
                )
                assertEquals(
                    listOf("10038"),
                    projectFindingIds(this, """{scanner: "zap"}""")
                )
                assertEquals(
                    setOf("CVE-1", "CVE-2"),
                    projectFindingIds(this, """{kind: IMAGE}""").toSet()
                )
                assertEquals(
                    listOf("CVE-1"),
                    projectFindingIds(this, """{kind: IMAGE, severity: HIGH}""")
                )
            }
        }
    }

    @Test
    fun `Filtering the findings of a project on their state`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    scan(
                        vs,
                        entry("CVE-RESOLVED"),
                        entry("CVE-EXPIRED", acceptedUntil = today.plusDays(10)),
                    )
                    scan(
                        vs,
                        entry("CVE-OPEN"),
                        entry("CVE-ACCEPTED", acceptedUntil = today.plusDays(10)),
                        entry("CVE-FOREVER", acceptedUntil = null, acceptedForever = true),
                        entry("CVE-EXPIRED", acceptedUntil = today.minusDays(1)),
                    )
                }
                assertEquals(
                    setOf("CVE-OPEN", "CVE-EXPIRED"),
                    projectFindingIds(this, """{state: OPEN}""").toSet()
                )
                assertEquals(
                    setOf("CVE-ACCEPTED", "CVE-FOREVER"),
                    projectFindingIds(this, """{state: ACCEPTED}""").toSet()
                )
                assertEquals(
                    listOf("CVE-RESOLVED"),
                    projectFindingIds(this, """{state: RESOLVED}""")
                )
                assertEquals(5, projectFindingIds(this).size)
            }
        }
    }

    @Test
    fun `Filtering the findings of a project on a branch, the state being the one on this branch`() {
        asAdmin {
            project {
                val main = branch("main")
                val release = branch("release-x")
                val vsMain = main.findingsStamp()
                val vsRelease = release.findingsStamp()
                main.scan(vsMain, entry("CVE-1"), entry("CVE-2"))
                main.scan(vsMain, entry("CVE-2"))
                release.scan(vsRelease, entry("CVE-1"), entry("CVE-3"))

                assertEquals(setOf("CVE-1", "CVE-2"), projectFindingIds(this, """{branch: "main"}""").toSet())
                assertEquals(setOf("CVE-1", "CVE-3"), projectFindingIds(this, """{branch: "release-x"}""").toSet())
                // Resolved on main, but still open in the project because of the release branch
                assertEquals(listOf("CVE-1"), projectFindingIds(this, """{branch: "main", state: RESOLVED}"""))
                assertEquals(listOf("CVE-2"), projectFindingIds(this, """{branch: "main", state: OPEN}"""))
                assertEquals(emptyList(), projectFindingIds(this, """{state: RESOLVED}"""))
                // Unknown branch
                assertEquals(emptyList(), projectFindingIds(this, """{branch: "unknown"}"""))
            }
        }
    }

    @Test
    fun `Paginating the findings of a project`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    scan(vs, *(1..5).map { entry("CVE-$it") }.toTypedArray())
                }
                val page = projectFindings(this, offset = 2, size = 2)
                assertEquals(5, page.path("pageInfo").path("totalSize").asInt())
                assertEquals(listOf("CVE-3", "CVE-4"), page.path("pageItems").toList().map { it.path("externalId").asText() })
                assertEquals(
                    emptyList(),
                    projectFindings(this, offset = 10, size = 2).path("pageItems").toList()
                )
            }
        }
    }

    @Test
    fun `A finding gives its exposure per branch, its observations and its acceptance`() {
        asAdmin {
            project {
                val main = branch("main")
                val release = branch("release-x")
                val vsMain = main.findingsStamp()
                val vsRelease = release.findingsStamp()
                val first = main.scan(vsMain, entry("CVE-1", severity = "HIGH", installedVersion = "1.0"))
                main.scan(vsMain)
                val onRelease = release.scan(
                    vsRelease,
                    entry("CVE-1", severity = "CRITICAL", installedVersion = "1.1", acceptedUntil = today.plusDays(10))
                )

                val finding = projectFindings(this).path("pageItems").single()
                assertEquals("CRITICAL", finding.path("maxSeverity").asText())
                assertEquals("ACCEPTED", finding.path("state").asText())

                // Exposure per branch
                val exposures = finding.path("exposures")
                assertEquals(listOf("main", "release-x"), exposures.toList().map { it.path("branch").path("name").asText() })
                val (onMain, onReleaseExposure) = exposures.toList()
                assertEquals(vsMain.name, onMain.path("validationStamp").path("name").asText())
                assertEquals(first.run.runTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), onMain.path("since").asText())
                assertEquals("RESOLVED", onMain.path("state").asText())
                assertEquals("ABSENT", onMain.path("resolutionReason").asText())
                assertFalse(onMain.path("resolvedAt").isNull)
                assertEquals(
                    onRelease.run.runTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    onReleaseExposure.path("since").asText()
                )
                assertEquals("ACCEPTED", onReleaseExposure.path("state").asText())
                assertTrue(onReleaseExposure.path("accepted").asBoolean())
                assertEquals(today.plusDays(10).toString(), onReleaseExposure.path("acceptanceExpiresAt").asText())
                assertTrue(onReleaseExposure.path("resolvedAt").isNull)

                // Observations, the most recent first
                val observations = finding.path("observations")
                assertEquals(2, observations.path("pageInfo").path("totalSize").asInt())
                val (latest, earliest) = observations.path("pageItems").toList()
                assertEquals("CRITICAL", latest.path("severity").asText())
                assertEquals("1.1", latest.path("installedVersion").asText())
                assertEquals(onRelease.run.id(), latest.path("validationRun").path("id").asInt())
                assertEquals("release-x", latest.path("validationRun").path("build").path("branch").path("name").asText())
                assertEquals("Not reachable", latest.path("acceptance").path("statement").asText())
                assertEquals("HIGH", earliest.path("severity").asText())
                assertEquals("1.0", earliest.path("installedVersion").asText())
                assertTrue(earliest.path("acceptance").isNull)

                // Acceptance of the latest observation
                val acceptance = finding.path("acceptance")
                assertEquals("Not reachable", acceptance.path("statement").asText())
                assertEquals(today.plusDays(10).toString(), acceptance.path("expiresAt").asText())
                assertEquals(".trivyignore.yaml", acceptance.path("source").asText())
                assertTrue(acceptance.path("effective").asBoolean())
            }
        }
    }

    @Test
    fun `An acceptance past its expiry is no longer effective when it is read`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    scan(vs, entry("CVE-1", acceptedUntil = today.minusDays(1)))
                }
                val finding = projectFindings(this).path("pageItems").single()
                assertEquals("OPEN", finding.path("state").asText())
                assertFalse(finding.path("acceptance").path("effective").asBoolean())
                assertEquals("EXPOSED", finding.path("exposures").single().path("state").asText())
            }
        }
    }

    @Test
    fun `Findings of a validation run, with the severity of their observation by this run`() {
        asAdmin {
            project {
                branch {
                    val vs = findingsStamp()
                    val first = scan(vs, entry("CVE-1", severity = "LOW"), entry("CVE-2", severity = "HIGH"))
                    val second = scan(vs, entry("CVE-1", severity = "CRITICAL"))

                    val firstFindings = runFindings(first)
                    assertEquals(listOf("CVE-2", "CVE-1"), firstFindings.map { it.path("finding").path("externalId").asText() })
                    assertEquals(listOf("HIGH", "LOW"), firstFindings.map { it.path("severity").asText() })
                    assertEquals("CRITICAL", firstFindings[1].path("finding").path("maxSeverity").asText())

                    val secondFindings = runFindings(second)
                    assertEquals(listOf("CVE-1"), secondFindings.map { it.path("finding").path("externalId").asText() })
                    assertEquals(listOf("CRITICAL"), secondFindings.map { it.path("severity").asText() })
                }
            }
        }
    }

    @Test
    fun `A validation run which is not a security scan has no findings`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = validate(vs)
                        val data = run(
                            """{ validationRuns(id: ${run.id}) { findings { severity } } }"""
                        )
                        assertEquals(0, data.path("validationRuns").single().path("findings").size())
                    }
                }
            }
        }
    }

    @Test
    fun `Findings by external ID across the projects`() {
        val (one, two) = asAdmin {
            val one = project {
                branch {
                    scan(findingsStamp(), entry("CVE-2021-44228"), entry("CVE-OTHER"))
                }
            }
            val two = project {
                branch {
                    scan(findingsStamp(), entry("CVE-2021-44228", location = "pkg:maven/org.a/b"))
                }
            }
            one to two
        }
        asAdmin {
            val projects = findingsByExternalId("CVE-2021-44228").map { it.path("project").path("name").asText() }
            assertTrue(projects.containsAll(listOf(one.name, two.name)))
            assertEquals(
                projects.sorted(),
                projects,
                "Sorted by project name"
            )
        }
        // A user who sees one project only
        asUser().withView(one).withProjectFunction(one, ProjectFindingsView::class.java).call {
            assertEquals(
                listOf(one.name),
                findingsByExternalId("CVE-2021-44228").map { it.path("project").path("name").asText() }
            )
        }
    }

    @Test
    fun `One finding by ID`() {
        asAdmin {
            project {
                branch {
                    scan(findingsStamp(), entry("CVE-1"))
                }
                val id = findingRepository.findFindingsByProject(id()).single().id
                val data = run("""{ finding(id: $id) { id externalId project { name } } }""")
                assertEquals(id, data.path("finding").path("id").asInt())
                assertEquals("CVE-1", data.path("finding").path("externalId").asText())
                assertEquals(name, data.path("finding").path("project").path("name").asText())

                assertTrue(run("""{ finding(id: ${Int.MAX_VALUE}) { id } }""").path("finding").isNull)
            }
        }
    }

    @Test
    fun `A read-only user sees the findings of the project`() {
        val (project, run) = asAdmin {
            lateinit var result: FindingsIngestionResult
            val project = project {
                branch {
                    result = scan(findingsStamp(), entry("CVE-READ-ONLY"))
                }
            }
            project to result
        }
        val account = asAdmin { doCreateAccountWithProjectRole(project, Roles.PROJECT_READ_ONLY) }
        asFixedAccount(account) {
            assertEquals(listOf("CVE-READ-ONLY"), projectFindingIds(project))
            assertEquals(1, runFindings(run).size)
            assertEquals(1, findingsByExternalId("CVE-READ-ONLY").size)
        }
    }

    @Test
    fun `A user without the permission to see the findings sees none`() {
        val (project, run) = asAdmin {
            lateinit var result: FindingsIngestionResult
            val project = project {
                branch {
                    result = scan(findingsStamp(), entry("CVE-HIDDEN"))
                }
            }
            project to result
        }
        val id = asAdmin { findingRepository.findFindingsByProject(project.id()).single().id }
        // The project view, but not the findings view
        asUserWithView(project) {
            val findings = projectFindings(project)
            assertEquals(0, findings.path("pageInfo").path("totalSize").asInt())
            assertEquals(0, findings.path("pageItems").size())
            assertEquals(0, runFindings(run).size)
            assertEquals(0, findingsByExternalId("CVE-HIDDEN").size)
            assertTrue(run("""{ finding(id: $id) { id } }""").path("finding").isNull)
        }
        // Not even the project
        asUser().call {
            assertEquals(0, findingsByExternalId("CVE-HIDDEN").size)
            assertTrue(run("""{ finding(id: $id) { id } }""").path("finding").isNull)
        }
    }

    private fun projectFindings(
        project: Project,
        filter: String? = null,
        offset: Int = 0,
        size: Int = 20,
    ): JsonNode {
        val filterArg = filter?.let { "filter: $it, " } ?: ""
        val data = run(
            """
                {
                    project(id: ${project.id}) {
                        findings($filterArg offset: $offset, size: $size) {
                            pageInfo {
                                totalSize
                            }
                            pageItems {
                                $FINDING_FIELDS
                            }
                        }
                    }
                }
            """
        )
        return data.path("project").path("findings")
    }

    private fun projectFindingIds(project: Project, filter: String? = null): List<String> =
        projectFindings(project, filter).path("pageItems").toList().map { it.path("externalId").asText() }

    private fun runFindings(result: FindingsIngestionResult): List<JsonNode> =
        run(
            """
                {
                    validationRuns(id: ${result.run.id}) {
                        findings {
                            severity
                            rawSeverity
                            installedVersion
                            finding {
                                externalId
                                maxSeverity
                            }
                        }
                    }
                }
            """
        ).path("validationRuns").single().path("findings").toList()

    private fun findingsByExternalId(externalId: String): List<JsonNode> =
        run(
            """
                {
                    findings(externalId: "$externalId") {
                        externalId
                        project {
                            name
                        }
                    }
                }
            """
        ).path("findings").toList()

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(
        vs: ValidationStamp,
        vararg entries: String,
        scanner: String = "trivy",
        kind: String = "IMAGE",
    ): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "$scanner", "kind": "$kind", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )

    private fun entry(
        externalId: String,
        severity: String = "HIGH",
        location: String = "pkg:maven/org.x/y",
        url: String? = null,
        installedVersion: String? = null,
        acceptedUntil: LocalDate? = null,
        acceptedForever: Boolean = false,
    ): String {
        val acceptance = when {
            acceptedUntil != null ->
                """, "acceptance": {"statement": "Not reachable", "expiresAt": "$acceptedUntil", "source": ".trivyignore.yaml"}"""

            acceptedForever ->
                """, "acceptance": {"statement": "Not reachable", "source": ".trivyignore.yaml"}"""

            else -> ""
        }
        val urlField = url?.let { """, "url": "$it"""" } ?: ""
        val installedVersionField = installedVersion?.let { """, "installedVersion": "$it"""" } ?: ""
        return """{"externalId": "$externalId", "location": "$location", "severity": "$severity", "title": "Title of $externalId"$urlField$installedVersionField$acceptance}"""
    }

    companion object {
        private const val FINDING_FIELDS = """
            id
            project {
                name
            }
            scanner
            externalId
            location
            kind
            title
            url
            firstSeen
            lastSeen
            resolvedAt
            maxSeverity
            state
            acceptance {
                statement
                expiresAt
                source
                effective
            }
            exposures {
                branch {
                    name
                }
                validationStamp {
                    name
                }
                since
                state
                accepted
                acceptanceExpiresAt
                resolvedAt
                resolutionReason
            }
            observations {
                pageInfo {
                    totalSize
                }
                pageItems {
                    time
                    severity
                    rawSeverity
                    installedVersion
                    fixedVersion
                    validationRun {
                        id
                        build {
                            branch {
                                name
                            }
                        }
                    }
                    acceptance {
                        statement
                    }
                }
            }
        """
    }
}
