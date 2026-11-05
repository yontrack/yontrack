package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A disabled branch is retired: wherever the findings are read, it is not taken into account —
 * unless a view asks about this branch by its name.
 */
class FindingsDisabledBranchesIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

    @Test
    fun `The exposures of a finding leave out the disabled branches, and counts only reflects the branch model`() {
        asAdmin {
            project {
                val main = branch("main")
                val old = branch("old")
                main.scan(main.findingsStamp(), entry("CVE-1"))
                old.scan(old.findingsStamp(), entry("CVE-1"))
                structureService.disableBranch(old)

                val exposures = finding(this, "CVE-1").path("exposures").toList()
                assertEquals(
                    listOf("main" to true),
                    exposures.map { it.path("branch").path("name").asText() to it.path("counts").asBoolean() }
                )
            }
        }
    }

    @Test
    fun `A finding exposed only on a disabled branch is not listed, whatever the state asked for`() {
        asAdmin {
            project {
                val main = branch("main")
                val old = branch("old")
                main.scan(main.findingsStamp(), entry("CVE-1"))
                old.scan(old.findingsStamp(), entry("CVE-1"), entry("CVE-2"))
                structureService.disableBranch(old)

                assertEquals(listOf("CVE-1"), findingIds(this))
                assertEquals(listOf("CVE-1"), findingIds(this, "{state: OPEN}"))
                assertEquals(emptyList(), findingIds(this, "{state: RESOLVED}"))
            }
        }
    }

    @Test
    fun `A finding resolved on an enabled branch and exposed on a disabled one is still resolved`() {
        asAdmin {
            project {
                val main = branch("main")
                val old = branch("old")
                val vs = main.findingsStamp()
                main.scan(vs, entry("CVE-1"))
                main.scan(vs)
                old.scan(old.findingsStamp(), entry("CVE-1"))
                structureService.disableBranch(old)

                assertEquals(listOf("CVE-1"), findingIds(this, "{state: RESOLVED}"))
                val finding = finding(this, "CVE-1")
                assertEquals("RESOLVED", finding.path("state").asText())
                assertEquals(listOf("main"), finding.branchNames())
            }
        }
    }

    @Test
    fun `The summary of a project counts neither the disabled branches nor the findings exposed only on them`() {
        asAdmin {
            project {
                val main = branch("main")
                val old = branch("old")
                main.scan(main.findingsStamp(), entry("CVE-1"))
                val vsOld = old.findingsStamp()
                old.scan(vsOld, entry("CVE-1"), entry("CVE-2"))
                old.scan(vsOld, entry("CVE-1"), entry("CVE-2"), entry("CVE-3"), scanner = "other")
                structureService.disableBranch(old)

                val summary = summary(this)
                assertEquals(1, summary.path("openCount").asInt())
                assertEquals(0, summary.path("acceptedCount").asInt())
                assertEquals(0, summary.path("resolvedCount").asInt())
                assertEquals(listOf("trivy"), summary.path("scanners").toList().map { it.asText() })
                assertEquals(
                    listOf("main"),
                    summary.path("branches").toList().map { it.path("branch").path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `A view asking about a disabled branch by its name keeps working`() {
        asAdmin {
            project {
                val old = branch("old")
                old.scan(old.findingsStamp(), entry("CVE-1"), entry("CVE-2", severity = "CRITICAL"))
                structureService.disableBranch(old)

                assertEquals(listOf("CVE-2", "CVE-1"), findingIds(this, """{branch: "old"}"""))
                assertEquals(listOf("CVE-2", "CVE-1"), findingIds(this, """{branch: "old", state: OPEN}"""))
                val summary = run(
                    """{ branches(id: ${old.id}) { findingsSummary { openCount } } }"""
                ).path("branches").single().path("findingsSummary")
                assertEquals(2, summary.path("openCount").asInt())
            }
        }
    }

    @Test
    fun `The page of a finding exposed only on a disabled branch opens, with no state and no branches`() {
        asAdmin {
            project {
                val old = branch("old")
                old.scan(
                    old.findingsStamp(),
                    """{"externalId": "CVE-1", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "CVE-1", "acceptance": {"statement": "Not reachable", "source": ".trivyignore"}}""",
                )
                old.scan(old.findingsStamp(), entry("CVE-2"))
                structureService.disableBranch(old)

                listOf("CVE-1", "CVE-2").forEach { externalId ->
                    val finding = finding(this, externalId)
                    assertEquals(externalId, finding.path("externalId").asText())
                    assertTrue(finding.path("state").isNull, "No state")
                    assertEquals(emptyList(), finding.branchNames(), "No branch")
                    assertTrue(finding.path("firstSeenIn").isNull, "Not first seen on a branch")
                    assertTrue(finding.path("resolvedIn").isNull, "Not resolved on a branch")
                    assertTrue(finding.path("acceptance").isNull, "No acceptance")
                    assertEquals(0, finding.path("history").path("pageInfo").path("totalSize").asInt())
                    assertEquals(0, finding.path("observations").path("pageInfo").path("totalSize").asInt())
                }
            }
        }
    }

    @Test
    fun `A finding fixed before its branch was disabled does not read as resolved, until it is enabled again`() {
        asAdmin {
            project {
                val old = branch("old")
                val vs = old.findingsStamp()
                old.scan(vs, entry("CVE-1"))
                old.scan(vs)
                assertTrue(finding(this, "CVE-1").path("resolvedAt").asText().isNotBlank())

                structureService.disableBranch(old)
                assertTrue(finding(this, "CVE-1").path("resolvedAt").isNull)

                structureService.enableBranch(structureService.getBranch(old.id))
                assertTrue(finding(this, "CVE-1").path("resolvedAt").asText().isNotBlank())
            }
        }
    }

    @Test
    fun `A finding reopened while its branch is disabled is open once it is enabled again`() {
        asAdmin {
            project {
                val old = branch("old")
                val vs = old.findingsStamp()
                old.scan(vs, entry("CVE-1"))
                old.scan(vs)
                // A new build would enable the branch: a scan of a build it already has
                val build = old.build()
                structureService.disableBranch(old)
                build.scan(vs, entry("CVE-1"))

                structureService.enableBranch(structureService.getBranch(old.id))

                val finding = finding(this, "CVE-1")
                assertEquals("OPEN", finding.path("state").asText())
                assertTrue(finding.path("resolvedAt").isNull)
            }
        }
    }

    @Test
    fun `The page of a finding also exposed on an enabled branch reads this branch only`() {
        asAdmin {
            project {
                val old = branch("old")
                val main = branch("main")
                old.scan(old.findingsStamp(), entry("CVE-1"))
                main.scan(main.findingsStamp(), entry("CVE-1"))
                structureService.disableBranch(old)

                val finding = finding(this, "CVE-1")
                assertEquals("OPEN", finding.path("state").asText())
                assertEquals(listOf("main"), finding.branchNames())
                assertEquals("main", finding.path("firstSeenIn").path("branch").path("name").asText())
                assertEquals("main", finding.path("exposedFor").path("branch").path("name").asText())
                assertEquals(
                    setOf("main"),
                    finding.path("history").path("pageItems").toList()
                        .map { it.path("branch").path("name").asText() }.toSet()
                )
                assertEquals(1, finding.path("observations").path("pageInfo").path("totalSize").asInt())
            }
        }
    }

    @Test
    fun `The observations of a disabled branch are given when asking for this branch`() {
        asAdmin {
            project {
                val old = branch("old")
                old.scan(old.findingsStamp(), entry("CVE-1"))
                structureService.disableBranch(old)

                val id = findingRepository.findFindingsByProject(this.id()).single().id
                val observations = run(
                    """{ finding(id: $id) { observations(branchId: ${old.id}) { pageInfo { totalSize } } } }"""
                ).path("finding").path("observations")
                assertEquals(1, observations.path("pageInfo").path("totalSize").asInt())
            }
        }
    }

    @Test
    fun `The findings of an external ID leave out the ones exposed only on a disabled branch`() {
        val externalId = uid("CVE-")
        asAdmin {
            val retired = project {
                val old = branch("old")
                old.scan(old.findingsStamp(), entry(externalId))
                structureService.disableBranch(old)
            }
            val exposed = project {
                val main = branch("main")
                main.scan(main.findingsStamp(), entry(externalId))
            }
            val projects = run("""{ findings(externalId: "$externalId") { project { name } } }""")
                .path("findings").toList().map { it.path("project").path("name").asText() }
            assertEquals(listOf(exposed.name), projects)
            assertTrue(retired.name !in projects)
        }
    }

    @Test
    fun `A finding exposed only outside the branch model keeps being listed, resolved`() {
        testBranchModelMatcherProvider.branchPattern = "main|release-.*"
        asAdmin {
            project {
                testBranchModelMatcherProvider.projects += name
                val feature = branch("feature-x")
                feature.scan(feature.findingsStamp(), entry("CVE-1"))

                assertEquals(listOf("CVE-1"), findingIds(this, "{state: RESOLVED}"))
                val finding = finding(this, "CVE-1")
                assertEquals("RESOLVED", finding.path("state").asText())
                assertEquals(
                    listOf("feature-x" to false),
                    finding.path("exposures").toList()
                        .map { it.path("branch").path("name").asText() to it.path("counts").asBoolean() }
                )
            }
        }
    }

    @Test
    fun `A finding without any exposure left keeps being listed, resolved`() {
        asAdmin {
            project {
                val feature = branch("feature-x")
                feature.scan(feature.findingsStamp(), entry("CVE-1"))
                structureService.deleteBranch(feature.id)

                assertEquals(listOf("CVE-1"), findingIds(this, "{state: RESOLVED}"))
            }
        }
    }

    @Test
    fun `Scans on a disabled branch are stored, and enabling it again brings everything back`() {
        asAdmin {
            project {
                val old = branch("old")
                val vs = old.findingsStamp()
                old.scan(vs, entry("CVE-1"))
                // A new build would enable the branch: a scan of a build it already has
                val build = old.build()
                structureService.disableBranch(old)

                // Scan while disabled: one finding fixed, a new one
                build.scan(vs, entry("CVE-2"))
                assertEquals(emptyList(), findingIds(this))
                assertEquals(
                    null,
                    findingRepository.findFindingByKey(this.id(), "trivy", "CVE-2", "pkg:maven/org.x/y")?.resolvedAt,
                    "Nothing was fixed",
                )

                structureService.enableBranch(structureService.getBranch(old.id))

                val finding = finding(this, "CVE-2")
                assertEquals("OPEN", finding.path("state").asText())
                assertEquals(listOf("old"), finding.branchNames())
                assertEquals(1, finding.path("observations").path("pageInfo").path("totalSize").asInt())
                val resolved = finding(this, "CVE-1")
                assertEquals("RESOLVED", resolved.path("state").asText())
                assertTrue(resolved.path("resolvedAt").asText().isNotBlank(), "Fixed while the branch was disabled")
                assertTrue(finding.path("resolvedAt").isNull)
                assertEquals(1, summary(this).path("branches").single().path("openCount").asInt())
            }
        }
    }

    @Test
    fun `A scan of a new build of a disabled branch enables it, and brings its findings back`() {
        asAdmin {
            project {
                val old = branch("old")
                val vs = old.findingsStamp()
                old.scan(vs, entry("CVE-1"))
                structureService.disableBranch(old)
                assertEquals(emptyList(), findingIds(this))

                structureService.getBranch(old.id).scan(vs, entry("CVE-1"))

                assertEquals(false, structureService.getBranch(old.id).isDisabled)
                assertEquals(listOf("CVE-1"), findingIds(this, "{state: OPEN}"))
            }
        }
    }

    private fun JsonNode.branchNames(): List<String> =
        path("exposures").toList().map { it.path("branch").path("name").asText() }

    private fun findingIds(project: Project, filter: String? = null): List<String> {
        val filterArg = filter?.let { "filter: $it," } ?: ""
        return run(
            """{ project(id: ${project.id}) { findings($filterArg size: 20) { pageItems { externalId } } } }"""
        ).path("project").path("findings").path("pageItems").toList().map { it.path("externalId").asText() }
    }

    /**
     * What the page of a finding reads, from its ID: it may not be in any list.
     */
    private fun finding(project: Project, externalId: String): JsonNode {
        val id = findingRepository.findFindingsByProject(project.id()).single { it.externalId == externalId }.id
        return run(
            """
                {
                    finding(id: $id) {
                        externalId
                        state
                        resolvedAt
                        acceptance { statement }
                        exposures { branch { name } counts }
                        firstSeenIn { branch { name } }
                        resolvedIn { branch { name } }
                        exposedFor { branch { name } }
                        history { pageInfo { totalSize } pageItems { branch { name } } }
                        observations { pageInfo { totalSize } }
                    }
                }
            """
        ).path("finding")
    }

    private fun summary(project: Project): JsonNode =
        run(
            """
                {
                    project(id: ${project.id}) {
                        findingsSummary {
                            openCount
                            acceptedCount
                            resolvedCount
                            scanners
                            branches { branch { name } openCount counting }
                        }
                    }
                }
            """
        ).path("project").path("findingsSummary")

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, vararg entries: String, scanner: String = "trivy") {
        build().scan(vs, *entries, scanner = scanner)
    }

    private fun Build.scan(vs: ValidationStamp, vararg entries: String, scanner: String = "trivy") {
        findingsIngestionService.ingest(
            build = this,
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "$scanner", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )
    }

    private fun entry(externalId: String, severity: String = "HIGH"): String =
        """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "$severity", "title": "Title of $externalId"}"""
}
