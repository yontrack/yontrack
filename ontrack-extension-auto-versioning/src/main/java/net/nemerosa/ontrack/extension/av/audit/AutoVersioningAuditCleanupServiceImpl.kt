package net.nemerosa.ontrack.extension.av.audit

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AutoVersioningAuditCleanupServiceImpl(
    private val cachedSettingsService: CachedSettingsService,
    private val store: AutoVersioningAuditStore,
    private val securityService: SecurityService,
) : AutoVersioningAuditCleanupService {

    override fun cleanup(): AutoVersioningAuditCleanupResult {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        val settings = cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java)
        // Non running
        val retentionDuration = settings.auditRetentionDuration
        val retentionDate = Time.now().minus(retentionDuration)
        val nonRunningCleanup = store.removeAllBefore(retentionDate, nonRunningOnly = true)
        // Any other state
        val cleanupDuration = settings.auditCleanupDuration
        val cleanupDate = retentionDate.minus(cleanupDuration)
        val anyStateCleanup = store.removeAllBefore(cleanupDate, nonRunningOnly = false)
        // Summary
        return AutoVersioningAuditCleanupResult(
            nonRunning = nonRunningCleanup,
            anyState = anyStateCleanup
        )
    }

    override fun purge() {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        store.removeAll()
    }
}