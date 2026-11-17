package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpNotificationSettingsCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var httpNotificationSettingsCascContext: HttpNotificationSettingsCascContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = httpNotificationSettingsCascContext.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "title": "HttpNotificationSettings",
                  "properties": {
                    "maxResponseBytes": {
                      "description": "Maximum size (in bytes) of a response body: a longer body is truncated",
                      "type": "integer"
                    },
                    "maxTimeoutSeconds": {
                      "description": "Maximum timeout (in seconds) a subscription may set on an HTTP call",
                      "type": "integer"
                    },
                    "requestTimeoutSeconds": {
                      "description": "Timeout (in seconds) of a single request, for both the connection and the reading of the response",
                      "type": "integer"
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
    fun `HTTP notifications settings using CasC`() {
        withCleanSettings<HttpNotificationSettings> {
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                http-notifications:
                                    maxTimeoutSeconds: 7200
                                    maxResponseBytes: 131072
                                    requestTimeoutSeconds: 10
                """.trimIndent()
            )
            val settings = settingsService.getCachedSettings(HttpNotificationSettings::class.java)
            assertEquals(
                HttpNotificationSettings(
                    maxTimeoutSeconds = 7200,
                    maxResponseBytes = 131072,
                    requestTimeoutSeconds = 10,
                ),
                settings
            )
        }
    }

    @Test
    fun `HTTP notifications settings using CasC with defaults`() {
        withCleanSettings<HttpNotificationSettings> {
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                http-notifications:
                                    requestTimeoutSeconds: 10
                """.trimIndent()
            )
            val settings = settingsService.getCachedSettings(HttpNotificationSettings::class.java)
            assertEquals(
                HttpNotificationSettings(
                    maxTimeoutSeconds = 3600,
                    maxResponseBytes = 65536,
                    requestTimeoutSeconds = 10,
                ),
                settings
            )
        }
    }

    @Test
    fun `Invalid HTTP notifications settings are rejected by CasC`() {
        withCleanSettings<HttpNotificationSettings> {
            assertFailsWith<HttpNotificationSettingsException> {
                casc(
                    """
                        ontrack:
                            config:
                                settings:
                                    http-notifications:
                                        maxResponseBytes: 0
                    """.trimIndent()
                )
            }
        }
    }

}
