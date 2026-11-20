package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningDispatcher
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Who may reschedule an auto-versioning order: `ProjectView`, then `AutoVersioningReschedule`, on the
 * **target** project of the order.
 */
@TestPropertySource(
    properties = [
        // The rescheduled orders are not scheduled by the job
        "ontrack.extension.auto-versioning.scheduling.enabled=false",
    ]
)
@QueueNoAsync
class AutoVersioningReschedulePermissionIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    @Autowired
    private lateinit var dispatcher: AutoVersioningDispatcher

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    /**
     * A throttled order on a target branch, its source being another project.
     *
     * The [code] runs as an administrator, so that it can set up its accounts: it then runs the
     * rescheduling as one of them.
     */
    private fun withOrder(code: (source: Project, target: Branch, order: AutoVersioningOrder) -> Unit) {
        asAdmin {
            val source = project()
            val target = project<Branch> { branch() }
            val order = target.createOrder(sourceProject = source.name).apply {
                store.create(this)
                store.addState(
                    uuid = uuid,
                    targetBranch = target,
                    state = AutoVersioningAuditState.THROTTLED,
                )
            }
            code(source, target, order)
        }
    }

    private fun reschedule(target: Branch, order: AutoVersioningOrder) {
        val rescheduled = dispatcher.reschedule(target, order.uuid)
        assertNotEquals(order.uuid, rescheduled.uuid, "A new order is created")
        assertNotNull(asAdmin { store.findByUUID(target, rescheduled.uuid) }, "The new order is stored")
    }

    private fun assertAllowedWithProjectRole(role: String) {
        withOrder { _, target, order ->
            target.asAccountWithProjectRole(role) {
                reschedule(target, order)
            }
        }
    }

    private fun assertAllowedWithGlobalRole(role: String) {
        withOrder { _, target, order ->
            asAccountWithGlobalRole(role) {
                reschedule(target, order)
            }
        }
    }

    private fun assertRefused(function: String, code: () -> Unit) {
        val ex = assertFailsWith<AccessDeniedException> { code() }
        assertEquals("Project function '$function' is not granted", ex.message)
    }

    @Test
    fun `Allowed to an auto-versioning controller of the target project`() {
        assertAllowedWithProjectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER)
    }

    @Test
    fun `Allowed to an owner of the target project`() {
        assertAllowedWithProjectRole(Roles.PROJECT_OWNER)
    }

    @Test
    fun `Allowed to a project manager of the target project`() {
        assertAllowedWithProjectRole(Roles.PROJECT_MANAGER)
    }

    @Test
    fun `Allowed to a global auto-versioning controller`() {
        assertAllowedWithGlobalRole(AutoVersioningRoleContributor.GLOBAL_AUTO_VERSIONING_CONTROLLER)
    }

    @Test
    fun `Allowed to the automation`() {
        assertAllowedWithGlobalRole(Roles.GLOBAL_AUTOMATION)
    }

    @Test
    fun `Allowed to an administrator`() {
        assertAllowedWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR)
    }

    @Test
    fun `Allowed to a participant who is also an auto-versioning controller`() {
        withOrder { _, target, order ->
            val account = asAdmin {
                doCreateAccountWithProjectRole(target.project, Roles.PROJECT_PARTICIPANT).apply {
                    accountService.grantProjectPermission(
                        target.project.id,
                        PermissionTargetType.ACCOUNT,
                        id(),
                        AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER,
                    )
                }
            }
            asFixedAccount(account) {
                reschedule(target, order)
            }
        }
    }

    @Test
    fun `Refused to a participant of the target project`() {
        withOrder { _, target, order ->
            target.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                assertRefused("AutoVersioningReschedule") { dispatcher.reschedule(target, order.uuid) }
            }
        }
    }

    @Test
    fun `Refused to a read-only user of the target project`() {
        withOrder { _, target, order ->
            target.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertRefused("AutoVersioningReschedule") { dispatcher.reschedule(target, order.uuid) }
            }
        }
    }

    @Test
    fun `Refused to a user without any role, who sees all projects`() {
        withGrantViewToAll {
            withOrder { _, target, order ->
                asUser {
                    assertRefused("AutoVersioningReschedule") { dispatcher.reschedule(target, order.uuid) }
                }
            }
        }
    }

    @Test
    fun `Refused on a target project which is not visible, the project view being checked first`() {
        withNoGrantViewToAll {
            withOrder { _, target, order ->
                // Controller on another project only
                val other = asAdmin { project() }
                other.asAccountWithProjectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER) {
                    assertRefused("ProjectView") { dispatcher.reschedule(target, order.uuid) }
                }
            }
        }
    }

    @Test
    fun `Refused to an auto-versioning controller of the source project only`() {
        withNoGrantViewToAll {
            withOrder { source, target, order ->
                source.asAccountWithProjectRole(AutoVersioningRoleContributor.AUTO_VERSIONING_CONTROLLER) {
                    assertRefused("ProjectView") { dispatcher.reschedule(target, order.uuid) }
                }
            }
        }
    }

    @Test
    fun `Refused to the agent of an owner of the target project`() {
        withOrder { _, target, order ->
            val owner = asAdmin { doCreateAccountWithProjectRole(target.project, Roles.PROJECT_OWNER) }
            val agent = agentTestSupport.registerAgent(owner)
            agentTestSupport.withToken(agent.token) {
                val ex = assertFailsWith<AgentPolicyException> { dispatcher.reschedule(target, order.uuid) }
                assertEquals(
                    "agent ${agent.account.email} may not AutoVersioningReschedule (agent policy)",
                    ex.message,
                )
            }
        }
    }

    @Test
    fun `Refused to the read-only token of an owner of the target project`() {
        withOrder { _, target, order ->
            val owner = asAdmin { doCreateAccountWithProjectRole(target.project, Roles.PROJECT_OWNER) }
            val token = asAdmin {
                tokensService.generateToken(owner.id(), TokenOptions(name = "ro", readOnly = true)).value
            }
            agentTestSupport.withToken(token) {
                val ex = assertFailsWith<ReadOnlyTokenException> { dispatcher.reschedule(target, order.uuid) }
                assertEquals("read-only token 'ro' may not use AutoVersioningReschedule", ex.message)
            }
        }
    }

}
