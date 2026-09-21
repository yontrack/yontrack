package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.getRequiredIntField
import net.nemerosa.ontrack.json.getRequiredTextField
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Management of build links using GraphQL.
 */
@AsAdminTest
class BuildLinksGraphQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Linking a build to another build using names`() {
        val build1 = doCreateBuild()
        val build2 = doCreateBuild()
        asAdmin {
            run(
                """
                    mutation {
                        linkBuild(input: {
                            fromProject: "${build1.project.name}",
                            fromBuild: "${build1.name}",
                            toProject: "${build2.project.name}",
                            toBuild: "${build2.name}",
                        }) {
                            build {
                                id
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "linkBuild") { payload ->
                    assertEquals(build1.id(), payload.path("build").path("id").asInt())
                    assertNotNull(
                        structureService.getQualifiedBuildsUsedBy(build1).pageItems.find { it.build.id == build2.id },
                        "Link has been created"
                    )
                }
            }
        }
    }

    @Test
    fun `Linking a build to another build using names and a qualifier`() {
        val build1 = doCreateBuild()
        val build2 = doCreateBuild()
        asAdmin {
            run(
                """
                    mutation {
                        linkBuild(input: {
                            fromProject: "${build1.project.name}",
                            fromBuild: "${build1.name}",
                            toProject: "${build2.project.name}",
                            toBuild: "${build2.name}",
                            qualifier: "dep1",
                        }) {
                            build {
                                id
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "linkBuild") { payload ->
                    assertEquals(build1.id(), payload.path("build").path("id").asInt())
                    assertTrue(
                        structureService.isLinkedTo(
                            build1,
                            build2,
                        ),
                        "Link OK"
                    )
                    assertTrue(
                        structureService.isLinkedTo(
                            build1,
                            build2,
                            "dep1"
                        ),
                        "Qualifier OK"
                    )
                }
            }
        }
    }

    @Test
    fun `Bulk addition of build links`() {
        val build0 = doCreateBuild()
        val builds = (0..2).map { doCreateBuild() }
        val links = builds.map {
            mapOf(
                "project" to it.project.name,
                "build" to it.name,
            )
        }
        asAdmin {
            run(
                """
                    mutation BulkLinks(${'$'}links: [LinksBuildInputItem!]!) {
                        linksBuild(input: {
                            fromProject: "${build0.project.name}",
                            fromBuild: "${build0.name}",
                            links: ${'$'}links,
                        }) {
                            build {
                                id
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """,
                mapOf("links" to links)
            ) { data ->
                checkGraphQLUserErrors(data, "linksBuild") { payload ->
                    assertEquals(build0.id(), payload.path("build").path("id").asInt())
                    (0..2).forEach { no ->
                        assertNotNull(
                            structureService.getQualifiedBuildsUsedBy(build0).pageItems.find { it.build.id == builds[no].id },
                            "Link has been created"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Linking a build to another build using IDs`() {
        val build1 = doCreateBuild()
        val build2 = doCreateBuild()
        asAdmin {
            run(
                """
                    mutation {
                        linkBuildById(input: {
                            fromBuild: ${build1.id},
                            toBuild: ${build2.id},
                        }) {
                            build {
                                id
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "linkBuildById") { payload ->
                    assertEquals(build1.id(), payload.path("build").path("id").asInt())
                    assertNotNull(
                        structureService.getQualifiedBuildsUsedBy(build1).pageItems.find { it.build.id == build2.id },
                        "Link has been created"
                    )
                }
            }
        }
    }

    @Test
    fun `Getting the list of links`() {
        asAdmin {
            val build1 = doCreateBuild()
            val build2 = doCreateBuild()
            structureService.createBuildLink(build1, build2, "dep1")
            structureService.createBuildLink(build1, build2, "dep2")
            run(
                """
                query {
                    build(id: ${build1.id}) {
                        usingQualified {
                            pageItems {
                                build {
                                    id
                                }
                                qualifier
                            }
                        }
                    }
                }
            """
            ) { data ->
                val items = data.path("build").path("usingQualified").path("pageItems")
                assertEquals(
                    listOf(build2.id(), build2.id()),
                    items.values().map { it.path("build").getRequiredIntField("id") }
                )
                assertEquals(
                    setOf("dep1", "dep2"),
                    items.values().map { it.getRequiredTextField("qualifier") }.toSet()
                )
            }
        }
    }

    @Test
    fun `Getting the list of links from`() {
        asAdmin {
            val build1 = doCreateBuild()
            val build2 = doCreateBuild()
            structureService.createBuildLink(build1, build2, "dep1")
            structureService.createBuildLink(build1, build2, "dep2")
            run(
                """
                query {
                    build(id: ${build2.id}) {
                        usedByQualified {
                            pageItems {
                                build {
                                    id
                                }
                                qualifier
                            }
                        }
                    }
                }
            """
            ) { data ->
                val items = data.path("build").path("usedByQualified").path("pageItems")
                assertEquals(
                    listOf(build1.id(), build1.id()),
                    items.values().map { it.path("build").getRequiredIntField("id") }
                )
                assertEquals(
                    setOf("dep1", "dep2"),
                    items.values().map { it.getRequiredTextField("qualifier") }.toSet()
                )
            }
        }
    }

}