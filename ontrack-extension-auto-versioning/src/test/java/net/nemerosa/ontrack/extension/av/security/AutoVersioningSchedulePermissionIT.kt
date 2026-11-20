package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.scheduler.AutoVersioningScheduler
import net.nemerosa.ontrack.extension.av.scheduler.AutoVersioningSchedulerJob
import net.nemerosa.ontrack.job.JobDecorator
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Scheduling the auto-versioning orders on demand needs the global `ApplicationManagement` function,
 * while the scheduling job keeps running without any user.
 */
class AutoVersioningSchedulePermissionIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var scheduler: AutoVersioningScheduler

    @Autowired
    private lateinit var schedulerJob: AutoVersioningSchedulerJob

    @Autowired
    private lateinit var jobDecorator: JobDecorator

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    @BeforeEach
    fun init() {
        asAdmin { store.removeAll() }
    }

    /**
     * A created order, ready to be scheduled.
     */
    private fun withOrder(code: (target: Branch, entry: AutoVersioningAuditEntry) -> Unit) {
        asAdmin {
            val target = project<Branch> { branch() }
            val entry = target.createOrder(sourceProject = "source").run { store.create(this) }
            code(target, entry)
        }
    }

    private fun assertScheduled(target: Branch, entry: AutoVersioningAuditEntry) {
        assertNotNull(asAdmin { store.findByUUID(target, entry.order.uuid) }) {
            assertNotEquals(AutoVersioningAuditState.CREATED, it.mostRecentState.state, "The order is scheduled")
        }
    }

    private fun assertNotScheduled(target: Branch, entry: AutoVersioningAuditEntry) {
        assertNotNull(asAdmin { store.findByUUID(target, entry.order.uuid) }) {
            assertEquals(AutoVersioningAuditState.CREATED, it.mostRecentState.state, "The order is not scheduled")
        }
    }

    @Test
    fun `Scheduling is allowed with the application management`() {
        withOrder { target, entry ->
            asUser().with(ApplicationManagement::class.java).call {
                scheduler.schedule(Time.now)
            }
            assertScheduled(target, entry)
        }
    }

    @Test
    fun `Scheduling is allowed to an administrator`() {
        withOrder { target, entry ->
            asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
                scheduler.schedule(Time.now)
            }
            assertScheduled(target, entry)
        }
    }

    @Test
    fun `Scheduling is refused to the automation`() {
        withOrder { target, entry ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                val ex = assertFailsWith<AccessDeniedException> { scheduler.schedule(Time.now) }
                assertEquals("Global function 'ApplicationManagement' is not granted.", ex.message)
            }
            assertNotScheduled(target, entry)
        }
    }

    @Test
    fun `Scheduling is refused to an owner of the target project`() {
        withOrder { target, entry ->
            target.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertFailsWith<AccessDeniedException> { scheduler.schedule(Time.now) }
            }
            assertNotScheduled(target, entry)
        }
    }

    @Test
    fun `The scheduling job still schedules the orders, run without any user`() {
        withOrder { target, entry ->
            val registration = schedulerJob.startingJobs.first()
            asAnonymous {
                // As the job scheduler runs it
                jobDecorator.decorate(registration.job) {
                    registration.job.task.run(JobRunListener.out())
                }()
            }
            assertScheduled(target, entry)
        }
    }

}
