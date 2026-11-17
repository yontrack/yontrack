package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpNotificationSettingsIT : AbstractDSLTestSupport() {

    @Test
    fun `Default settings`() {
        withCleanSettings<HttpNotificationSettings> {
            val settings = settingsService.getCachedSettings(HttpNotificationSettings::class.java)
            assertEquals(3600, settings.maxTimeoutSeconds)
            assertEquals(65536, settings.maxResponseBytes)
            assertEquals(30, settings.requestTimeoutSeconds)
        }
    }

    @Test
    fun `Saving the settings`() {
        asAdmin {
            withCleanSettings<HttpNotificationSettings> {
                settingsManagerService.saveSettings(
                    HttpNotificationSettings(
                        maxTimeoutSeconds = 600,
                        maxResponseBytes = 1024,
                        requestTimeoutSeconds = 5,
                    )
                )
                val settings = settingsService.getCachedSettings(HttpNotificationSettings::class.java)
                assertEquals(
                    HttpNotificationSettings(
                        maxTimeoutSeconds = 600,
                        maxResponseBytes = 1024,
                        requestTimeoutSeconds = 5,
                    ),
                    settings
                )
            }
        }
    }

    @Test
    fun `The maximum timeout must be strictly positive`() {
        assertRejected(HttpNotificationSettings(maxTimeoutSeconds = 0))
    }

    @Test
    fun `The maximum response size must be strictly positive`() {
        assertRejected(HttpNotificationSettings(maxResponseBytes = 0))
    }

    @Test
    fun `The request timeout must be strictly positive`() {
        assertRejected(HttpNotificationSettings(requestTimeoutSeconds = -1))
    }

    private fun assertRejected(invalid: HttpNotificationSettings) {
        asAdmin {
            withCleanSettings<HttpNotificationSettings> {
                assertFailsWith<HttpNotificationSettingsException> {
                    settingsManagerService.saveSettings(invalid)
                }
                // Nothing was saved
                assertEquals(
                    HttpNotificationSettings(),
                    settingsService.getCachedSettings(HttpNotificationSettings::class.java)
                )
            }
        }
    }

}
