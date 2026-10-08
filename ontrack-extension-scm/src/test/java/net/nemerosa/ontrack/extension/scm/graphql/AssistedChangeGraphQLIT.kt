package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Build.assistedChange` in GraphQL, and the previous build it was computed from (#2033).
 */
@AsAdminTest
class AssistedChangeGraphQLIT : AbstractQLKTITSupport() {

    private val query = """
        query AssistedChange(${'$'}id: Int!) {
            build(id: ${'$'}id) {
                assistedChange {
                    basis
                    assistedCommits
                    totalCommits
                    previousBuildId
                    previousBuild {
                        id
                        name
                    }
                }
            }
        }
    """

    @Test
    fun `The previous build of an assisted change`() {
        project {
            branch {
                val from = build()
                build {
                    propertyService.editProperty(
                        this,
                        AssistedChangePropertyType::class.java,
                        AssistedChangeProperty(
                            basis = AssistedChangeBasis.SET_BY_CI,
                            assistants = listOf("Claude Code"),
                            assistedCommits = 1,
                            totalCommits = 2,
                            previousBuildId = from.id(),
                        )
                    )
                    run(query, mapOf("id" to id())) { data ->
                        val assistedChange = data.path("build").path("assistedChange")
                        assertEquals("SET_BY_CI", assistedChange.path("basis").asString())
                        assertEquals(1, assistedChange.path("assistedCommits").asInt())
                        assertEquals(2, assistedChange.path("totalCommits").asInt())
                        assertEquals(from.id(), assistedChange.path("previousBuildId").asInt())
                        assertEquals(from.id(), assistedChange.path("previousBuild").path("id").asInt())
                        assertEquals(from.name, assistedChange.path("previousBuild").path("name").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `No previous build when the assisted change has none`() {
        project {
            branch {
                build {
                    propertyService.editProperty(
                        this,
                        AssistedChangePropertyType::class.java,
                        AssistedChangeProperty(
                            basis = AssistedChangeBasis.SET_BY_CI,
                            assistants = listOf("Claude Code"),
                        )
                    )
                    run(query, mapOf("id" to id())) { data ->
                        val assistedChange = data.path("build").path("assistedChange")
                        assertTrue(assistedChange.path("previousBuildId").isNull)
                        assertTrue(assistedChange.path("previousBuild").isNull)
                    }
                }
            }
        }
    }
}
