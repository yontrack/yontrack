package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredTextField
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GQLTypeValidationRunStatusIT : AbstractQLKTITSupport() {

    @Test
    fun `Authorizations because admin`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = validate(vs)
                        run(
                            """
                                {
                                    validationRuns(id: ${run.id}) {
                                        lastStatus {
                                            authorizations {
                                                name
                                                action
                                                authorized
                                            }
                                        }
                                        validationRunStatuses {
                                            authorizations {
                                                name
                                                action
                                                authorized
                                            }
                                        }
                                    }
                                }
                        """
                        ) { data ->
                            val r = data.path("validationRuns").path(0)

                            fun checkAuth(status: JsonNode) {
                                val auths = status.path("authorizations")
                                val auth = auths.find {
                                    it.getRequiredTextField("name") == "validation_run_status" &&
                                            it.getRequiredTextField("action") == "comment_change"
                                }
                                assertNotNull(auth) {
                                    assertEquals(
                                        true,
                                        it.getRequiredBooleanField("authorized"),
                                        "Comment change is authorized"
                                    )
                                }
                            }

                            checkAuth(r.path("lastStatus"))
                            checkAuth(r.path("validationRunStatuses").path(0))

                        }
                    }
                }
            }
        }
    }

}