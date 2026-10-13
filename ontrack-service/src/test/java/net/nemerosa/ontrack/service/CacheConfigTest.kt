package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Configuration of the caches declared by the extensions. The core declares none: the settings and
 * the properties are read once per unit of work (#2080, #2081), see `CacheConfigIT`.
 */
class CacheConfigTest {

    private fun specs(
        overrides: Map<String, String> = emptyMap(),
        extensions: List<CacheConfigExtension>,
    ) = CacheConfig(
        CacheConfigProperties().apply { specs.putAll(overrides) },
        extensions,
    ).specs

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
            specs(extensions = listOf(extension))["test"],
        )
    }

}
