package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How long the findings of a project have been exposed, and their order by it, through GraphQL.
 */
class FindingsExposedForGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    private val now = Time.now.withNano(0)

    private val day = 86400L

    @Test
    fun `The longest exposed first, the page cut after the sort, the fixed findings last`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    scan(vs, 30, entry("CVE-OLD", "HIGH"))
                    scan(vs, 20, entry("CVE-OLD", "HIGH"), entry("CVE-MID", "CRITICAL"), entry("CVE-FIXED", "CRITICAL"))
                    scan(vs, 10, entry("CVE-OLD", "HIGH"), entry("CVE-MID", "CRITICAL"), entry("CVE-NEW", "LOW"))
                }

                assertEquals(
                    listOf("CVE-MID", "CVE-FIXED", "CVE-OLD", "CVE-NEW"),
                    findings(this).map { it.path("externalId").asText() },
                )
                val sorted = findings(this, sort = "EXPOSED_FOR")
                assertEquals(
                    listOf("CVE-OLD", "CVE-MID", "CVE-NEW", "CVE-FIXED"),
                    sorted.map { it.path("externalId").asText() },
                )
                assertEquals(
                    listOf("CVE-OLD", "CVE-MID"),
                    findings(this, sort = "EXPOSED_FOR", size = 2).map { it.path("externalId").asText() },
                )

                val old = sorted[0].path("exposedFor")
                assertTrue(old.path("ongoing").asBoolean())
                assertAbout(30 * day, old.path("ongoingSeconds").asLong())
                assertEquals(now.minusDays(30).iso(), old.path("since").asText())
                assertEquals("main", old.path("branch").path("name").asText())
                assertEquals("scan", old.path("validationStamp").path("name").asText())
                assertFalse(old.path("accepted").asBoolean())
                assertFalse(old.path("reopened").asBoolean())
                assertAbout(30 * day, old.path("lastEpisodeSeconds").asLong())

                val fixed = sorted[3].path("exposedFor")
                assertFalse(fixed.path("ongoing").asBoolean())
                assertTrue(fixed.path("ongoingSeconds").isNull)
                assertTrue(fixed.path("branch").isNull)
                assertEquals(10 * day, fixed.path("lastEpisodeSeconds").asLong())
            }
        }
    }

    @Test
    fun `Over the branches which count, or over the filtered branch only, accepted or reopened`() {
        asAdmin {
            project {
                val main = branch("main")
                val release = branch("release-2.4")
                val vsMain = main.findingsStamp()
                val vsRelease = release.findingsStamp()
                main.scan(vsMain, 40, entry("CVE-1", "HIGH"), entry("CVE-2", "HIGH"))
                main.scan(vsMain, 35, entry("CVE-1", "HIGH"))
                release.scan(vsRelease, 10, entry("CVE-1", "HIGH", accepted = true), entry("CVE-2", "HIGH"))
                main.scan(vsMain, 5, entry("CVE-1", "HIGH"), entry("CVE-2", "HIGH"))

                // All branches count: CVE-1 exposed on main for 40 days
                val all = findings(this, sort = "EXPOSED_FOR").associateBy { it.path("externalId").asText() }
                all.getValue("CVE-1").path("exposedFor").let { exposedFor ->
                    assertAbout(40 * day, exposedFor.path("ongoingSeconds").asLong())
                    assertEquals("main", exposedFor.path("branch").path("name").asText())
                    assertFalse(exposedFor.path("accepted").asBoolean())
                }
                // CVE-2 fixed on main at day 35, back at day 5: the oldest ongoing period is the one of the release branch
                all.getValue("CVE-2").path("exposedFor").let { exposedFor ->
                    assertAbout(10 * day, exposedFor.path("ongoingSeconds").asLong())
                    assertEquals("release-2.4", exposedFor.path("branch").path("name").asText())
                    assertFalse(exposedFor.path("reopened").asBoolean())
                }

                // On the release branch only
                val onRelease = findings(this, sort = "EXPOSED_FOR", branch = "release-2.4")
                    .associateBy { it.path("externalId").asText() }
                onRelease.getValue("CVE-1").path("exposedFor").let { exposedFor ->
                    assertAbout(10 * day, exposedFor.path("ongoingSeconds").asLong())
                    assertTrue(exposedFor.path("accepted").asBoolean())
                }

                // On main only: CVE-2 reopened
                val onMain = findings(this, sort = "EXPOSED_FOR", branch = "main")
                    .associateBy { it.path("externalId").asText() }
                onMain.getValue("CVE-2").path("exposedFor").let { exposedFor ->
                    assertAbout(5 * day, exposedFor.path("ongoingSeconds").asLong())
                    assertTrue(exposedFor.path("reopened").asBoolean())
                    assertAbout(5 * day, exposedFor.path("lastEpisodeSeconds").asLong())
                }
            }
        }
    }

    /**
     * A duration measured until now, a few minutes of test run allowed
     */
    private fun assertAbout(expected: Long, actual: Long) {
        assertTrue(actual in expected..(expected + 600), "$actual seconds is about $expected seconds")
    }

    private fun LocalDateTime.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun findings(
        project: Project,
        sort: String? = null,
        branch: String? = null,
        size: Int = 20,
    ): List<JsonNode> {
        val filter = branch?.let { """filter: {branch: "$it"}, """ } ?: ""
        val sortArg = sort?.let { "sort: $it, " } ?: ""
        val branchArg = branch?.let { """(branch: "$it")""" } ?: ""
        return run(
            """
                {
                    project(id: ${project.id}) {
                        findings($filter${sortArg}size: $size) {
                            pageItems {
                                externalId
                                exposedFor$branchArg {
                                    ongoing
                                    ongoingSeconds
                                    since
                                    branch { name }
                                    validationStamp { name }
                                    accepted
                                    reopened
                                    lastEpisodeSeconds
                                }
                            }
                        }
                    }
                }
            """
        ).path("project").path("findings").path("pageItems").toList()
    }

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            name = "scan",
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, daysAgo: Long, vararg entries: String) {
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
                dateTime = now.minusDays(daysAgo),
            )
        )
    }

    private fun entry(externalId: String, severity: String, accepted: Boolean = false): String {
        val acceptance = if (accepted) {
            """, "acceptance": {"statement": "Not reachable", "source": ".trivyignore.yaml"}"""
        } else {
            ""
        }
        return """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "$severity", "title": "Title of $externalId"$acceptance}"""
    }
}
