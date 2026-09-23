package net.nemerosa.ontrack.extension.findings.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Searching the findings by their external ID.
 */
@TestPropertySource(
    properties = [
        "ontrack.config.search.index.immediate=true"
    ]
)
class FindingSearchIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var findingSearchIndexer: FindingSearchIndexer

    @Autowired
    private lateinit var searchIndexService: SearchIndexService

    @Test
    fun `Searching an external ID lists the projects and the branches the finding is exposed on`() {
        val cve = uid("CVE-")
        val (one, two) = asAdmin {
            val one = project {
                branch("main") {
                    scan(findingsStamp(), entry(cve))
                }
                branch("release-1.0") {
                    val vs = findingsStamp()
                    scan(vs, entry(cve))
                    // Fixed on this branch
                    scan(vs, entry("CVE-OTHER"))
                }
                branch("release-2.0") {
                    scan(findingsStamp(), entry(cve, acceptedUntil = Time.now.toLocalDate().plusDays(10)))
                }
            }
            val two = project {
                branch("main") {
                    scan(findingsStamp(), entry(cve, location = "pkg:maven/org.a/b"))
                }
            }
            one to two
        }
        asAdmin {
            val results = search(cve)
            assertEquals(
                setOf(one.name, two.name),
                results.map { it.path("data").path("project").path("name").asText() }.toSet(),
                "One result per finding, a finding seen several times in a project being listed once"
            )
            assertEquals(2, results.size)

            val resultOne = results.single { it.path("data").path("project").path("name").asText() == one.name }
            val findingOne = findingRepository.findFindingsByProject(one.id()).single { it.externalId == cve }
            assertEquals(cve, resultOne.path("title").asText())
            assertEquals("Title of $cve", resultOne.path("description").asText())
            assertEquals("finding", resultOne.path("type").path("id").asText())
            resultOne.path("data").path("finding").let { finding ->
                assertEquals(findingOne.id, finding.path("id").asInt())
                assertEquals(cve, finding.path("externalId").asText())
                assertEquals("trivy", finding.path("scanner").asText())
                assertEquals("pkg:maven/org.x/y", finding.path("location").asText())
                assertEquals("HIGH", finding.path("maxSeverity").asText())
                assertEquals("OPEN", finding.path("state").asText())
            }
            assertEquals(
                listOf("main" to "EXPOSED", "release-2.0" to "ACCEPTED"),
                resultOne.path("data").path("branches").toList().map {
                    it.path("name").asText() to it.path("state").asText()
                },
                "The branches the finding is exposed on, the branch where it is fixed left out"
            )

            val resultTwo = results.single { it.path("data").path("project").path("name").asText() == two.name }
            assertEquals(
                listOf("main"),
                resultTwo.path("data").path("branches").toList().map { it.path("name").asText() }
            )
            assertEquals("pkg:maven/org.a/b", resultTwo.path("data").path("finding").path("location").asText())
        }
    }

    @Test
    fun `The external ID is matched as a whole, case-insensitively, or by its beginning`() {
        val prefix = uid("CVE-")
        val cve = "$prefix-1"
        asAdmin {
            project {
                branch {
                    scan(findingsStamp(), entry(cve), entry("$prefix-2"), entry("$prefix-12"))
                }
            }
            val exact = search(cve).map { it.path("title").asText() }
            assertEquals(cve, exact.first(), "The exact match comes first")
            assertTrue("$prefix-2" !in exact, "Another external ID is not listed")

            assertEquals(cve, search(cve.lowercase()).first().path("title").asText())

            assertTrue(
                search(prefix).map { it.path("title").asText() }.containsAll(listOf(cve, "$prefix-2", "$prefix-12")),
                "Looking for the beginning of an external ID"
            )
        }
    }

    @Test
    fun `The results are filtered by the permission to see the findings`() {
        val cve = uid("CVE-")
        val (one, two) = asAdmin {
            val one = project {
                branch {
                    scan(findingsStamp(), entry(cve))
                }
            }
            val two = project {
                branch {
                    scan(findingsStamp(), entry(cve))
                }
            }
            one to two
        }
        asAdmin {
            assertEquals(2, search(cve).size)
        }
        // A user who sees both projects, but the findings of one only
        asUser()
            .withView(one).withProjectFunction(one, ProjectFindingsView::class.java)
            .withView(two)
            .call {
                assertEquals(
                    listOf(one.name),
                    search(cve).map { it.path("data").path("project").path("name").asText() }
                )
            }
        // A user who sees one project only
        asUser().withView(two).withProjectFunction(two, ProjectFindingsView::class.java).call {
            assertEquals(
                listOf(two.name),
                search(cve).map { it.path("data").path("project").path("name").asText() }
            )
        }
        // A user who sees no project
        asUser().call {
            assertEquals(0, search(cve).size)
        }
    }

    @Test
    fun `The findings of a deleted project are no longer found`() {
        val cve = uid("CVE-")
        asAdmin {
            val project = project {
                branch {
                    scan(findingsStamp(), entry(cve))
                }
            }
            assertEquals(1, search(cve).size)
            structureService.deleteProject(project.id)
            assertEquals(0, search(cve).size)
        }
    }

    @Test
    fun `Reindexing the findings`() {
        val cve = uid("CVE-")
        asAdmin {
            val project = project {
                branch {
                    scan(findingsStamp(), entry(cve))
                }
            }
            val id = findingRepository.findFindingsByProject(project.id()).single().id
            searchIndexService.deleteSearchIndex(findingSearchIndexer, id.toString())
            assertEquals(0, search(cve).size)

            searchIndexService.index(findingSearchIndexer)
            assertEquals(listOf(cve), search(cve).map { it.path("title").asText() })
        }
    }

    private fun search(token: String): List<JsonNode> =
        run(
            """
                {
                    search(type: "finding", token: "$token", offset: 0, size: 20) {
                        pageItems {
                            title
                            description
                            type {
                                id
                            }
                            data
                        }
                    }
                }
            """
        ).path("search").path("pageItems").toList()

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, vararg entries: String) {
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )
    }

    private fun entry(
        externalId: String,
        location: String = "pkg:maven/org.x/y",
        acceptedUntil: LocalDate? = null,
    ): String {
        val acceptance = acceptedUntil?.let {
            """, "acceptance": {"statement": "Not reachable", "expiresAt": "$it", "source": ".trivyignore.yaml"}"""
        } ?: ""
        return """{"externalId": "$externalId", "location": "$location", "severity": "HIGH", "title": "Title of $externalId"$acceptance}"""
    }
}
