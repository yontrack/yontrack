package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryFilter
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.av.security.AutoVersioningRoleContributor
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@QueueNoAsync
class AutoVersioningMutationRescheduleIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningAuditQueryService: AutoVersioningAuditQueryService

    @Test
    @AsAdminTest
    fun `Rescheduling an order`() {
        withSimpleSetup { sourcePromotion, targetBranch ->

            // Launching an AV request
            sourcePromotion.branch.apply {
                build("1.0.0") {
                    promote(sourcePromotion)
                }
            }

            val entry = autoVersioningAuditQueryService.findByFilter(
                filter = AutoVersioningAuditQueryFilter(
                    project = targetBranch.project.name,
                    branch = targetBranch.name,
                    version = "1.0.0"
                )
            ).firstOrNull()

            assertNotNull(entry, "Entry 1.0.0 found") {
                assertEquals(
                    AutoVersioningAuditState.PR_MERGED,
                    it.mostRecentState.state,
                    "Entry 1.0.0 has been merged",
                )
            }

            // Rescheduling the order
            run(
                """
                    mutation {
                        rescheduleAutoVersioning(input: {
                            uuid: "${entry.order.uuid}"
                        }) {
                            order {
                                uuid
                            }
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "rescheduleAutoVersioning") { node ->
                    val uuid = node.path("order").path("uuid").asText()
                    // Was rescheduled, but aborted since same version
                    assertNotNull(
                        autoVersioningAuditQueryService.findByUUID(targetBranch, uuid),
                        "Rescheduled entry 1.0.0 found"
                    ) {
                        assertEquals(
                            AutoVersioningAuditState.PROCESSING_ABORTED,
                            it.mostRecentState.state,
                            "Rescheduled entry 1.0.0 has been aborted (same version)",
                        )
                    }
                }
            }

        }
    }

    @Test
    fun `Rescheduling an order is refused to a participant of the target project`() {
        withSimpleSetup { sourcePromotion, targetBranch ->
            sourcePromotion.branch.apply {
                build("1.0.0") {
                    promote(sourcePromotion)
                }
            }
            val entry = autoVersioningAuditQueryService.findByFilter(
                filter = AutoVersioningAuditQueryFilter(
                    project = targetBranch.project.name,
                    branch = targetBranch.name,
                    version = "1.0.0"
                )
            ).first()

            targetBranch.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                runWithError(
                    """
                        mutation {
                            rescheduleAutoVersioning(input: {
                                uuid: "${entry.order.uuid}"
                            }) {
                                order {
                                    uuid
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """,
                    errorMessage = "Project function 'AutoVersioningReschedule' is not granted",
                )
            }

            // No new order
            assertEquals(
                1,
                autoVersioningAuditQueryService.findByFilter(
                    filter = AutoVersioningAuditQueryFilter(
                        project = targetBranch.project.name,
                        branch = targetBranch.name,
                    )
                ).size,
                "The order has not been rescheduled",
            )
        }
    }

    @Test
    fun `Rescheduling an order as an auto-versioning controller of the target project`() {
        withSimpleSetup { sourcePromotion, targetBranch ->
            sourcePromotion.branch.apply {
                build("1.0.0") {
                    promote(sourcePromotion)
                }
            }
            val entry = autoVersioningAuditQueryService.findByFilter(
                filter = AutoVersioningAuditQueryFilter(
                    project = targetBranch.project.name,
                    branch = targetBranch.name,
                    version = "1.0.0"
                )
            ).first()

            targetBranch.asAccountWithProjectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER) {
                run(
                    """
                        mutation {
                            rescheduleAutoVersioning(input: {
                                uuid: "${entry.order.uuid}"
                            }) {
                                order {
                                    uuid
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "rescheduleAutoVersioning") { node ->
                        val uuid = node.path("order").path("uuid").asText()
                        assertNotNull(
                            asAdmin { autoVersioningAuditQueryService.findByUUID(targetBranch, uuid) },
                            "Rescheduled entry found"
                        )
                    }
                }
            }
        }
    }

}
