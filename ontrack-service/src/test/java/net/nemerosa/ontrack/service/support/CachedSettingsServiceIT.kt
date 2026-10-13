package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.it.RecordedQueries
import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.model.support.ReadMemo
import net.nemerosa.ontrack.model.support.SettingsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * The settings are read once per unit of work - an HTTP request, a queue message - and fresh
 * outside of one, never from a cache which would outlive it: what another replica writes is read
 * by the next unit of this one (#2080).
 */
class CachedSettingsServiceIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var settingsRepository: SettingsRepository

    /**
     * Queries of the current thread reading the settings of the given [type]
     */
    private fun RecordedQueries.settings(type: Class<*>): RecordedQueries =
        on("SETTINGS").withParameter(type.name).filter { it.thread == Thread.currentThread().name }

    private fun security(): SecuritySettings =
        cachedSettingsService.getCachedSettings(SecuritySettings::class.java)

    private fun saveSecurity(settings: SecuritySettings) {
        asAdmin().execute {
            settingsManagerService.saveSettings(settings)
        }
    }

    @Test
    fun `With no unit of work, each read of some settings goes to the database`() {
        val queries = queryCounter.record {
            security()
            security()
        }
        assertEquals(2, queries.settings(SecuritySettings::class.java).size, "One query per read: $queries")
    }

    @Test
    fun `In a unit of work, a settings type is read once, in one query`() {
        val queries = queryCounter.record {
            ReadMemo.unitOfWork {
                security()
                security()
                cachedSettingsService.getCachedSettings(EventsSettings::class.java)
                cachedSettingsService.getCachedSettings(EventsSettings::class.java)
            }
        }
        assertEquals(1, queries.settings(SecuritySettings::class.java).size, "Security settings: $queries")
        assertEquals(1, queries.settings(EventsSettings::class.java).size, "Events settings: $queries")
    }

    @Test
    fun `Each unit of work reads the settings again`() {
        val queries = queryCounter.record {
            ReadMemo.unitOfWork { security() }
            ReadMemo.unitOfWork { security() }
        }
        assertEquals(2, queries.settings(SecuritySettings::class.java).size, "One query per unit: $queries")
    }

    @Test
    fun `Settings written and read in the same unit of work are read with their new value`() {
        ReadMemo.unitOfWork {
            val settings = security()
            saveSecurity(settings.withDashboardSharing(!settings.grantDashboardSharingToAll))
            assertEquals(!settings.grantDashboardSharingToAll, security().grantDashboardSharingToAll)
        }
    }

    @Test
    fun `Settings written outside a unit of work are read with their new value`() {
        val settings = security()
        saveSecurity(settings.withDashboardSharing(!settings.grantDashboardSharingToAll))
        assertEquals(!settings.grantDashboardSharingToAll, security().grantDashboardSharingToAll)
    }

    /**
     * Another replica writes directly into the database, which the unit of work of this one
     * reads the next time.
     */
    @Test
    fun `Settings written by another replica are read by the next unit of work`() {
        val settings = ReadMemo.unitOfWork { security() }
        settingsRepository.setBoolean(
            SecuritySettings::class.java,
            SecuritySettings::grantDashboardSharingToAll.name,
            !settings.grantDashboardSharingToAll,
        )
        assertEquals(
            !settings.grantDashboardSharingToAll,
            ReadMemo.unitOfWork { security() }.grantDashboardSharingToAll,
        )
    }

    @Test
    fun `A thread outside the unit of work reads fresh settings`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val queries = queryCounter.record {
                ReadMemo.unitOfWork {
                    security()
                    executor.submit { security() }.get(30, TimeUnit.SECONDS)
                    executor.submit { security() }.get(30, TimeUnit.SECONDS)
                }
            }
            assertEquals(
                3,
                queries.on("SETTINGS").withParameter(SecuritySettings::class.java.name).size,
                "Once in the unit, twice by the other thread: $queries"
            )
        } finally {
            executor.shutdown()
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Settings written by a rolled back transaction are not left in the unit of work`() {
        ReadMemo.unitOfWork {
            val settings = security()
            TransactionTemplate(platformTransactionManager).executeWithoutResult { status ->
                saveSecurity(settings.withDashboardSharing(!settings.grantDashboardSharingToAll))
                assertEquals(
                    !settings.grantDashboardSharingToAll,
                    security().grantDashboardSharingToAll,
                    "Read by the transaction which writes them"
                )
                status.setRollbackOnly()
            }
            assertEquals(
                settings.grantDashboardSharingToAll,
                security().grantDashboardSharingToAll,
                "Rolled back"
            )
        }
    }

    private fun SecuritySettings.withDashboardSharing(grantDashboardSharingToAll: Boolean) = SecuritySettings(
        isGrantProjectViewToAll = isGrantProjectViewToAll,
        isGrantProjectParticipationToAll = isGrantProjectParticipationToAll,
        builtInAuthenticationEnabled = builtInAuthenticationEnabled,
        grantDashboardEditionToAll = grantDashboardEditionToAll,
        grantDashboardSharingToAll = grantDashboardSharingToAll,
    )

}
