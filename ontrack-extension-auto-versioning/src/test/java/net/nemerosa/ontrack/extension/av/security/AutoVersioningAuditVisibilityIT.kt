package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningController
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryFilter
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueuePayload
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueueProcessor
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An auto-versioning order is visible with `ProjectView` on its **target** project, the source
 * project not being checked.
 */
@TestPropertySource(
    properties = [
        // The orders stay where the tests put them
        "ontrack.extension.auto-versioning.scheduling.enabled=false",
    ]
)
class AutoVersioningAuditVisibilityIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    @Autowired
    private lateinit var queryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var controller: AutoVersioningController

    @Autowired
    private lateinit var queueProcessor: AutoVersioningQueueProcessor

    private class Setup(
        val source: Project,
        val visible: Branch,
        val hidden: Branch,
        val visibleOrders: List<AutoVersioningOrder>,
        val hiddenOrders: List<AutoVersioningOrder>,
    )

    /**
     * Two target projects with pending orders from the same source project: three orders on the
     * first one, two on the second one.
     *
     * The [code] runs as an administrator, without the grant of `ProjectView` to all, and with an
     * empty audit, so that the counts are exact.
     */
    private fun withOrders(code: (setup: Setup) -> Unit) {
        withNoGrantViewToAll {
            asAdmin {
                store.removeAll()
                val source = project()
                val visible = project<Branch> { branch() }
                val hidden = project<Branch> { branch() }
                val visibleOrders = (1..3).map { visible.order(source, "1.0.$it") }
                val hiddenOrders = (1..2).map { hidden.order(source, "2.0.$it") }
                code(Setup(source, visible, hidden, visibleOrders, hiddenOrders))
            }
        }
    }

    private fun Branch.order(source: Project, version: String) =
        createOrder(sourceProject = source.name, targetVersion = version).apply {
            store.create(this)
        }

    private val List<AutoVersioningOrder>.uuids get() = map { it.uuid }.toSet()

    @Test
    fun `A user with ProjectView on one target project sees only its orders, with the right count and pages`() {
        withOrders { setup ->
            setup.visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertEquals(3, queryService.countByFilter(AutoVersioningAuditQueryFilter()))
                assertEquals(
                    setup.visibleOrders.uuids,
                    queryService.findByFilter(AutoVersioningAuditQueryFilter()).map { it.order.uuid }.toSet(),
                )
                // Pages of two: two orders, then the last one
                val firstPage = queryService.findByFilter(AutoVersioningAuditQueryFilter(offset = 0, count = 2))
                val secondPage = queryService.findByFilter(AutoVersioningAuditQueryFilter(offset = 2, count = 2))
                assertEquals(2, firstPage.size)
                assertEquals(1, secondPage.size)
                assertEquals(
                    setup.visibleOrders.uuids,
                    (firstPage + secondPage).map { it.order.uuid }.toSet(),
                )
                // Filtering on the hidden project gives nothing
                assertEquals(
                    0,
                    queryService.countByFilter(AutoVersioningAuditQueryFilter(project = setup.hidden.project.name)),
                )
            }
        }
    }

    @Test
    fun `The GraphQL list of audit entries is limited to the visible target projects`() {
        withOrders { setup ->
            setup.visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val page = run(
                    """
                        {
                            autoVersioningAuditEntries(size: 2) {
                                pageInfo {
                                    totalSize
                                    nextPage {
                                        offset
                                        size
                                    }
                                }
                                pageItems {
                                    order {
                                        uuid
                                    }
                                }
                            }
                        }
                    """
                ).path("autoVersioningAuditEntries")
                assertEquals(3, page.path("pageInfo").path("totalSize").asInt())
                assertEquals(2, page.path("pageInfo").path("nextPage").path("offset").asInt())
                val uuids = page.path("pageItems").values().map { it.path("order").path("uuid").asText() }
                assertEquals(2, uuids.size)
                assertEquals(true, setup.visibleOrders.uuids.containsAll(uuids))
            }
        }
    }

    @Test
    fun `The source project gives no visibility on the orders`() {
        withOrders { setup ->
            setup.source.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertEquals(0, queryService.countByFilter(AutoVersioningAuditQueryFilter()))
                assertEquals(emptyList(), queryService.findByFilter(AutoVersioningAuditQueryFilter()))
            }
        }
    }

    @Test
    fun `A global read-only user sees all the orders`() {
        withOrders { setup ->
            asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(5, queryService.countByFilter(AutoVersioningAuditQueryFilter()))
                assertEquals(
                    setup.visibleOrders.uuids + setup.hiddenOrders.uuids,
                    queryService.findByFilter(AutoVersioningAuditQueryFilter(count = 10))
                        .map { it.order.uuid }.toSet(),
                )
            }
        }
    }

    @Test
    fun `A user without any role sees all the orders when ProjectView is granted to all`() {
        withOrders { _ ->
            withGrantViewToAll {
                asUser {
                    assertEquals(5, queryService.countByFilter(AutoVersioningAuditQueryFilter()))
                }
            }
        }
    }

    @Test
    fun `The single entry of a project which is not visible is not found`() {
        withOrders { setup ->
            val hiddenOrder = setup.hiddenOrders.first()
            val visibleOrder = setup.visibleOrders.first()
            setup.visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val query = """
                    query(${'$'}uuid: String!) {
                        autoVersioningAuditEntry(uuid: ${'$'}uuid) {
                            order {
                                uuid
                            }
                        }
                    }
                """
                assertJsonNull(
                    run(query, mapOf("uuid" to hiddenOrder.uuid)).path("autoVersioningAuditEntry"),
                    "Entry of the hidden project not found",
                )
                assertEquals(
                    visibleOrder.uuid,
                    run(query, mapOf("uuid" to visibleOrder.uuid))
                        .path("autoVersioningAuditEntry").path("order").path("uuid").asText(),
                    "Entry of the visible project found",
                )
                assertNull(queryService.findByUUID(setup.hidden, hiddenOrder.uuid))
                assertNotNull(queryService.findByUUID(setup.visible, visibleOrder.uuid))
            }
        }
    }

    @Test
    fun `Rescheduling an order of a project which is not visible fails like a non-existent order`() {
        withOrders { setup ->
            val hiddenOrder = setup.hiddenOrders.first()
            val missing = UUID.randomUUID().toString()
            setup.visible.asAccountWithProjectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER) {
                val hiddenError = rescheduleError(hiddenOrder.uuid)
                val missingError = rescheduleError(missing)
                assertEquals(
                    "Auto versioning audit entry for UUID = ${hiddenOrder.uuid} cannot be found.",
                    hiddenError,
                )
                assertEquals(
                    "Auto versioning audit entry for UUID = $missing cannot be found.",
                    missingError,
                )
            }
            // Nothing was rescheduled
            assertEquals(2, queryService.countByFilter(AutoVersioningAuditQueryFilter(project = setup.hidden.project.name)))
        }
    }

    /**
     * Runs the reschedule mutation and returns its error message.
     */
    private fun rescheduleError(uuid: String): String {
        val payload = run(
            """
                mutation(${'$'}uuid: String!) {
                    rescheduleAutoVersioning(input: {uuid: ${'$'}uuid}) {
                        order {
                            uuid
                        }
                        errors {
                            message
                        }
                    }
                }
            """,
            mapOf("uuid" to uuid)
        ).path("rescheduleAutoVersioning")
        assertJsonNull(payload.path("order"), "No order rescheduled")
        return payload.path("errors").first().path("message").asText()
    }

    @Test
    fun `The stats count only the visible pending orders`() {
        withOrders { setup ->
            setup.visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertEquals(3, controller.getStats().body?.pendingOrders)
            }
            setup.hidden.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertEquals(2, controller.getStats().body?.pendingOrders)
            }
            asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(5, controller.getStats().body?.pendingOrders)
            }
        }
    }

    @Test
    fun `The queue processor sees the orders whatever the security context`() {
        withOrders { setup ->
            val payload = AutoVersioningQueuePayload(setup.hiddenOrders.first())
            // The queue consumer has no security context of its own
            asAnonymous().call {
                assertNull(queueProcessor.isCancelled(payload), "The running order is not cancelled")
            }
            // Not even a user who cannot see the target project changes this
            setup.visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertNull(queueProcessor.isCancelled(payload), "The running order is not cancelled")
            }
        }
    }

}
