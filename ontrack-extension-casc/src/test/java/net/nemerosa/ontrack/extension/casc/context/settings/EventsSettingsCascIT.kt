package net.nemerosa.ontrack.extension.casc.context.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.settings.EventsSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@AsAdminTest
class EventsSettingsCascIT : AbstractCascTestSupport() {

    @Test
    fun `Events settings`() {
        withSettings<EventsSettings> {
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                events:
                                    retentionDays: 365
                """.trimIndent()
            )
            val settings = cachedSettingsService.getCachedSettings(EventsSettings::class.java)
            assertEquals(365, settings.retentionDays)
        }
    }

    @Test
    fun `A negative retention is rejected`() {
        withSettings<EventsSettings> {
            assertFailsWith<InputException> {
                casc(
                    """
                        ontrack:
                            config:
                                settings:
                                    events:
                                        retentionDays: -1
                    """.trimIndent()
                )
            }
        }
    }

}
