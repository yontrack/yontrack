package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchGraphQLIT : AbstractSearchTestSupport() {

    @Test
    fun `Looking for a branch`() {
        project {
            branch {
                val data = run("""{
                    search(type: "branch", token: "$name") {
                        pageItems {
                            title
                            description
                            accuracy
                            type {
                                id 
                                name 
                            }
                        }
                    }
                }""")
                val results = data["search"]["pageItems"]
                val result = results.find { it["title"].asText() == "${project.name}/$name" }
                assertNotNull(result, "Branch found") { node ->
                    assertEquals("branch", node["type"]["id"].asText())
                    assertEquals("Branch", node["type"]["name"].asText())
                }
            }
        }
    }

    @Test
    fun `Pagination on builds`() {
        val prefix = uid("v")
        val project = project {
            branch {
                // Creates N builds
                (1..25).forEach {
                    build {
                        release("$prefix $it")
                    }
                }
            }
        }
        // Looks for the builds
        withNoGrantViewToAll {
            project.asUserWithView {
                val variables = mutableMapOf(
                        "token" to prefix,
                        "offset" to 0,
                        "size" to 10
                )
                val query = """query Search(${'$'}token: String!, ${'$'}offset: Int!, ${'$'}size: Int!) {
                    search(type: "build-release", token: ${'$'}token, offset: ${'$'}offset, size: ${'$'}size) {
                        pageInfo {
                            totalSize
                            currentOffset
                            currentSize
                            previousPage { offset size }
                            nextPage { offset size }
                        }
                        pageItems {
                            title
                        }
                    }
                }"""
                // First 10 builds
                val first = run(query, variables)
                first["search"]["pageInfo"].let {
                    assertEquals(25, it["totalSize"].asInt())
                    assertEquals(0, it["currentOffset"].asInt())
                    assertEquals(10, it["currentSize"].asInt())
                    assertTrue(it["previousPage"].isNull)
                    assertNotNull(it["nextPage"]) { page ->
                        assertEquals(10, page["offset"].asInt())
                        assertEquals(10, page["size"].asInt())
                    }
                }
                // Next 10 builds
                val next = run(query, variables + mapOf("offset" to 10))
                next["search"]["pageInfo"].let {
                    assertEquals(25, it["totalSize"].asInt())
                    assertEquals(10, it["currentOffset"].asInt())
                    assertEquals(10, it["currentSize"].asInt())
                    assertNotNull(it["previousPage"]) { page ->
                        assertEquals(0, page["offset"].asInt())
                        assertEquals(10, page["size"].asInt())
                    }
                    assertNotNull(it["nextPage"]) { page ->
                        assertEquals(20, page["offset"].asInt())
                        assertEquals(5, page["size"].asInt())
                    }
                }
                // Last 5 builds
                val last = run(query, variables + mapOf("offset" to 20))
                last["search"]["pageInfo"].let {
                    assertEquals(25, it["totalSize"].asInt())
                    assertEquals(20, it["currentOffset"].asInt())
                    assertEquals(5, it["currentSize"].asInt())
                    assertNotNull(it["previousPage"]) { page ->
                        assertEquals(10, page["offset"].asInt())
                        assertEquals(10, page["size"].asInt())
                    }
                    assertTrue(it["nextPage"].isNull)
                }
            }
        }
    }


    @Test
    fun `Searching across types with total, facets and items`() {
        val name = token()
        val project = project(NameDescription.nd(name, "Description of $name"))
        val data = run("""{
            search(query: "$name", types: ["project"]) {
                total
                message
                facets {
                    type { id name }
                    count
                }
                items {
                    title
                    description
                    accuracy
                    type { id }
                    data
                }
            }
        }""")
        val search = data["search"]
        assertEquals(1, search["total"].asInt())
        assertTrue(search["message"].isNull)
        assertEquals(1, search["facets"].size())
        assertEquals("project", search["facets"][0]["type"]["id"].asText())
        assertEquals("Project", search["facets"][0]["type"]["name"].asText())
        assertEquals(1, search["facets"][0]["count"].asInt())
        val item = search["items"][0]
        assertEquals(name, item["title"].asText())
        assertEquals("Description of $name", item["description"].asText())
        assertEquals("project", item["type"]["id"].asText())
        assertEquals(project.id(), item["data"]["project"]["id"].asInt())
        assertEquals(name, item["data"]["project"]["name"].asText())
    }

    @Test
    fun `Highlighting the free text of the results`() {
        val name = token()
        project(NameDescription.nd(name, "About <b>$name</b> & more"))
        val data = run("""{
            search(query: "$name", types: ["project"]) {
                items {
                    title
                    highlight { text match }
                }
            }
        }""")
        val highlight = data["search"]["items"][0]["highlight"]
        assertEquals(
            listOf("About <b>" to false, name to true, "</b> & more" to false),
            highlight.values().map { it["text"].asText() to it["match"].asBoolean() }
        )
    }

    @Test
    fun `No highlight for a result without free text`() {
        val name = token()
        project(NameDescription.nd(name, ""))
        val data = run("""{
            search(query: "$name", types: ["project"]) {
                items { highlight { text match } }
            }
        }""")
        assertTrue(data["search"]["items"][0]["highlight"].isNull)
    }

    @Test
    fun `The deprecated search on one type is a wrapper of the new one`() {
        val name = token()
        project(NameDescription.nd(name, ""))
        val data = run("""{
            search(type: "project", token: "$name") {
                pageInfo { totalSize currentOffset currentSize }
                pageItems { title type { id } }
            }
        }""")
        val search = data["search"]
        assertEquals(1, search["pageInfo"]["totalSize"].asInt())
        assertEquals(0, search["pageInfo"]["currentOffset"].asInt())
        assertEquals(1, search["pageInfo"]["currentSize"].asInt())
        assertEquals(listOf(name), search["pageItems"].values().map { it["title"].asText() })
    }

    @Test
    fun `A query is required`() {
        runWithMatchingError(
            """{ search(types: ["project"]) { total } }""",
            errorMessage = "A query is required",
        )
    }

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "p" + UUID.randomUUID().toString().replace("-", "").take(15)

}