package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.ui.AbstractWebTestSupport
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.model.settings.SettingsProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every settings type, of the core or of an extension, is loaded in one query, whatever its number
 * of fields (#2080).
 */
class SettingsProvidersIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var settingsProviders: List<SettingsProvider<*>>

    @Test
    fun `The settings of the core and of the extensions are all checked`() {
        val types = settingsProviders.map { it.settingsClass.name }
        assertTrue(SecuritySettings::class.java.name in types, "Security settings")
        assertTrue(
            "net.nemerosa.ontrack.extension.github.ingestion.settings.GitHubIngestionSettings" in types,
            "GitHub ingestion settings"
        )
    }

    @Test
    fun `Each settings type is loaded in one query`() {
        val counts = settingsProviders.associate { provider ->
            val queries = queryCounter.record { provider.settings }
                .on("SETTINGS")
                .filter { it.thread == Thread.currentThread().name }
            provider.settingsClass.simpleName to queries.size
        }
        assertEquals(
            emptyMap(),
            counts.filterValues { it != 1 },
            "Settings types not loaded in one query"
        )
    }
}
