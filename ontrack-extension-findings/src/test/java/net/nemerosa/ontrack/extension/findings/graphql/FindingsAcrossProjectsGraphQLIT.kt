package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A finding across all the projects: the root `searchedFindings(text, size)`, and the root
 * `findings(externalId)` with its states and its exposures, which the fan-out reads.
 */
class FindingsAcrossProjectsGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var extensionManager: ExtensionManager

    @Test
    fun `The findings whose external ID contains the text, ignoring case, open or not, in any project`() {
        val prefix = uid("CVE-")
        val open = "$prefix-OPEN"
        val accepted = "$prefix-ACCEPTED"
        val resolved = "$prefix-RESOLVED"
        asAdmin {
            project {
                scannedBranch().scanEntries(entry(open), entry(accepted, accepted = true))
            }
            project {
                scannedBranch().scanEntries(entry(open))
            }
            project {
                scannedBranch().apply {
                    scanEntries(entry(resolved), entry(uid("GHSA-")))
                    // Reporting nothing any longer
                    scanEntries()
                }
            }

            val expected = listOf("$open: 2/0/0", "$accepted: 0/1/0", "$resolved: 0/0/1")
            // A part of the external ID, in another case, padded
            assertEquals(expected, searchedFindings("  ${prefix.lowercase()}  ").counts())
            assertEquals(expected, searchedFindings(prefix.substring(4).lowercase()).counts())
            // The whole external ID
            assertEquals(listOf("$resolved: 0/0/1"), searchedFindings(resolved).counts())
            // Nothing
            assertEquals(emptyList(), searchedFindings(uid("CVE-")).counts())
            assertEquals(emptyList(), searchedFindings("   ").counts())
        }
    }

    @Test
    fun `The wildcards of a search match themselves`() {
        val prefix = uid("CVE-")
        asAdmin {
            project {
                scannedBranch().scanEntries(entry("$prefix-1"))
            }
            assertEquals(emptyList(), searchedFindings("$prefix%").counts())
            assertEquals(emptyList(), searchedFindings("${prefix}_1").counts())
        }
    }

    @Test
    fun `The findings found are bounded by the size asked for`() {
        val prefix = uid("CVE-")
        asAdmin {
            project {
                scannedBranch().scanEntries(*(1..5).map { entry("$prefix-$it") }.toTypedArray())
            }
            assertEquals(
                listOf("$prefix-1", "$prefix-2"),
                searchedFindings(prefix, size = 2).map { it.path("externalId").asString() },
            )
            assertEquals(5, searchedFindings(prefix).size)
        }
    }

    @Test
    fun `The projects whose findings the user cannot see are left out silently`() {
        val externalId = uid("CVE-")
        lateinit var visible: Project
        lateinit var viewOnly: Project
        lateinit var hidden: Project
        asAdmin {
            visible = exposedProject(externalId)
            viewOnly = exposedProject(externalId)
            hidden = exposedProject(externalId)
        }
        asUser()
            .withView(visible)
            .withProjectFunction(visible, ProjectFindingsView::class.java)
            .withView(viewOnly)
            .execute {
                assertEquals(listOf("$externalId: 1/0/0"), searchedFindings(externalId).counts())
                assertEquals(
                    listOf(visible.name),
                    findings(externalId).map { it.path("project").path("name").asString() },
                )
            }
        // Only reported in a project whose findings the user cannot see: no trace of it
        asUser().withView(viewOnly).execute {
            assertEquals(emptyList(), searchedFindings(externalId).counts())
            assertEquals(emptyList(), findings(externalId))
        }
        asUser().execute {
            assertEquals(emptyList(), searchedFindings(externalId).counts())
            assertEquals(emptyList(), findings(externalId))
        }
    }

    @Test
    fun `A finding exposed only on a disabled branch is neither found nor listed`() {
        val retired = uid("CVE-")
        val mixed = uid("CVE-")
        asAdmin {
            project {
                val old = scannedBranch("old")
                old.scanEntries(entry(retired), entry(mixed))
                scannedBranch("main").scanEntries(entry(mixed))
                structureService.disableBranch(old)
            }
            assertEquals(emptyList(), searchedFindings(retired).counts())
            assertEquals(emptyList(), findings(retired))
            assertEquals(listOf("$mixed: 1/0/0"), searchedFindings(mixed).counts())
            assertEquals(
                listOf("main"),
                findings(mixed).single().path("exposures").values().map { it.path("branch").path("name").asString() },
            )
        }
    }

    @Test
    fun `A finding across projects gives its state and its exposures, resolved or not, for each project`() {
        val externalId = uid("CVE-")
        lateinit var exposed: Project
        lateinit var fixed: Project
        asAdmin {
            exposed = project {
                scannedBranch("main").scanEntries(entry(externalId, location = "pkg:maven/org.x/a"), entry(externalId, location = "pkg:maven/org.x/b"))
                scannedBranch("release").scanEntries(entry(externalId, location = "pkg:maven/org.x/a", accepted = true))
            }
            fixed = project {
                scannedBranch("main").apply {
                    scanEntries(entry(externalId))
                    scanEntries()
                }
            }

            val findings = findings(externalId)
            assertEquals(
                listOf(
                    "${exposed.name} pkg:maven/org.x/a OPEN [main:EXPOSED, release:ACCEPTED]",
                    "${exposed.name} pkg:maven/org.x/b OPEN [main:EXPOSED]",
                    "${fixed.name} pkg:maven/org.x/y RESOLVED [main:RESOLVED]",
                ).sorted(),
                findings.map { finding ->
                    val exposures = finding.path("exposures").values().map {
                        "${it.path("branch").path("name").asString()}:${it.path("state").asString()}"
                    }
                    "${finding.path("project").path("name").asString()} ${finding.path("location").asString()} " +
                            "${finding.path("state").asString()} $exposures"
                }.sorted(),
            )
            // The date a resolved branch shows
            val resolved = findings.single { it.path("state").asString() == "RESOLVED" }
            assertTrue(!resolved.path("resolvedAt").isNull)
            assertTrue(!resolved.path("exposures").first().path("resolvedAt").isNull)
            assertTrue(findings.first { it.path("state").asString() == "OPEN" }.path("resolvedAt").isNull)
        }
    }

    @Test
    fun `The fan-out of a finding is in the Information group of the user menu of any authenticated user`() {
        // The core groups belong to ontrack-ui, out of the classpath of this module: the items
        // contributed are read, and the Playwright test goes through the menu itself
        asUser().execute {
            val item = extensionManager.getExtensions(UserMenuItemExtension::class.java)
                .flatMap { it.items }
                .single { it.extension == "extension/findings" && it.id == "fan-out" }
            assertEquals(CoreUserMenuGroups.INFORMATION, item.groupId)
            assertEquals("Finding across projects", item.name)
        }
    }

    private fun searchedFindings(text: String, size: Int? = null): List<JsonNode> =
        run(
            """
                query(${'$'}text: String!, ${'$'}size: Int) {
                    searchedFindings(text: ${'$'}text, size: ${'$'}size) {
                        externalId
                        title
                        severity
                        openProjects
                        acceptedProjects
                        resolvedProjects
                        firstSeen
                    }
                }
            """,
            mapOf("text" to text, "size" to size)
        ).path("searchedFindings").values().toList()

    private fun findings(externalId: String): List<JsonNode> =
        run(
            """
                query(${'$'}externalId: String!) {
                    findings(externalId: ${'$'}externalId) {
                        location
                        state
                        resolvedAt
                        project { name }
                        exposures {
                            branch { name }
                            state
                            counts
                            resolvedAt
                        }
                    }
                }
            """,
            mapOf("externalId" to externalId)
        ).path("findings").values().toList()

    /**
     * Each found finding as `externalId: open/accepted/resolved`
     */
    private fun List<JsonNode>.counts(): List<String> = map {
        "${it.path("externalId").asString()}: ${it.path("openProjects").asInt()}/" +
                "${it.path("acceptedProjects").asInt()}/${it.path("resolvedProjects").asInt()}"
    }

    /**
     * A project whose `main` branch reports the external ID.
     */
    private fun exposedProject(externalId: String): Project =
        project {
            scannedBranch().scanEntries(entry(externalId))
        }

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
     * One entry of a scan report.
     */
    private fun entry(
        externalId: String,
        location: String = "pkg:maven/org.x/y",
        accepted: Boolean = false,
    ): String {
        val acceptance = if (accepted) {
            """, "acceptance": {"statement": "Not reachable", "source": "VEX"}"""
        } else {
            ""
        }
        return """{"externalId": "$externalId", "location": "$location", "severity": "HIGH", "title": "$externalId"$acceptance}"""
    }
}
