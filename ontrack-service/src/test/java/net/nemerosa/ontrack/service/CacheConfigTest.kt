package net.nemerosa.ontrack.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The caches are per instance: while two instances run side by side, as during a rolling upgrade,
 * what one of them writes is seen by the other once its entry expires.
 */
class CacheConfigTest {

    private fun specs(
        overrides: Map<String, String> = emptyMap(),
        extensions: List<CacheConfigExtension> = listOf(CoreCacheConfigExtension()),
    ) = CacheConfig(
        CacheConfigProperties().apply { specs.putAll(overrides) },
        extensions,
    ).specs

    /**
     * Time after which an entry of the cache specified by [spec] has expired, checked at the
     * given [steps].
     */
    private fun expiry(spec: String, vararg steps: Duration): Duration? {
        val time = AtomicLong()
        val cache = Caffeine.from(spec)
            .ticker(Ticker { time.get() })
            .executor(Runnable::run)
            .build<String, String>()
        cache.put("key", "value")
        return steps.firstOrNull { step ->
            time.set(step.toNanos())
            cache.getIfPresent("key") == null
        }
    }

    @Test
    fun `The settings are not cached`() {
        assertNull(
            specs().keys.firstOrNull { it.equals("settings", ignoreCase = true) },
            "The settings are read once per unit of work, never cached (#2080)"
        )
    }

    @Test
    fun `The properties expire after five minutes`() {
        val spec = assertNotNull(specs()[CoreCacheConfigExtension.PROPERTIES], "Properties cache")
        assertEquals(
            Duration.ofMinutes(5),
            expiry(spec, Duration.ofMinutes(4), Duration.ofSeconds(299), Duration.ofMinutes(5)),
        )
    }

    @Test
    fun `The expiry of the properties can be configured`() {
        val spec = assertNotNull(
            specs(overrides = mapOf("properties" to "maximumSize=10,expireAfterWrite=1h"))["properties"],
            "Properties cache"
        )
        assertNull(expiry(spec, Duration.ofMinutes(59)), "Not expired after 59 minutes")
    }

    @Test
    fun `The name of a cache is not case sensitive in its configuration`() {
        val extension = object : CacheConfigExtension {
            override val caches = mapOf("SAMPLE" to "maximumSize=1")
        }
        // An environment variable gives the key in lowercase
        assertEquals(
            "maximumSize=10",
            specs(
                overrides = mapOf("sample" to "maximumSize=10"),
                extensions = listOf(extension),
            )["SAMPLE"],
        )
    }

    @Test
    fun `The caches of the extensions are configured`() {
        val extension = object : CacheConfigExtension {
            override val caches = mapOf("test" to "maximumSize=1")
        }
        assertEquals(
            "maximumSize=1",
            specs(extensions = listOf(CoreCacheConfigExtension(), extension))["test"],
        )
    }

}
