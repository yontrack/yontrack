package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Estate.findings(externalId)`: the findings cross-project query, restricted to the projects of
 * the estate, for the fan-out of the estate view.
 */
class EstateFindingsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

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
     * Posts a scan of a new build of the branch, reporting the given external IDs, each at the
     * given location.
     */
    private fun Branch.scan(vararg findings: Pair<String, String>) {
        scanEntries(*findings.map { (externalId, location) -> entry(externalId, location) }.toTypedArray())
    }

    /**
     * One entry of a scan report.
     */
    private fun entry(
        externalId: String,
        location: String = "pkg:maven/org.x/y",
        severity: String = "HIGH",
        title: String = externalId,
        accepted: Boolean = false,
    ): String {
        val acceptance = if (accepted) {
            """, "acceptance": {"statement": "Not reachable", "source": "VEX"}"""
        } else {
            ""
        }
        return """{"externalId": "$externalId", "location": "$location", "severity": "$severity", "title": "$title"$acceptance}"""
    }

    /**
     * Posts a scan of a new build of the branch, reporting the given entries.
     */
    private fun Branch.scanEntries(vararg findings: String) {
        val entries = findings.joinToString(",")
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = "scan",
                format = "findings",
                report = """{"scanner": "trivy", "kind": "DEPENDENCIES", "findings": [$entries]}""".parseAsJson(),
            )
        )
    }

    /**
     * A project carrying the label, whose `main` branch reports the external ID at the given
     * locations.
     */
    private fun exposedProject(label: Label?, externalId: String, vararg locations: String): Project =
        project {
            if (label != null) {
                labels = listOf(label)
            }
            scannedBranch().scan(*locations.map { externalId to it }.toTypedArray())
        }

    private fun estateFindings(name: String, externalId: String): JsonNode =
        run(
            """
                query(${'$'}name: String!, ${'$'}externalId: String!) {
                    estate(name: ${'$'}name) {
                        findings(externalId: ${'$'}externalId) {
                            externalId
                            location
                            state
                            project { name }
                            exposures {
                                branch { name }
                                since
                                state
                                counts
                            }
                        }
                    }
                }
            """,
            mapOf("name" to name, "externalId" to externalId)
        ).path("estate").path("findings")

    @Test
    fun `The findings of an external ID among the projects of the estate only, by project name, with their exposure`() {
        val externalId = uid("CVE-")
        asAdmin {
            val label = label()
            val second = exposedProject(label, externalId, "pkg:maven/org.x/y")
            val first = exposedProject(label, externalId, "pkg:maven/org.a/b", "pkg:maven/org.c/d")
            // Not in the estate
            exposedProject(null, externalId, "pkg:maven/org.x/y")
            // In the estate, not reporting the external ID
            project {
                labels = listOf(label)
                scannedBranch().scan(uid("CVE-") to "pkg:maven/org.x/y")
            }
            val estate = estate(label)

            val findings = estateFindings(estate.name, externalId).values().toList()
            assertEquals(
                listOf(first.name, first.name, second.name).sorted(),
                findings.map { it.path("project").path("name").asText() }
            )
            assertTrue(findings.all { it.path("externalId").asText() == externalId })
            assertEquals(
                setOf("pkg:maven/org.a/b", "pkg:maven/org.c/d"),
                findings.filter { it.path("project").path("name").asText() == first.name }
                    .map { it.path("location").asText() }
                    .toSet()
            )
            findings.forEach { finding ->
                assertEquals("OPEN", finding.path("state").asText())
                val exposure = finding.path("exposures").values().single()
                assertEquals("main", exposure.path("branch").path("name").asText())
                assertEquals("EXPOSED", exposure.path("state").asText())
                assertTrue(exposure.path("since").asText().isNotBlank())
            }
        }
    }

    @Test
    fun `No finding for an external ID no project of the estate reports`() {
        asAdmin {
            val label = label()
            exposedProject(label, uid("CVE-"), "pkg:maven/org.x/y")
            val estate = estate(label)
            assertEquals(0, estateFindings(estate.name, uid("CVE-")).size())
        }
    }

    @Test
    fun `The findings of an estate are filtered by the right to see their projects and their findings`() {
        val externalId = uid("CVE-")
        val label = asAdmin { label() }
        val visible = asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        val viewOnly = asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        val estate = estate(label)
        withNoGrantViewToAll {
            asUser()
                .withView(visible)
                .withProjectFunction(visible, ProjectFindingsView::class.java)
                .withView(viewOnly)
                .call {
                    val projects = estateFindings(estate.name, externalId).values()
                        .map { it.path("project").path("name").asText() }
                    assertEquals(listOf(visible.name), projects)
                }
        }
    }

    /**
     * Whether each branch exposing the finding counts toward its state in the project, by branch
     * name.
     */
    private fun countingByBranch(estate: Estate, externalId: String): Map<String, Boolean> =
        estateFindings(estate.name, externalId).values().single()
            .path("exposures").values()
            .associate { it.path("branch").path("name").asText() to it.path("counts").asBoolean() }

    @Test
    fun `A branch outside the branch model is listed as not counting toward the state of the project`() {
        val externalId = uid("CVE-")
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                // Branch model of the test provider: master|release-.*
                testBranchModelMatcherProvider.projects += name
                scannedBranch("master").scan(externalId to "pkg:maven/org.x/y")
                scannedBranch("feature-x").scan(externalId to "pkg:maven/org.x/y")
            }
            val estate = estate(label)
            assertEquals(
                mapOf("feature-x" to false, "master" to true),
                countingByBranch(estate, externalId)
            )
        }
    }

    @Test
    fun `Without a branch model, every branch exposing the finding counts`() {
        val externalId = uid("CVE-")
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                scannedBranch("master").scan(externalId to "pkg:maven/org.x/y")
                scannedBranch("feature-x").scan(externalId to "pkg:maven/org.x/y")
            }
            val estate = estate(label)
            assertEquals(
                mapOf("feature-x" to true, "master" to true),
                countingByBranch(estate, externalId)
            )
        }
    }

    // Ranked findings

    private fun rankedFindings(name: String, size: Int? = null): List<JsonNode> =
        run(
            """
                query(${'$'}name: String!, ${'$'}size: Int) {
                    estate(name: ${'$'}name) {
                        rankedFindings(size: ${'$'}size) {
                            externalId
                            title
                            severity
                            openProjects
                            acceptedProjects
                            resolvedProjects
                            firstSeen
                        }
                    }
                }
            """,
            mapOf("name" to name, "size" to size)
        ).path("estate").path("rankedFindings").values().toList()

    /**
     * `externalId: open/accepted/resolved`, for each ranked finding, in order.
     */
    private fun List<JsonNode>.counts(): List<String> = map {
        "${it.path("externalId").asText()}: " +
                "${it.path("openProjects").asInt()}/${it.path("acceptedProjects").asInt()}/${it.path("resolvedProjects").asInt()}"
    }

    @Test
    fun `The findings open in the estate, by number of projects where they are open, then by severity, then by external ID`() {
        val prefix = uid("CVE-")
        val wide = "$prefix-3-WIDE"
        val critical = "$prefix-2-CRITICAL"
        val highA = "$prefix-1-HIGH"
        val highB = "$prefix-0-HIGH"
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                scannedBranch().scanEntries(
                    entry(wide, severity = "LOW"),
                    entry(critical, severity = "CRITICAL", title = "Remote code execution"),
                    entry(highA),
                    entry(highB),
                )
            }
            project {
                labels = listOf(label)
                scannedBranch().scanEntries(entry(wide, severity = "MEDIUM"))
            }
            val estate = estate(label)
            val ranked = rankedFindings(estate.name)
            assertEquals(
                listOf("$wide: 2/0/0", "$critical: 1/0/0", "$highB: 1/0/0", "$highA: 1/0/0"),
                ranked.counts()
            )
            // The highest severity of the finding among the projects of the estate
            assertEquals("MEDIUM", ranked.first().path("severity").asText())
            assertEquals("Remote code execution", ranked[1].path("title").asText())
            assertTrue(ranked.all { it.path("firstSeen").asText().isNotBlank() })
        }
    }

    @Test
    fun `A ranked finding counts the projects where it is open, accepted and resolved, and leaves out the findings open nowhere`() {
        val prefix = uid("CVE-")
        val ranked = "$prefix-OPEN"
        val acceptedOnly = "$prefix-ACCEPTED"
        val resolvedOnly = "$prefix-RESOLVED"
        asAdmin {
            val label = label()
            // Open, on two locations: one project
            project {
                labels = listOf(label)
                scannedBranch().scanEntries(entry(ranked, location = "a"), entry(ranked, location = "b"))
            }
            // Accepted
            project {
                labels = listOf(label)
                scannedBranch().scanEntries(entry(ranked, accepted = true), entry(acceptedOnly, accepted = true))
            }
            // Resolved
            project {
                labels = listOf(label)
                scannedBranch().apply {
                    scanEntries(entry(ranked), entry(resolvedOnly))
                    // Reporting nothing any longer
                    scanEntries()
                }
            }
            val estate = estate(label)
            assertEquals(listOf("$ranked: 1/1/1"), rankedFindings(estate.name).counts())
        }
    }

    @Test
    fun `The ranked findings are among the projects of the estate only`() {
        val externalId = uid("CVE-")
        asAdmin {
            val label = label()
            exposedProject(label, externalId, "pkg:maven/org.x/y")
            exposedProject(null, externalId, "pkg:maven/org.x/y")
            exposedProject(null, uid("CVE-"), "pkg:maven/org.x/y")
            val estate = estate(label)
            assertEquals(listOf("$externalId: 1/0/0"), rankedFindings(estate.name).counts())
        }
    }

    @Test
    fun `The ranked findings are filtered by the right to see their projects and their findings`() {
        val externalId = uid("CVE-")
        val label = asAdmin { label() }
        val visible = asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        val viewOnly = asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        asAdmin { exposedProject(label, externalId, "pkg:maven/org.x/y") }
        val estate = estate(label)
        withNoGrantViewToAll {
            asUser()
                .withView(visible)
                .withProjectFunction(visible, ProjectFindingsView::class.java)
                .withView(viewOnly)
                .call {
                    assertEquals(listOf("$externalId: 1/0/0"), rankedFindings(estate.name).counts())
                }
        }
    }

    @Test
    fun `A finding exposed on branches outside the branch model only is not open in its project`() {
        val inModel = uid("CVE-")
        val outside = uid("CVE-")
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                // Branch model of the test provider: master|release-.*
                testBranchModelMatcherProvider.projects += name
                scannedBranch("master").scanEntries(entry(inModel))
                scannedBranch("feature-x").scanEntries(entry(inModel), entry(outside))
            }
            val estate = estate(label)
            assertEquals(listOf("$inModel: 1/0/0"), rankedFindings(estate.name).counts())
        }
    }

    @Test
    fun `The ranked findings are bounded by the size asked for`() {
        val prefix = uid("CVE-")
        asAdmin {
            val label = label()
            project {
                labels = listOf(label)
                scannedBranch().scanEntries(*(1..5).map { entry("$prefix-$it") }.toTypedArray())
            }
            val estate = estate(label)
            assertEquals(
                listOf("$prefix-1", "$prefix-2"),
                rankedFindings(estate.name, size = 2).map { it.path("externalId").asText() }
            )
            assertEquals(5, rankedFindings(estate.name).size)
        }
    }
}
