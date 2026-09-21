package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@AsAdminTest
class GQLProjectPromotionLevelNamesFieldContributorIT : AbstractQLKTITSupport() {

    @Test
    fun `Promotion level names for a project using a token`() {
        doTest(
            token = "ACC",
            expected = listOf(
                "ACCEPTANCE",
                "KDSL-ACCEPTANCE",
            )
        )
    }

    @Test
    fun `Promotion level names for a project using an empty token`() {
        doTest(
            token = "",
            expected = listOf(
                "ACCEPTANCE",
                "KDSL-ACCEPTANCE",
                "PERFORMANCES"
            )
        )
    }

    @Test
    fun `Promotion level names for a project using a null token`() {
        doTest(
            token = null,
            expected = listOf(
                "ACCEPTANCE",
                "KDSL-ACCEPTANCE",
                "PERFORMANCES"
            )
        )
    }

    private fun doTest(
        token: String?,
        expected: List<String>,
    ) {
        project {
            branch("main") {
                promotionLevel("ACCEPTANCE")
            }
            branch("staging") {
                promotionLevel("KDSL-ACCEPTANCE")
                promotionLevel("PERFORMANCES")
            }
            run(
                """
                    query Test(${'$'}token: String) {
                        project(id: $id) {
                            promotionLevelNames(token: ${'$'}token)
                        }
                    }
                """.trimIndent(),
                mapOf("token" to token)
            ) { data ->
                val names = data.path("project")
                    .path("promotionLevelNames")
                    .values().map { it.asText() }
                assertEquals(expected, names)
            }
        }
    }

}