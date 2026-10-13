package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import kotlin.test.assertEquals

/**
 * Several replicas run at once: the settings and the properties are read once per unit of work,
 * never cached across requests (#2080, #2081, ADR 0021).
 */
class CacheConfigIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Test
    fun `Neither the settings nor the properties are cached`() {
        assertEquals(
            emptyList(),
            cacheManager.cacheNames.filter { it.lowercase() in setOf("settings", "properties") },
        )
    }

}
