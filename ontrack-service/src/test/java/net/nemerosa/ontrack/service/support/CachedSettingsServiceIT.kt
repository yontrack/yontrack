package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.model.settings.SecuritySettings
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
 * The settings are cached for all the threads, which read them from the database: a read from another
 * thread while some settings are being written puts their committed value into the cache, which must
 * neither hide the new value from the transaction which writes them, nor stay in the cache after its
 * commit.
 */
class CachedSettingsServiceIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    @Test
    fun `Settings written by a transaction are read by it, whatever another thread reads meanwhile`() {
        val settings = cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
        asAdmin().execute {
            settingsManagerService.saveSettings(settings.withDashboardSharing(!settings.grantDashboardSharingToAll))
        }
        assertEquals(
            settings.grantDashboardSharingToAll,
            readFromAnotherThread().grantDashboardSharingToAll,
            "Not committed yet"
        )
        assertEquals(
            !settings.grantDashboardSharingToAll,
            cachedSettingsService.getCachedSettings(SecuritySettings::class.java).grantDashboardSharingToAll,
            "Written by this transaction"
        )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A concurrent read before the commit of some settings does not leave their previous value in the cache`() {
        val settings = cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
        try {
            TransactionTemplate(platformTransactionManager).executeWithoutResult {
                asAdmin().execute {
                    settingsManagerService.saveSettings(settings.withDashboardSharing(!settings.grantDashboardSharingToAll))
                }
                assertEquals(
                    settings.grantDashboardSharingToAll,
                    readFromAnotherThread().grantDashboardSharingToAll,
                    "Not committed yet"
                )
            }
            assertEquals(
                !settings.grantDashboardSharingToAll,
                cachedSettingsService.getCachedSettings(SecuritySettings::class.java).grantDashboardSharingToAll,
                "Committed value"
            )
        } finally {
            asAdmin().execute {
                settingsManagerService.saveSettings(settings)
            }
        }
    }

    private fun readFromAnotherThread(): SecuritySettings {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return executor.submit<SecuritySettings> {
                cachedSettingsService.getCachedSettings(SecuritySettings::class.java)
            }.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
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
