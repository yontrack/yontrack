package net.nemerosa.ontrack.service

import com.github.benmanes.caffeine.cache.Caffeine
import net.nemerosa.ontrack.common.Caches
import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig(
        private val cacheConfigProperties: CacheConfigProperties,
        private val cacheConfigExtensions: List<CacheConfigExtension>
) {
    @Bean
    fun cacheManager(): CacheManager {
        val manager = SimpleCacheManager()

        manager.setCaches(
                // Built in caches
                listOf(
                        // Cache for settings, one entry per settings type
                        CaffeineCache(
                                Caches.SETTINGS,
                                Caffeine.newBuilder()
                                        .maximumSize(SETTINGS_CACHE_SIZE)
                                        .expireAfterWrite(10, TimeUnit.HOURS)
                                        .recordStats()
                                        .build()
                        )
                ) + cacheConfigExtensions.flatMap {
                    it.caches.map { (name, spec) -> toCache(name, spec) }
                }
        )

        return manager
    }

    private fun toCache(name: String, defaultSpec: String) = CaffeineCache(
            name,
            Caffeine.from(
                    cacheConfigProperties.specs[name] ?: defaultSpec
            ).build()
    )

    companion object {
        /**
         * Above the number of settings types (one per `SettingsProvider`), so that reading
         * some settings never evicts others.
         */
        private const val SETTINGS_CACHE_SIZE = 1_000L
    }

}