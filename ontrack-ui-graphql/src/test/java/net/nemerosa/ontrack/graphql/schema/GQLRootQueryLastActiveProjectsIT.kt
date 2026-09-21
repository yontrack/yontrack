package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GQLRootQueryLastActiveProjectsIT: AbstractQLKTITSupport() {

    @Test
    fun `Last active projects`() {
        asAdmin {
            val pa = project()
            val pb = project()
            val pc = project()
            val pd = project()

            pa.apply {
                branch {
                    build()
                }
            }

            pb.apply {
                branch {
                    build()
                }
            }

            pc.apply {
                branch {
                    build()
                }
            }

            pa.apply {
                branch {
                    build()
                }
            }

            run("""{
                lastActiveProjects(count: 4) {
                    name
                }
            }""") { data ->
                assertEquals(
                    listOf(
                        pa.name,
                        pc.name,
                        pb.name,
                        pd.name,
                    ),
                    data.path("lastActiveProjects").values().map {
                        it.getRequiredTextField("name")
                    }
                )
            }
        }
    }

}