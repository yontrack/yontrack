package net.nemerosa.ontrack.extension.findings.graphql

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
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading a period of an exposure against a build filter through GraphQL: a lens, not a state.
 */
class FindingPeriodBoundaryGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Test
    fun `A matching build, and the state, the resolution and the duration left as they are`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    val level = promotionLevel("GOLD")
                    scan(vs, entry("CVE-1"))
                    val fixed = scan(vs)
                    val promoted = build()
                    promoted.promote(level)

                    val without = finding(project, "CVE-1", """{}""", boundary = false)
                    val with = finding(project, "CVE-1", """{withPromotionLevel: "GOLD"}""", boundary = true)

                    val period = with.path("exposures").single().path("periods").single()
                    val boundary = period.path("boundary")
                    assertEquals("MATCHED", boundary.path("status").asText())
                    assertEquals(promoted.id(), boundary.path("build").path("id").asInt())
                    assertEquals(promoted.name, boundary.path("build").path("name").asText())
                    assertFalse(boundary.path("time").isNull)
                    assertTrue(boundary.path("durationSeconds").asLong() >= 0)
                    assertTrue(boundary.path("reason").isNull)

                    // A lens: the raw facts stay on the fixing build
                    assertEquals("RESOLVED", with.path("state").asText())
                    assertEquals("RESOLVED", without.path("state").asText())
                    assertEquals(fixed.run.build.name, with.path("resolvedIn").path("build").asText())
                    assertEquals(
                        without.path("resolvedIn").path("build").asText(),
                        with.path("resolvedIn").path("build").asText(),
                    )
                    val raw = without.path("exposures").single().path("periods").single()
                    assertEquals(raw.path("durationSeconds").asLong(), period.path("durationSeconds").asLong())
                    assertEquals(raw.path("endedAt").asText(), period.path("endedAt").asText())
                }
            }
        }
    }

    @Test
    fun `An open period has a null boundary`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    promotionLevel("GOLD")
                    scan(vs, entry("CVE-1"))

                    val period = finding(project, "CVE-1", """{withPromotionLevel: "GOLD"}""", boundary = true)
                        .path("exposures").single().path("periods").single()
                    assertTrue(period.path("ongoing").asBoolean())
                    assertTrue(period.path("boundary").isNull)
                }
            }
        }
    }

    @Test
    fun `No matching build yet`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    promotionLevel("GOLD")
                    scan(vs, entry("CVE-1"))
                    scan(vs)

                    val boundary = finding(project, "CVE-1", """{withPromotionLevel: "GOLD"}""", boundary = true)
                        .path("exposures").single().path("periods").single().path("boundary")
                    assertEquals("NONE_YET", boundary.path("status").asText())
                    assertTrue(boundary.path("build").isNull)
                    assertTrue(boundary.path("time").isNull)
                    assertTrue(boundary.path("durationSeconds").asLong() >= 0)
                }
            }
        }
    }

    @Test
    fun `Reopened before any matching build`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    promotionLevel("GOLD")
                    scan(vs, entry("CVE-1"))
                    scan(vs)
                    scan(vs, entry("CVE-1"))

                    val periods = finding(project, "CVE-1", """{withPromotionLevel: "GOLD"}""", boundary = true)
                        .path("exposures").single().path("periods").toList()
                    assertEquals("NOT_REACHED", periods.first().path("boundary").path("status").asText())
                    assertTrue(periods.first().path("boundary").path("durationSeconds").isNull)
                    assertTrue(periods.last().path("boundary").isNull)
                }
            }
        }
    }

    @Test
    fun `A filter which cannot apply says why, a since criterion and an invalid expression included`() {
        asAdmin {
            project {
                branch("main") {
                    val vs = findingsStamp()
                    scan(vs, entry("CVE-1"))
                    scan(vs)

                    listOf(
                        """{withPromotionLevel: "GOLD"}""",
                        """{sinceValidationStamp: "${vs.name}"}""",
                        """{withDisplayName: "["}""",
                    ).forEach { filter ->
                        val boundary = finding(project, "CVE-1", filter, boundary = true)
                            .path("exposures").single().path("periods").single().path("boundary")
                        assertEquals("NOT_APPLICABLE", boundary.path("status").asText(), filter)
                        assertFalse(boundary.path("reason").asText().isBlank(), filter)
                        assertTrue(boundary.path("durationSeconds").isNull, filter)
                    }
                }
            }
        }
    }

    /**
     * The finding, with or without the boundary of its periods.
     */
    private fun finding(project: Project, externalId: String, filter: String, boundary: Boolean): JsonNode {
        val id = findingRepository.findFindingByKey(project.id(), "trivy", externalId, "pkg:maven/org.x/y")!!.id
        val boundaryField = if (boundary) {
            """
                boundary(filter: $filter) {
                    status
                    build {
                        id
                        name
                    }
                    time
                    durationSeconds
                    reason
                }
            """
        } else {
            ""
        }
        return run(
            """
                {
                    finding(id: $id) {
                        state
                        resolvedIn {
                            build
                        }
                        exposures {
                            periods {
                                ongoing
                                endedAt
                                durationSeconds
                                $boundaryField
                            }
                        }
                    }
                }
            """
        ).path("finding")
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

    private fun Branch.scan(vs: ValidationStamp, vararg entries: String): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )

    private fun entry(externalId: String): String =
        """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "Title of $externalId"}"""
}
