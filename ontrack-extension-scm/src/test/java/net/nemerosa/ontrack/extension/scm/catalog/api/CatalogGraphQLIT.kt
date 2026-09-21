package net.nemerosa.ontrack.extension.scm.catalog.api

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.scm.catalog.CatalogFixtures.entry
import net.nemerosa.ontrack.extension.scm.catalog.CatalogFixtures.team
import net.nemerosa.ontrack.extension.scm.catalog.CatalogLinkService
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalog
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogAccessFunction
import net.nemerosa.ontrack.extension.scm.catalog.mock.MockSCMCatalogProvider
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class CatalogGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var scmCatalog: SCMCatalog

    @Autowired
    private lateinit var catalogLinkService: CatalogLinkService

    @Autowired
    private lateinit var scmCatalogProvider: MockSCMCatalogProvider

    @Test
    fun `SCM catalog stats per team`() {
        scmCatalogProvider.clear()
        // Registration of mock entries with their teams
        val entries = listOf(
            entry(
                scm = "mocking", repository = "project/repository-1", config = "my-config", teams = listOf(
                    team("team-1")
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-2", config = "my-config", teams = listOf(
                    team("team-1")
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-3", config = "my-config", teams = listOf(
                    team("team-1")
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-4", config = "my-config", teams = listOf(
                    team("team-2")
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-5", config = "my-config", teams = listOf(
                    team("team-2")
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-6", config = "my-config", teams = listOf(
                    team("team-3"),
                )
            ),
            entry(
                scm = "mocking", repository = "project/repository-7", config = "my-config", teams = listOf(
                    team("team-4"),
                    team("team-5"),
                )
            ),
        )
        entries.forEach { entry ->
            scmCatalogProvider.storeEntry(entry)
        }
        // Collection of entries
        scmCatalog.collectSCMCatalog { println(it) }
        // Gets stats about teams
        asAdmin {
            run(
                """{
                scmCatalogTeams {
                    id
                    entryCount
                    entries {
                        repository
                    }
                }
            }"""
            ) { data ->
                val scmCatalogTeams = data.path("scmCatalogTeams")
                assertEquals(5, scmCatalogTeams.size())
                assertEquals(
                    mapOf(
                        "id" to "team-1",
                        "entryCount" to 3,
                        "entries" to listOf(
                            mapOf("repository" to "project/repository-1"),
                            mapOf("repository" to "project/repository-2"),
                            mapOf("repository" to "project/repository-3"),
                        )
                    ).asJson(),
                    scmCatalogTeams.find { it.getTextField("id") == "team-1" }
                )
                assertEquals(
                    mapOf(
                        "id" to "team-2",
                        "entryCount" to 2,
                        "entries" to listOf(
                            mapOf("repository" to "project/repository-4"),
                            mapOf("repository" to "project/repository-5"),
                        )
                    ).asJson(),
                    scmCatalogTeams.find { it.getTextField("id") == "team-2" }
                )
                assertEquals(
                    mapOf(
                        "id" to "team-3",
                        "entryCount" to 1,
                        "entries" to listOf(
                            mapOf("repository" to "project/repository-6"),
                        )
                    ).asJson(),
                    scmCatalogTeams.find { it.getTextField("id") == "team-3" }
                )
                assertEquals(
                    mapOf(
                        "id" to "team-4",
                        "entryCount" to 1,
                        "entries" to listOf(
                            mapOf("repository" to "project/repository-7"),
                        )
                    ).asJson(),
                    scmCatalogTeams.find { it.getTextField("id") == "team-4" }
                )
                assertEquals(
                    mapOf(
                        "id" to "team-5",
                        "entryCount" to 1,
                        "entries" to listOf(
                            mapOf("repository" to "project/repository-7"),
                        )
                    ).asJson(),
                    scmCatalogTeams.find { it.getTextField("id") == "team-5" }
                )
            }
        }
        // Gets stats about team count
        asAdmin {
            run(
                """{
                scmCatalogTeamStats {
                    teamCount
                    entryCount
                }
            }"""
            ) { data ->
                assertEquals(
                    mapOf(
                        "scmCatalogTeamStats" to listOf(
                            mapOf("teamCount" to 2, "entryCount" to 1),
                            mapOf("teamCount" to 1, "entryCount" to 6),
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    @Disabled("FLAKY")
    fun `Collection of entries and linking`() {
        scmCatalogProvider.clear()
        deleteAllProjects()
        // Registration of mock entry
        val entry = entry(scm = "mocking", repository = "project/repository", config = "my-config")
        scmCatalogProvider.storeEntry(entry)
        // Collection of entries
        scmCatalog.collectSCMCatalog { println(it) }
        // Checks entry has been collected
        val collectionData = withGrantViewToAll {
            asUserWith<SCMCatalogAccessFunction, JsonNode> {
                run(
                    """{
                        scmCatalog(scm: "mocking") {
                            pageItems {
                                entry {
                                    scm
                                    config
                                    repository
                                    repositoryPage
                                }
                            }
                        }
                    }"""
                )
            }
        }
        val item = collectionData["scmCatalog"]["pageItems"][0]
        assertJsonNotNull(item["entry"], "Entry is defined")
        assertEquals("mocking", item["entry"]["scm"].asText())
        assertEquals("my-config", item["entry"]["config"].asText())
        assertEquals("project/repository", item["entry"]["repository"].asText())
        assertEquals("uri:project/repository", item["entry"]["repositoryPage"].asText())
        // Search on orphan entries
        val orphanData = withGrantViewToAll {
            asUserWith<SCMCatalogAccessFunction, JsonNode> {
                run(
                    """{
                    scmCatalog(link: "UNLINKED") {
                        pageItems {
                            entry {
                                repository
                            }
                            project {
                                name
                            }
                        }
                    }
                }"""
                )
            }
        }
        assertNotNull(orphanData) {
            val orphanItem = it["scmCatalog"]["pageItems"][0]
            assertEquals("project/repository", orphanItem["entry"]["repository"].asText())
            assertTrue(orphanItem["project"].isNull, "Entry is not linked")
        }
        // Link with one project
        val project = project {
            // Collection of catalog links
            scmCatalogProvider.linkEntry(entry, this)
            catalogLinkService.computeCatalogLinks()
            // Checks the link has been recorded
            val linkedEntry = catalogLinkService.getSCMCatalogEntry(this)
            assertNotNull(linkedEntry, "Project is linked to a SCM catalog entry") {
                assertEquals("mocking", it.scm)
                assertEquals("my-config", it.config)
                assertEquals("project/repository", it.repository)
                assertEquals("uri:project/repository", it.repositoryPage)
            }
            // Getting the SCM entry through GraphQL & project root
            val data = withGrantViewToAll {
                asUserWith<SCMCatalogAccessFunction, JsonNode> {
                    run(
                        """
                        query ProjectInfo {
                            projects(id: $id) {
                                scmCatalogEntry {
                                    scm
                                    config
                                    repository
                                    repositoryPage
                                }
                            }
                        }
                    """
                    )
                }
            }
            // Checking data
            assertNotNull(data) {
                val projectItem = it["projects"][0]["scmCatalogEntry"]
                assertEquals("mocking", projectItem["scm"].asText())
                assertEquals("my-config", projectItem["config"].asText())
                assertEquals("project/repository", projectItem["repository"].asText())
                assertEquals("uri:project/repository", projectItem["repositoryPage"].asText())
            }
            // Getting the data through GraphQL & catalog entries
            val entryCollectionData = withGrantViewToAll {
                asUserWith<SCMCatalogAccessFunction, JsonNode> {
                    run(
                        """{
                        scmCatalog {
                            pageItems {
                                project {
                                    name
                                }
                            }
                        }
                    }"""
                    )
                }
            }
            assertNotNull(entryCollectionData) {
                val project = it["scmCatalog"]["pageItems"][0]["project"]
                assertEquals(name, project["name"].asText())
            }
        }
        // Search on linked entries
        val data = withGrantViewToAll {
            asUserWith<SCMCatalogAccessFunction, JsonNode> {
                run(
                    """{
                    scmCatalog(link: "LINKED") {
                        pageItems {
                            project {
                                name
                            }
                        }
                    }
                }"""
                )
            }
        }
        assertNotNull(data) {
            val projectItem = it["scmCatalog"]["pageItems"][0]["project"]
            assertEquals(project.name, projectItem["name"].asText())
        }
    }

    @Test
    fun `SCM Catalog accessible to administrators in view-to-all mode`() {
        scmCatalogTest {
            withGrantViewToAll {
                asAdmin(it)
            }
        }
    }

    @Test
    fun `SCM Catalog accessible to administrators in restricted mode`() {
        scmCatalogTest {
            withNoGrantViewToAll {
                asAdmin(it)
            }
        }
    }

    @Test
    fun `SCM Catalog accessible to global read only`() {
        scmCatalogTest {
            withNoGrantViewToAll {
                asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY, it)
            }
        }
    }

    private fun scmCatalogTest(setup: (code: () -> Unit) -> Unit) {
        // Collection of entries
        val entry = entry(scm = "mocking")
        scmCatalogProvider.clear()
        scmCatalogProvider.storeEntry(entry)
        scmCatalog.collectSCMCatalog { println(it) }
        // Link with one project
        val project = project {
            scmCatalogProvider.linkEntry(entry, this)
            catalogLinkService.computeCatalogLinks()
        }
        // Checks rights
        setup {
            val data = run(
                """{
                    scmCatalog(link: "LINKED") {
                        pageItems {
                            project {
                                name
                            }
                        }
                    }
                }"""
            )
            assertNotNull(data) {
                val entryItem = it["scmCatalog"]["pageItems"][0]
                assertEquals(project.name, entryItem["project"]["name"].asText())
            }
        }
    }

}