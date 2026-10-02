package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScorecardSettingsCascContextIT : AbstractCascTestSupport() {

    @Test
    fun `Default settings`() {
        withSettings<ScorecardSettings> {
            settingsRepository.deleteAll(ScorecardSettings::class.java)
            cachedSettingsService.invalidate(ScorecardSettings::class.java)
            val settings = cachedSettingsService.getCachedSettings(ScorecardSettings::class.java)
            assertEquals(90, settings.windowDays)
            assertEquals(730, settings.retentionDays)
            assertEquals("0 0 2 * * *", settings.cron)
            assertEquals(7, settings.securityFreshnessDays)
        }
    }

    @Test
    fun `Scorecard settings using CasC`() {
        withSettings<ScorecardSettings> {
            settingsRepository.deleteAll(ScorecardSettings::class.java)
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                delivery-scorecard:
                                    windowDays: 30
                                    retentionDays: 365
                                    cron: "0 30 3 * * *"
                                    securityFreshnessDays: 14
                """.trimIndent()
            )
            val settings = cachedSettingsService.getCachedSettings(ScorecardSettings::class.java)
            assertEquals(30, settings.windowDays)
            assertEquals(365, settings.retentionDays)
            assertEquals("0 30 3 * * *", settings.cron)
            assertEquals(14, settings.securityFreshnessDays)
        }
    }

    @Test
    fun `Scorecard settings using CasC with defaults`() {
        withSettings<ScorecardSettings> {
            settingsRepository.deleteAll(ScorecardSettings::class.java)
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                delivery-scorecard:
                                    windowDays: 60
                """.trimIndent()
            )
            val settings = cachedSettingsService.getCachedSettings(ScorecardSettings::class.java)
            assertEquals(60, settings.windowDays)
            assertEquals(730, settings.retentionDays)
            assertEquals("0 0 2 * * *", settings.cron)
            assertEquals(7, settings.securityFreshnessDays)
        }
    }

    @Test
    fun `A security scan freshness under one day is rejected`() {
        withSettings<ScorecardSettings> {
            asAdmin {
                assertFailsWith<ScorecardSettingsException> {
                    settingsManagerService.saveSettings(ScorecardSettings(securityFreshnessDays = 0))
                }
            }
        }
    }

    @Test
    fun `Invalid cron is rejected`() {
        withSettings<ScorecardSettings> {
            asAdmin {
                assertFailsWith<ScorecardSettingsException> {
                    settingsManagerService.saveSettings(ScorecardSettings(cron = "every day"))
                }
            }
        }
    }
}
