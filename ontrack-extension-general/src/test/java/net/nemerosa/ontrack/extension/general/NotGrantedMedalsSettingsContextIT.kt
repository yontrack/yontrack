package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotGrantedMedalsSettingsContextIT : AbstractCascTestSupport() {

    @Test
    fun `Not-granted medals settings as CasC`() {
        asAdmin {
            withSettings<NotGrantedMedalsSettings> {
                settingsManagerService.saveSettings(NotGrantedMedalsSettings(enabled = false))
                assertFalse(cachedSettingsService.getCachedSettings(NotGrantedMedalsSettings::class.java).enabled)
                casc("""
                    ontrack:
                        config:
                            settings:
                                not-granted-medals:
                                    enabled: true
                """.trimIndent())
                assertTrue(cachedSettingsService.getCachedSettings(NotGrantedMedalsSettings::class.java).enabled)
            }
        }
    }

}
