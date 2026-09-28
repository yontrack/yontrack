package net.nemerosa.ontrack.extension.scorecard.casc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.nemerosa.ontrack.extension.casc.CascConfigurationProperties
import net.nemerosa.ontrack.extension.casc.CascStartup
import net.nemerosa.ontrack.extension.casc.removed.CascRemovedKeys
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.settings.JobHistorySettings
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals

/**
 * The settings of the end-to-end promotion metrics export were removed in 6.0 with the
 * delivery metrics: a CasC file still carrying their key must not stop Yontrack from starting.
 */
class ScorecardRemovedCascKeysIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var cascStartup: CascStartup

    @Autowired
    private lateinit var cascConfigurationProperties: CascConfigurationProperties

    @Test
    fun `The removed e2e-promotion-metrics key is ignored at startup with a warning`() {
        val warnings = captureWarnings {
            withSettings<JobHistorySettings> {
                withCascLocations("classpath:casc/removed/e2e-promotion-metrics.yaml") {
                    // Does not fail
                    cascStartup.start()
                }
                // The rest of the CasC file has been applied
                val settings = cachedSettingsService.getCachedSettings(JobHistorySettings::class.java)
                assertEquals(Duration.ofDays(45), settings.retention)
            }
        }
        assertEquals(
            listOf(
                "CasC key ontrack/config/settings/e2e-promotion-metrics is ignored: it was removed in 6.0."
            ),
            warnings,
        )
    }

    private fun withCascLocations(vararg locations: String, code: () -> Unit) {
        val old = cascConfigurationProperties.locations
        cascConfigurationProperties.locations = locations.toList()
        try {
            code()
        } finally {
            cascConfigurationProperties.locations = old
        }
    }

    private fun captureWarnings(code: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(CascRemovedKeys::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            code()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

}
