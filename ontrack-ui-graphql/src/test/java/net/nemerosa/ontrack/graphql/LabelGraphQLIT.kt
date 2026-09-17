package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class LabelGraphQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Schema OK`() {
        val data = run("""
           {
                labels {
                    category
                    name
                }
           }
        """)
        val labels = data["labels"]
        assertNotNull(labels)
    }

    @Test
    fun `Number of projects for a label`() {
        val used = label()
        val unused = label()
        project { labels = listOf(used) }
        project { labels = listOf(used) }
        run(
            """
                query Labels(${'$'}category: String!, ${'$'}name: String!) {
                    labels(category: ${'$'}category, name: ${'$'}name) {
                        projectCount
                    }
                }
            """,
            mapOf("category" to used.category, "name" to used.name)
        ).apply {
            assertEquals(2, path("labels").first()["projectCount"].asInt())
        }
        run(
            """
                query Labels(${'$'}category: String!, ${'$'}name: String!) {
                    labels(category: ${'$'}category, name: ${'$'}name) {
                        projectCount
                    }
                }
            """,
            mapOf("category" to unused.category, "name" to unused.name)
        ).apply {
            assertEquals(0, path("labels").first()["projectCount"].asInt())
        }
    }

    @Test
    fun `Number of projects for a label counts only the projects visible to the user`() {
        val label = label()
        val visible = project { labels = listOf(label) }
        project { labels = listOf(label) }
        withNoGrantViewToAll {
            asUserWithView(visible) {
                run(
                    """
                        query Labels(${'$'}category: String!, ${'$'}name: String!) {
                            labels(category: ${'$'}category, name: ${'$'}name) {
                                projectCount
                            }
                        }
                    """,
                    mapOf("category" to label.category, "name" to label.name)
                ).apply {
                    assertEquals(1, path("labels").first()["projectCount"].asInt())
                }
            }
        }
    }

    @Test
    fun `Filtering project list based on labels`() {
        // Creates two labels
        val l1 = label()
        val l2 = label()
        // Project without label
        val p0 = project()
        // Project with one label
        val p1 = project {
            labels = listOf(l1)
        }
        // Project with two labels
        val p2 = project {
            labels = listOf(l1, l2)
        }
        // Looking without any filter on labels
        run("""
            query Projects {
                projects {
                    name
                }
            }
        """).apply {
            val names = path("projects").map { it["name"].textValue() }
            assertTrue(p0.name in names)
            assertTrue(p1.name in names)
            assertTrue(p2.name in names)
        }
        // Filter on one label
        run("""
            query Projects(${'$'}labels: [String!]!) {
                projects(labels: ${'$'}labels) {
                    name
                }
            }
        """, mapOf("labels" to listOf(l1.getDisplay()))).apply {
            val names = path("projects").map { it["name"].textValue() }
            assertFalse(p0.name in names)
            assertTrue(p1.name in names)
            assertTrue(p2.name in names)
        }
        // Filter on two labels
        run("""
            query Projects(${'$'}labels: [String!]!) {
                projects(labels: ${'$'}labels) {
                    name
                }
            }
        """, mapOf("labels" to listOf(l1.getDisplay(), l2.getDisplay()))).apply {
            val names = path("projects").map { it["name"].textValue() }
            assertFalse(p0.name in names)
            assertFalse(p1.name in names)
            assertTrue(p2.name in names)
        }
    }

    @Test
    fun `Getting a label using its ID`() {
        val label = label()
        val project = project { labels = listOf(label) }
        run(
            """
                query Label(${'$'}id: Int!) {
                    label(id: ${'$'}id) {
                        category
                        name
                        projects {
                            name
                        }
                    }
                }
            """,
            mapOf("id" to label.id)
        ).apply {
            val node = path("label")
            assertEquals(label.category, node["category"].asText())
            assertEquals(label.name, node["name"].asText())
            assertEquals(listOf(project.name), node["projects"].map { it["name"].asText() })
        }
    }

    @Test
    fun `Getting a label using an unknown ID returns null`() {
        run(
            """
                query Label(${'$'}id: Int!) {
                    label(id: ${'$'}id) {
                        name
                    }
                }
            """,
            mapOf("id" to -1)
        ).apply {
            assertTrue(path("label").isNull)
        }
    }

}