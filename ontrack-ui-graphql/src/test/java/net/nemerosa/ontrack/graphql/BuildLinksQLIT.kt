package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.forRecursiveLinks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildLinksQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Getting first level builds by default`() {
        forRecursiveLinks { _, p, builds ->
            run(
                """
                    {
                        build(id: ${p.id}) {
                            usingQualified {
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                """
            ) { data ->
                val names = data.path("build")
                    .path("usingQualified").path("pageItems")
                    .map {
                        it.path("build").path("name").asText()
                    }
                assertEquals(
                    listOf(
                        builds["q2"]?.name,
                        builds["q1"]?.name,
                    ),
                    names
                )
            }
        }
    }

    @Test
    fun `Filtering builds on project labels`() {
        forRecursiveLinks { label, p, builds ->
            run(
                """
                    {
                        build(id: ${p.id}) {
                            usingQualified(label: "${label.getDisplay()}") {
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
            """
            ) { data ->
                val names = data.path("build")
                    .path("usingQualified").path("pageItems")
                    .map {
                        it.path("build").path("name").asText()
                    }
                assertEquals(
                    listOf(
                        builds["q1"]?.name,
                    ),
                    names
                )
            }
        }
    }

    @Test
    fun `Getting recursive links with max depth`() {
        forRecursiveLinks { _, p, builds ->
            run(
                """
                    {
                        build(id: ${p.id}) {
                            usingQualified(depth: 1) {
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
            """
            ) { data ->
                val names = data.path("build")
                    .path("usingQualified").path("pageItems")
                    .map {
                        it.path("build").path("name").asText()
                    }
                assertEquals(
                    listOf(
                        builds["q2"]?.name,
                        builds["r5"]?.name,
                        builds["r4"]?.name,
                        builds["r3"]?.name,
                        builds["q1"]?.name,
                        builds["r2"]?.name,
                        builds["r1"]?.name,
                    ),
                    names
                )
            }
        }
    }

    @Test
    fun `Getting recursive links with max depth and label filtering`() {
        forRecursiveLinks { label, p, builds ->
            run(
                """
                    {
                        build(id: ${p.id}) {
                            usingQualified(depth: 1, label: "${label.getDisplay()}") {
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
            """
            ) { data ->
                val names = data.path("build")
                    .path("usingQualified").path("pageItems")
                    .map {
                        it.path("build").path("name").asText()
                    }
                assertEquals(
                    listOf(
                        builds["r4"]?.name,
                        builds["q1"]?.name,
                        builds["r1"]?.name,
                    ),
                    names
                )
            }
        }
    }

    @Test
    fun `Getting recursive links with deep depth and label filtering`() {
        forRecursiveLinks { label, p, builds ->
            run(
                """
                    {
                        build(id: ${p.id}) {
                            usingQualified(depth: 10, label: "${label.getDisplay()}") {
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
            """
            ) { data ->
                val names = data.path("build")
                    .path("usingQualified").path("pageItems")
                    .map {
                        it.path("build").path("name").asText()
                    }
                assertEquals(
                    listOf(
                        builds["r4"]?.name,
                        builds["q1"]?.name,
                        builds["s2"]?.name,
                        builds["r1"]?.name,
                    ),
                    names
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Deleting all links from a build`() {
        val source = doCreateBuild()
        val t1 = doCreateBuild()
        val t2 = doCreateBuild()
        val t3 = doCreateBuild()
        source.linkTo(t1, "")
        source.linkTo(t2, "dep")
        source.linkTo(t3, "")

        run("""
            mutation {
                deleteBuildLinks(input: { fromBuild: ${source.id} }) {
                    errors { message }
                }
            }
        """)

        run("""
            {
                build(id: ${source.id}) {
                    usingQualified {
                        pageItems { build { id } }
                    }
                }
            }
        """) { data ->
            val items = data.path("build").path("usingQualified").path("pageItems")
            assertTrue(items.isEmpty, "All links should have been deleted")
        }
    }

    @Test
    @AsAdminTest
    fun `Deleting links from a build to a given target project`() {
        val source = doCreateBuild()
        val tA = doCreateBuild()
        val tB = doCreateBuild()
        source.linkTo(tA, "")
        source.linkTo(tA, "dep")
        source.linkTo(tB, "")

        run("""
            mutation {
                deleteBuildLinks(input: { fromBuild: ${source.id}, project: "${tA.branch.project.name}" }) {
                    errors { message }
                }
            }
        """)

        run("""
            {
                build(id: ${source.id}) {
                    usingQualified {
                        pageItems { build { id } qualifier }
                    }
                }
            }
        """) { data ->
            val items = data.path("build").path("usingQualified").path("pageItems")
            assertEquals(1, items.size(), "Only the link to project B should remain")
            assertEquals(tB.id.toString(), items[0].path("build").path("id").asText())
        }
    }

    @Test
    @AsAdminTest
    fun `Deleting links from a build for a given project and qualifier`() {
        val source = doCreateBuild()
        val tA = doCreateBuild()
        val tB = doCreateBuild()
        source.linkTo(tA, "")
        source.linkTo(tA, "dep")
        source.linkTo(tB, "")

        run("""
            mutation {
                deleteBuildLinks(input: { fromBuild: ${source.id}, project: "${tA.branch.project.name}", qualifier: "dep" }) {
                    errors { message }
                }
            }
        """)

        run("""
            {
                build(id: ${source.id}) {
                    usingQualified {
                        pageItems { build { id } qualifier }
                    }
                }
            }
        """) { data ->
            val items = data.path("build").path("usingQualified").path("pageItems")
            assertEquals(2, items.size(), "Only the 'dep' link to project A should have been deleted")
            val remaining = items.map { it.path("build").path("id").asText() to it.path("qualifier").asText() }
            assertTrue(remaining.any { it.first == tA.id.toString() && it.second == "" }, "Link to A with qualifier '' should remain")
            assertTrue(remaining.any { it.first == tB.id.toString() && it.second == "" }, "Link to B should remain")
        }
    }

}