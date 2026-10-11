package net.nemerosa.ontrack.service

import com.github.benmanes.caffeine.cache.Caffeine
import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Caches of the instance, declared by the [CacheConfigExtension]s, each one with a default
 * Caffeine specification which `ontrack.config.cache.specs.<name>` overrides.
 *
 * The caches are per instance: while two instances run side by side, as during a rolling upgrade,
 * what one of them writes is seen by the other once its entries expire.
 */
@Configuration
@EnableCaching
class CacheConfig(
    private val cacheConfigProperties: CacheConfigProperties,
    private val cacheConfigExtensions: List<CacheConfigExtension>,
) {

    /**
     * Caffeine specification of each cache, indexed by name.
     */
    val specs: Map<String, String>
        get() = cacheConfigExtensions
            .flatMap { it.caches.entries }
            .associate { (name, defaultSpec) -> name to (configuredSpec(name) ?: defaultSpec) }

    /**
     * Spec configured for the cache, whose name is not case-sensitive: an environment variable
     * gives it in lowercase.
     */
    private fun configuredSpec(name: String): String? =
        cacheConfigProperties.specs.entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

    @Bean
    fun cacheManager(): CacheManager {
        val manager = SimpleCacheManager()
        manager.setCaches(
            specs.map { (name, spec) ->
                CaffeineCache(name, Caffeine.from(spec).build())
            }
        )
        return manager
    }

}
