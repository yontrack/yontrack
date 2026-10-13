package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.ReadMemo
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The settings are read through the [ReadMemo] of the unit of work: once per HTTP request or queue
 * message, and from the database on every read outside of one, as in a job (ADR 0021).
 *
 * Some settings being written are evicted from the memo, so that the unit of work reads them again,
 * as written by its transaction - and once more when this transaction is over, so that a rollback
 * does not leave the values it wrote in the memo.
 */
@Service
class CachedSettingsServiceImpl(
    settingsProviders: Collection<SettingsProvider<*>>,
) : CachedSettingsService {

    private val settingsProviders: Map<Class<*>, SettingsProvider<*>> =
        settingsProviders.associateBy { it.settingsClass }

    override fun <T> getCachedSettings(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReadMemo.read(SETTINGS, type) { load(type) } as T
    }

    override fun <T> invalidate(type: Class<T>) {
        ReadMemo.evict(SETTINGS, type)
        if (ReadMemo.current() != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    ReadMemo.evict(SETTINGS, type)
                }
            })
        }
    }

    private fun <T> load(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        val settingsProvider = settingsProviders[type] as SettingsProvider<T>?
            ?: throw IllegalStateException("No settings provider for ${type.name}")
        return settingsProvider.settings
    }

    companion object {
        /**
         * Settings by type, in the read memo
         */
        private val SETTINGS = ReadMemo.Region<Class<*>, Any?>("settings")
    }
}
