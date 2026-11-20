package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test

/**
 * The scheduling, cleanup, purge and check mutations refuse a caller without the required function.
 *
 * The services they call are tested for the full matrix: this test only checks that the refusal
 * reaches the GraphQL caller.
 */
class AutoVersioningMutationsPermissionIT : AbstractAutoVersioningTestSupport() {

    private val applicationManagementNotGranted = "Global function 'ApplicationManagement' is not granted."

    @Test
    fun `Scheduling refused without the application management`() {
        asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
            runWithError(
                """
                    mutation {
                        scheduleAutoVersioning(input: {}) {
                            errors { message }
                        }
                    }
                """,
                errorMessage = applicationManagementNotGranted,
            )
        }
    }

    @Test
    fun `Cleanup refused without the application management`() {
        asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
            runWithError(
                """
                    mutation {
                        cleanupAutoVersioningAuditEntries {
                            errors { message }
                        }
                    }
                """,
                errorMessage = applicationManagementNotGranted,
            )
        }
    }

    @Test
    fun `Purge refused without the application management`() {
        asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
            runWithError(
                """
                    mutation {
                        purgeAutoVersioningAuditEntries {
                            errors { message }
                        }
                    }
                """,
                errorMessage = applicationManagementNotGranted,
            )
        }
    }

    @Test
    fun `Check refused without the validation run creation`() {
        project {
            branch {
                val build = build()
                build.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                    runWithError(
                        """
                            mutation {
                                checkAutoVersioning(input: {
                                    project: "${project.name}",
                                    branch: "$name",
                                    build: "${build.name}"
                                }) {
                                    errors { message }
                                }
                            }
                        """,
                        errorMessage = "Project function 'ValidationRunCreate' is not granted",
                    )
                }
            }
        }
    }

    @Test
    fun `Check allowed to a controller`() {
        project {
            branch {
                val build = build()
                asAccountWithGlobalRole(Roles.GLOBAL_CONTROLLER) {
                    run(
                        """
                            mutation {
                                checkAutoVersioning(input: {
                                    project: "${project.name}",
                                    branch: "$name",
                                    build: "${build.name}"
                                }) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "checkAutoVersioning")
                    }
                }
            }
        }
    }

}
