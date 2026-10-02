package net.nemerosa.ontrack.extension.scorecard.estates

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
        val entries = findings.joinToString(",") { (externalId, location) ->
            """{"externalId": "$externalId", "location": "$location", "severity": "HIGH", "title": "$externalId"}"""
        }
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
}
