package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditCleanupJob
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditCleanupService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.audit.withSignatureDaysOlder
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.job.JobDecorator
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cleaning up and purging the auto-versioning audit entries need the global `ApplicationManagement`
 * function, while the cleanup job keeps running without any user.
 */
class AutoVersioningAuditCleanupPermissionIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var cleanupService: AutoVersioningAuditCleanupService

    @Autowired
    private lateinit var cleanupJob: AutoVersioningAuditCleanupJob

    @Autowired
    private lateinit var jobDecorator: JobDecorator

    @Autowired
    private lateinit var auditService: AutoVersioningAuditService

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    private lateinit var oldSettings: AutoVersioningSettings

    @BeforeEach
    fun init() {
        asAdmin {
            oldSettings = settingsService.getCachedSettings(AutoVersioningSettings::class.java)
            settingsManagerService.saveSettings(
                AutoVersioningSettings(
                    enabled = true,
                    auditRetentionDuration = Duration.ofDays(7),
                    auditCleanupDuration = Duration.ofDays(20),
                )
            )
        }
    }

    @AfterEach
    fun restore() {
        asAdmin {
            settingsManagerService.saveSettings(oldSettings)
        }
    }

    /**
     * A stopped order, 14 days old: past the retention period, removed by a cleanup.
     */
    private fun withOldOrder(code: (target: Branch, entry: AutoVersioningAuditEntry) -> Unit) {
        asAdmin {
            val target = project<Branch> { branch() }
            val order = store.withSignatureDaysOlder(securityService, 14) {
                target.createOrder(sourceProject = "source").apply {
                    auditService.onCreated(this)
                    auditService.onScheduled(this, "routing")
                    auditService.onReceived(this, "queue")
                    auditService.onPRMerged(this, "branch", "#1", "uri:1")
                }
            }
            code(target, store.findByUUID(target, order.uuid)!!)
        }
    }

    private fun assertRemoved(target: Branch, entry: AutoVersioningAuditEntry) {
        assertNull(asAdmin { store.findByUUID(target, entry.order.uuid) }, "The entry is removed")
    }

    private fun assertKept(target: Branch, entry: AutoVersioningAuditEntry) {
        assertNotNull(asAdmin { store.findByUUID(target, entry.order.uuid) }, "The entry is kept")
    }

    private fun assertRefused(code: () -> Unit) {
        val ex = assertFailsWith<AccessDeniedException> { code() }
        assertEquals("Global function 'ApplicationManagement' is not granted.", ex.message)
    }

    @Test
    fun `Cleanup is allowed with the application management`() {
        withOldOrder { target, entry ->
            asUser().with(ApplicationManagement::class.java).call {
                cleanupService.cleanup()
            }
            assertRemoved(target, entry)
        }
    }

    @Test
    fun `Cleanup is refused to the automation`() {
        withOldOrder { target, entry ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                assertRefused { cleanupService.cleanup() }
            }
            assertKept(target, entry)
        }
    }

    @Test
    fun `Cleanup is refused to an owner of the target project`() {
        withOldOrder { target, entry ->
            target.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertRefused { cleanupService.cleanup() }
            }
            assertKept(target, entry)
        }
    }

    @Test
    fun `The cleanup job still cleans up the entries, run without any user`() {
        withOldOrder { target, entry ->
            val registration = cleanupJob.startingJobs.first()
            asAnonymous {
                // As the job scheduler runs it
                jobDecorator.decorate(registration.job) {
                    registration.job.task.run(JobRunListener.out())
                }()
            }
            assertRemoved(target, entry)
        }
    }

    @Test
    fun `Purge is allowed with the application management`() {
        withOldOrder { target, entry ->
            asUser().with(ApplicationManagement::class.java).call {
                cleanupService.purge()
            }
            assertRemoved(target, entry)
        }
    }

    @Test
    fun `Purge is refused to the automation`() {
        withOldOrder { target, entry ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                assertRefused { cleanupService.purge() }
            }
            assertKept(target, entry)
        }
    }

    @Test
    fun `Purge is refused to an owner of the target project`() {
        withOldOrder { target, entry ->
            target.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertRefused { cleanupService.purge() }
            }
            assertKept(target, entry)
        }
    }

}
