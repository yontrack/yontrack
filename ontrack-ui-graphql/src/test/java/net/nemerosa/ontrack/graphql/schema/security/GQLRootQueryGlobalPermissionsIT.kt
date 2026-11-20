package net.nemerosa.ontrack.graphql.schema.security

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class GQLRootQueryGlobalPermissionsIT : AbstractQLKTITSupport() {

    @Test
    @AsAdminTest
    fun `Getting the global permissions`() {
        val group = doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_AUTOMATION)
        val account = doCreateAccountWithGlobalRole(Roles.GLOBAL_PARTICIPANT)

        run(
            """
                {
                    globalPermissions {
                        target {
                            type
                            id
                            name
                        }
                        role {
                            id
                            name
                        }
                    }
                }
            """.trimIndent()
        ) { data ->

            val groupPermission = data.path("globalPermissions")
                .find {
                    val target = it.path("target")
                    target.path("type").asText() == "GROUP" &&
                            target.path("id").asInt() == group.id()
                }
            assertNotNull(groupPermission) {
                assertEquals(
                    group.name,
                    it.path("target").path("name").asText()
                )
                val role = it.path("role")
                assertEquals(
                    Roles.GLOBAL_AUTOMATION,
                    role.path("id").asText()
                )
                assertEquals(
                    "Automation",
                    role.path("name").asText()
                )
            }

            val accountPermission = data.path("globalPermissions")
                .find {
                    val target = it.path("target")
                    target.path("type").asText() == "ACCOUNT" &&
                            target.path("id").asInt() == account.id()
                }
            assertNotNull(accountPermission) {
                assertEquals(
                    account.email,
                    it.path("target").path("name").asText()
                )
                val role = it.path("role")
                assertEquals(
                    Roles.GLOBAL_PARTICIPANT,
                    role.path("id").asText()
                )
                assertEquals(
                    "Participant",
                    role.path("name").asText()
                )
            }
        }
    }

    @Test
    fun `Getting the global permissions is refused without the management of accounts or of groups`() {
        asAccountWithGlobalRole(Roles.GLOBAL_PARTICIPANT) {
            runWithMatchingError(
                "{ globalPermissions { role { id } } }",
                errorClassification = ErrorType.FORBIDDEN
            )
        }
    }

}