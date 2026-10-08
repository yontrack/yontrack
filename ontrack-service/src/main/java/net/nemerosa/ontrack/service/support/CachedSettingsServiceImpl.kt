package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.common.Caches
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.core.Ordered
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The settings are cached for all the threads, and loaded from the database by the first one which
 * reads them. Some settings being written by a transaction are invalidated, which happens before its
 * commit: a read by another thread meanwhile puts their committed value back into the cache, which
 * must neither be read by this transaction, nor stay in the cache after it. So:
 *
 * - the transaction which writes some settings reads them from the database, never from the cache,
 *   until it is over;
 * - their entry is evicted again once the transaction is over: after its commit, before any other
 *   reaction to it, and after its completion, for a rollback;
 * - the loading of some settings is synchronized, so that their eviction waits for a load in
 *   progress, which started before the commit.
 */
@Service
class CachedSettingsServiceImpl(
    settingsProviders: Collection<SettingsProvider<*>>,
    private val cacheManager: ObjectProvider<CacheManager>,
) : CachedSettingsService {

    private val settingsProviders: Map<Class<*>, SettingsProvider<*>> =
        settingsProviders.associateBy { it.settingsClass }

    private val cache: Cache by lazy {
        cacheManager.getObject().getCache(Caches.SETTINGS) ?: error("No cache for the settings")
    }

    override fun <T> getCachedSettings(type: Class<T>): T =
        if (writtenByTransaction(type)) {
            load(type)
        } else {
            @Suppress("UNCHECKED_CAST")
            cache.get(type) { load(type) as Any } as T
        }

    override fun <T> invalidate(type: Class<T>) {
        cache.evict(type)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            val written = TransactionSynchronizationManager.getResource(this) as WrittenSettings?
                ?: WrittenSettings().apply {
                    TransactionSynchronizationManager.bindResource(this@CachedSettingsServiceImpl, this)
                    TransactionSynchronizationManager.registerSynchronization(this)
                }
            written.types += type
        }
    }

    private fun writtenByTransaction(type: Class<*>): Boolean =
        (TransactionSynchronizationManager.getResource(this) as WrittenSettings?)?.types?.contains(type) == true

    private fun <T> load(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        val settingsProvider = settingsProviders[type] as SettingsProvider<T>
        return settingsProvider.settings
    }

    /**
     * Settings written by the current transaction, bound to it while it is active.
     */
    private inner class WrittenSettings : TransactionSynchronization {

        val types = mutableSetOf<Class<*>>()

        override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

        override fun suspend() {
            TransactionSynchronizationManager.unbindResource(this@CachedSettingsServiceImpl)
        }

        override fun resume() {
            TransactionSynchronizationManager.bindResource(this@CachedSettingsServiceImpl, this)
        }

        override fun afterCommit() {
            evict()
        }

        override fun afterCompletion(status: Int) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this@CachedSettingsServiceImpl)
            evict()
        }

        private fun evict() {
            types.forEach { cache.evict(it) }
        }
    }
}
