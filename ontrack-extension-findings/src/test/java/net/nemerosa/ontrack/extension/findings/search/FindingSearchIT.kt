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
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchDocumentService
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Searching the findings by their external ID, on Postgres.
 */
class FindingSearchIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    @Autowired
    private lateinit var searchService: SearchService

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
            assertEquals(listOf(cve, "$prefix-12"), exact, "The exact match comes first, then the prefix match")
            assertTrue("$prefix-2" !in exact, "Another external ID is not listed, however similar")

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
    fun `The results are filtered by the roles of the user`() {
        val cve = uid("CVE-")
        val (one, two) = asAdmin {
            project { branch { scan(findingsStamp(), entry(cve)) } } to
                    project { branch { scan(findingsStamp(), entry(cve)) } }
        }
        // Creating the accounts needs the administrator
        asAdmin {
            withNoGrantViewToAll {
                one.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                    assertEquals(listOf(one.name), search(cve).map { it.path("data").path("project").path("name").asText() })
                }
                asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                    assertEquals(
                        setOf(one.name, two.name),
                        search(cve).map { it.path("data").path("project").path("name").asText() }.toSet()
                    )
                }
            }
            // Seeing all the projects is not seeing their findings
            withGrantViewToAll {
                asUser().call {
                    assertEquals(0, search(cve).size)
                }
            }
        }
    }

    @Test
    fun `A later scan updates the branches the finding is exposed on`() {
        val cve = uid("CVE-")
        asAdmin {
            project {
                lateinit var mainStamp: ValidationStamp
                val main = branch("main") {
                    mainStamp = findingsStamp()
                    scan(mainStamp, entry(cve))
                }
                branch("release-1.0") {
                    val vs = findingsStamp()
                    scan(vs, entry(cve))
                    assertEquals(listOf("main", "release-1.0"), branches(cve))
                    // Fixed on this branch
                    scan(vs, entry("CVE-OTHER"))
                }
                assertEquals(listOf("main"), branches(cve))
                // Fixed on main as well
                main.scan(mainStamp, entry("CVE-OTHER"))
                assertEquals(emptyList(), branches(cve))
                assertEquals("RESOLVED", search(cve).single().path("data").path("finding").path("state").asText())
            }
        }
    }

    @Test
    fun `Renaming or deleting a branch rewrites the results of its findings`() {
        val cve = uid("CVE-")
        asAdmin {
            project {
                val main = branch("main") {
                    scan(findingsStamp(), entry(cve))
                }
                val release = branch("release-1.0") {
                    scan(findingsStamp(), entry(cve))
                }
                assertEquals(listOf("main", "release-1.0"), branches(cve))

                structureService.saveBranch(release.copy(name = "release-1.1"))
                assertEquals(listOf("main", "release-1.1"), branches(cve))

                structureService.deleteBranch(main.id)
                assertEquals(listOf("release-1.1"), branches(cve))
            }
        }
    }

    @Test
    fun `Renaming a project rewrites the results of its findings`() {
        val cve = uid("CVE-")
        asAdmin {
            val project = project {
                branch { scan(findingsStamp(), entry(cve)) }
            }
            val name = uid("P")
            structureService.saveProject(
                Project(project.id, name, project.description, project.isDisabled, project.signature)
            )
            assertEquals(
                listOf(name),
                search(cve).map { it.path("data").path("project").path("name").asText() }
            )
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

    /**
     * The rebuild runs in its own transactions: the findings of the test must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the findings`() {
        val cve = uid("CVE-")
        asAdmin {
            val project = project {
                branch("main") {
                    scan(findingsStamp(), entry(cve))
                }
            }
            val id = findingRepository.findFindingsByProject(project.id()).single().id
            searchDocumentService.delete(FINDING_SEARCH_RESULT_TYPE, id.toString())
            assertEquals(0, search(cve).size)

            searchService.reindex(FINDING_SEARCH_RESULT_TYPE)
            val result = search(cve).single()
            assertEquals(cve, result.path("title").asText())
            assertEquals(listOf("main"), result.path("data").path("branches").toList().map { it.path("name").asText() })
        }
    }

    private fun search(query: String): List<JsonNode> =
        run(
            """
                {
                    search(query: "$query", types: ["$FINDING_SEARCH_RESULT_TYPE"], offset: 0, size: 20) {
                        items {
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
        ).path("search").path("items").toList()

    /**
     * Names of the branches the single result of a query is exposed on
     */
    private fun branches(query: String): List<String> =
        search(query).single().path("data").path("branches").toList().map { it.path("name").asText() }

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
