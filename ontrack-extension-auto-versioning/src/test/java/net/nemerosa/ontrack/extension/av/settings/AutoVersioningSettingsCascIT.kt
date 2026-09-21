package net.nemerosa.ontrack.extension.av.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals

class AutoVersioningSettingsCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var autoVersioningSettingsCasc: AutoVersioningSettingsCasc

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = autoVersioningSettingsCasc.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "title": "AutoVersioningSettings",
                  "properties": {
                    "auditCleanupDuration": {
                      "description": "Maximum time to keep audit entries for all kinds of auto versioning requests (counted _after_ the audit retention)",
                      "type": "string",
                      "pattern": "^\\d+|P(?:\\d+Y)?(?:\\d+M)?(?:\\d+D)?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+S)?)?|(\\d+)([smhdwMy]|ms)${'$'}"
                    },
                    "auditRetentionDuration": {
                      "description": "Maximum time to keep audit entries for non-running auto versioning requests",
                      "type": "string",
                      "pattern": "^\\d+|P(?:\\d+Y)?(?:\\d+M)?(?:\\d+D)?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+S)?)?|(\\d+)([smhdwMy]|ms)${'$'}"
                    },
                    "buildLinks": {
                      "description": "Creation of the build link on auto version check",
                      "type": "boolean"
                    },
                    "enabled": {
                      "description": "The \"Auto versioning on promotion\" feature is enabled only if this flag is set to `true`.",
                      "type": "boolean"
                    }
                  },
                  "required": [],
                  "additionalProperties": false,
                  "type": "object"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `Minimal set of parameters`() {
        asAdmin {
            withCleanSettings<AutoVersioningSettings> {
                casc(
                    """
                    ontrack:
                        config:
                            settings:
                                auto-versioning:
                                    enabled: true
                """.trimIndent()
                )
                val settings = cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java)
                assertEquals(true, settings.enabled)
                assertEquals(Duration.ofDays(14), settings.auditRetentionDuration)
                assertEquals(Duration.ofDays(90), settings.auditCleanupDuration)
                assertEquals(true, settings.buildLinks)
            }
        }
    }

    @Test
    fun `Complete set of parameters`() {
        asAdmin {
            withCleanSettings<AutoVersioningSettings> {
                casc(
                    """
                    ontrack:
                        config:
                            settings:
                                auto-versioning:
                                    enabled: true
                                    auditRetentionDuration: 30d
                                    auditCleanupDuration: 120d
                                    buildLinks: false
                """.trimIndent()
                )
                val settings = cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java)
                assertEquals(true, settings.enabled)
                assertEquals(Duration.ofDays(30), settings.auditRetentionDuration)
                assertEquals(Duration.ofDays(120), settings.auditCleanupDuration)
                assertEquals(false, settings.buildLinks)
            }
        }
    }

    @Test
    fun `Validation of auto-versioning settings with valid durations`() {
        assertValidYaml(
            """
                ontrack:
                  config:
                    settings:
                      auto-versioning:
                        enabled: true
                        buildLinks: true
                        auditCleanupDuration: 15d
                        auditRetentionDuration: "2592000" # second = 30 days
            """.trimIndent()
        )
    }

    @Test
    fun `Validation of auto-versioning settings with invalid durations`() {
        assertInvalidYaml(
            yamlSource = """
                ontrack:
                  config:
                    settings:
                      auto-versioning:
                        enabled: true
                        buildLinks: true
                        auditCleanupDuration: xxxx
                        auditRetentionDuration: 30d
            """.trimIndent(),
            message = "/ontrack/config/settings/auto-versioning/auditCleanupDuration: does not match the regex pattern ^\\d+|P(?:\\d+Y)?(?:\\d+M)?(?:\\d+D)?(?:T(?:\\d+H)?(?:\\d+M)?(?:\\d+S)?)?|(\\d+)([smhdwMy]|ms)\$"
        )
    }

}