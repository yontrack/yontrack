package net.nemerosa.ontrack.graphql

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class EntityCountsGraphQLIT: AbstractQLKTITSupport() {

    @Test
    fun `Project counts`() {
        asAdmin {
            project()
            run("""{
                entityCounts {
                    projects
                }
            }""").let { data ->
                assertTrue(
                    data.path("entityCounts").path("projects").asInt() > 0,
                    "At least one project"
                )
            }
        }
    }

    @Test
    fun `Counts for all project entities`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val vs = validationStamp()
                    build {
                        promote(pl)
                        validate(vs)
                    }
                }
            }
            run("""{
                entityCounts {
                    projects
                    branches
                    promotionLevels
                    validationStamps
                    builds
                    promotionRuns
                    validationRuns
                }
            }""").let { data ->
                val counts = data.path("entityCounts")
                listOf(
                    "projects",
                    "branches",
                    "promotionLevels",
                    "validationStamps",
                    "builds",
                    "promotionRuns",
                    "validationRuns",
                ).forEach { field ->
                    assertTrue(
                        counts.path(field).asInt() > 0,
                        "At least one entity counted for $field"
                    )
                }
            }
        }
    }

}
