package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionResult
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading the periods of the exposure of a finding, where it was first seen and resolved, and its
 * history, through GraphQL.
 */
class FindingsHistoryGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var buildDisplayNameService: BuildDisplayNameService

    private val today: LocalDate get() = Time.now.toLocalDate()

    @Test
    fun `Periods of an exposure, with their builds, their duration and their accepted stretches`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    val discovered = scan(vs, "3.4.0", entry("CVE-1"))
                    scan(vs, "3.4.1", entry("CVE-1", acceptedUntil = today.plusDays(30)))
                    val fixed = scan(vs, "3.4.2")
                    scan(vs, "3.4.3", entry("CVE-1"))

                    val periods = finding(project, "CVE-1").path("exposures").single().path("periods").toList()
                    assertEquals(2, periods.size)
                    val (first, second) = periods
                    assertEquals(discovered.run.runTime.iso(), first.path("startedAt").asText())
                    assertEquals("3.4.0", first.path("startedInBuild").asText())
                    assertEquals(discovered.run.id(), first.path("startedBy").path("id").asInt())
                    assertEquals(fixed.run.runTime.iso(), first.path("endedAt").asText())
                    assertEquals("3.4.2", first.path("endedInBuild").asText())
                    assertEquals(fixed.run.id(), first.path("endedBy").path("id").asInt())
                    assertEquals("ABSENT", first.path("resolutionReason").asText())
                    assertFalse(first.path("ongoing").asBoolean())
                    assertTrue(first.path("durationSeconds").asLong() >= 0)
                    val span = first.path("acceptedSpans").single()
                    assertEquals("3.4.1", span.path("fromBuild").asText())
                    assertEquals(fixed.run.runTime.iso(), span.path("to").asText())
                    assertEquals("Not reachable", span.path("acceptance").path("statement").asText())

                    assertEquals("3.4.3", second.path("startedInBuild").asText())
                    assertTrue(second.path("ongoing").asBoolean())
                    assertTrue(second.path("endedAt").isNull)
                    assertTrue(second.path("acceptedSpans").isEmpty)
                }
            }
        }
    }

    @Test
    fun `A purged build is named as it was`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    val discovered = scan(vs, "1.0.0", entry("CVE-1"))
                    scan(vs, "1.0.1", entry("CVE-1"))
                    discovered.run.build.delete()
                    val period = finding(project, "CVE-1").path("exposures").single().path("periods").single()
                    assertTrue(period.path("startedBy").isNull)
                    assertEquals("1.0.0", period.path("startedInBuild").asText())
                }
            }
        }
    }

    @Test
    fun `Where a finding was first seen, and where it resolved in its project`() {
        asAdmin {
            project {
                val main = branch("main")
                val release = branch("release-2.4")
                val vsMain = main.findingsStamp()
                val vsRelease = release.findingsStamp()
                main.scan(vsMain, "3.4.0", entry("CVE-1"))
                release.scan(vsRelease, "2.4.7", entry("CVE-1"))

                var finding = finding(this, "CVE-1")
                assertEquals("main", finding.path("firstSeenIn").path("branch").path("name").asText())
                assertEquals("3.4.0", finding.path("firstSeenIn").path("build").asText())
                assertEquals(vsMain.name, finding.path("firstSeenIn").path("validationStamp").path("name").asText())
                assertTrue(finding.path("resolvedIn").isNull)

                main.scan(vsMain, "3.4.2")
                assertTrue(finding(this, "CVE-1").path("resolvedIn").isNull, "Still exposed on the release branch")
                val fixed = release.scan(vsRelease, "2.4.8")

                finding = finding(this, "CVE-1")
                val resolvedIn = finding.path("resolvedIn")
                assertEquals("release-2.4", resolvedIn.path("branch").path("name").asText())
                assertEquals("2.4.8", resolvedIn.path("build").asText())
                assertEquals(fixed.run.id(), resolvedIn.path("validationRun").path("id").asInt())
                assertEquals(fixed.run.runTime.iso(), resolvedIn.path("time").asText())
            }
        }
    }

    @Test
    fun `History of a finding, discovered, fixed and reopened, with its observations grouped`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    scan(vs, "3.4.0", entry("CVE-1"))
                    scan(vs, "3.4.1", entry("CVE-1"))
                    scan(vs, "3.4.2", entry("CVE-1"))
                    scan(vs, "3.4.3")
                    scan(vs, "3.4.4", entry("CVE-1"))

                    val history = history(project, "CVE-1")
                    assertEquals(4, history.path("pageInfo").path("totalSize").asInt())
                    val entries = history.path("pageItems").toList()
                    assertEquals(
                        listOf("REOPENED", "RESOLVED", "OBSERVATIONS", "DISCOVERED"),
                        entries.map { it.path("type").asText() }
                    )
                    val (reopened, resolved, group, discovered) = entries
                    assertEquals("3.4.4", reopened.path("build").asText())
                    assertEquals("3.4.3", resolved.path("build").asText())
                    assertEquals("ABSENT", resolved.path("resolutionReason").asText())
                    assertEquals(2, group.path("count").asInt())
                    assertEquals("3.4.1", group.path("firstBuild").asText())
                    assertEquals("3.4.2", group.path("lastBuild").asText())
                    assertEquals("3.4.0", discovered.path("build").asText())
                    assertEquals("main", discovered.path("branch").path("name").asText())
                    assertEquals(vs.name, discovered.path("validationStamp").path("name").asText())

                    // Paging
                    val page = history(project, "CVE-1", offset = 1, size = 2)
                    assertEquals(4, page.path("pageInfo").path("totalSize").asInt())
                    assertEquals(
                        listOf("RESOLVED", "OBSERVATIONS"),
                        page.path("pageItems").toList().map { it.path("type").asText() }
                    )

                    // Expanding the group
                    val observations = observations(
                        project, "CVE-1",
                        branchId = id(), validationStampId = vs.id(),
                        from = group.path("firstTime").asText(), to = group.path("lastTime").asText(),
                    )
                    assertEquals(2, observations.size)
                }
            }
        }
    }

    @Test
    fun `History of an accepted finding`() {
        asAdmin {
            project {
                branch("release-2.4") {
                    val vs = findingsStamp()
                    scan(vs, "2.4.7", entry("CVE-1"))
                    scan(vs, "2.4.8", entry("CVE-1", acceptedUntil = today.plusDays(30)))
                    val entries = history(project, "CVE-1").path("pageItems").toList()
                    assertEquals(listOf("ACCEPTED", "DISCOVERED"), entries.map { it.path("type").asText() })
                    val accepted = entries.first()
                    assertEquals("2.4.8", accepted.path("build").asText())
                    assertEquals("Not reachable", accepted.path("acceptance").path("statement").asText())
                    assertEquals(today.plusDays(30).toString(), accepted.path("acceptance").path("expiresAt").asText())
                }
            }
        }
    }

    private fun LocalDateTime.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun finding(project: Project, externalId: String): JsonNode {
        val id = findingRepository.findFindingByKey(project.id(), "trivy", externalId, "pkg:maven/org.x/y")!!.id
        return run(
            """
                {
                    finding(id: $id) {
                        firstSeenIn {
                            time
                            branch { name }
                            validationStamp { name }
                            validationRun { id }
                            build
                        }
                        resolvedIn {
                            time
                            branch { name }
                            validationStamp { name }
                            validationRun { id }
                            build
                        }
                        exposures {
                            branch { name }
                            periods {
                                startedAt
                                startedBy { id }
                                startedInBuild
                                endedAt
                                endedBy { id }
                                endedInBuild
                                resolutionReason
                                ongoing
                                durationSeconds
                                acceptedSpans {
                                    from
                                    to
                                    fromBuild
                                    fromValidationRun { id }
                                    acceptance { statement expiresAt }
                                }
                            }
                        }
                    }
                }
            """
        ).path("finding")
    }

    private fun history(project: Project, externalId: String, offset: Int = 0, size: Int = 20): JsonNode {
        val id = findingRepository.findFindingByKey(project.id(), "trivy", externalId, "pkg:maven/org.x/y")!!.id
        return run(
            """
                {
                    finding(id: $id) {
                        history(offset: $offset, size: $size) {
                            pageInfo { totalSize }
                            pageItems {
                                type
                                time
                                branch { name }
                                validationStamp { name }
                                validationRun { id }
                                build
                                resolutionReason
                                acceptance { statement expiresAt }
                                count
                                firstTime
                                lastTime
                                firstBuild
                                lastBuild
                            }
                        }
                    }
                }
            """
        ).path("finding").path("history")
    }

    private fun observations(
        project: Project,
        externalId: String,
        branchId: Int,
        validationStampId: Int,
        from: String,
        to: String,
    ): List<JsonNode> {
        val id = findingRepository.findFindingByKey(project.id(), "trivy", externalId, "pkg:maven/org.x/y")!!.id
        return run(
            """
                {
                    finding(id: $id) {
                        observations(branchId: $branchId, validationStampId: $validationStampId, from: "$from", to: "$to") {
                            pageItems { time }
                        }
                    }
                }
            """
        ).path("finding").path("observations").path("pageItems").toList()
    }

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, label: String, vararg entries: String): FindingsIngestionResult {
        val build = build()
        buildDisplayNameService.setDisplayName(build, label, override = true)
        return findingsIngestionService.ingest(
            build = build,
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )
    }

    private fun entry(externalId: String, acceptedUntil: LocalDate? = null): String {
        val acceptance = acceptedUntil?.let {
            """, "acceptance": {"statement": "Not reachable", "expiresAt": "$it", "source": ".trivyignore.yaml"}"""
        } ?: ""
        return """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "Title of $externalId"$acceptance}"""
    }
}
