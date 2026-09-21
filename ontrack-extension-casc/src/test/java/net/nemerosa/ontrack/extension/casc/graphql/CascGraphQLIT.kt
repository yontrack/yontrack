package net.nemerosa.ontrack.extension.casc.graphql

import net.nemerosa.ontrack.extension.casc.CascConfigurationProperties
import net.nemerosa.ontrack.extension.casc.support.SampleSettings
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CascGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var cascConfigurationProperties: CascConfigurationProperties

    @Test
    fun `Accessing the Casc schema`() {
        asAdmin {
            run(
                """
                {
                    casc {
                        schema
                    }
                }
            """
            ).let { data ->
                val schema = data.path("casc").path("schema")
                assertJsonNotNull(schema)
            }
        }
    }

    @Test
    fun `Accessing the Casc resources`() {
        cascConfigurationProperties.apply {
            locations = listOf(
                "classpath:casc/settings-security.yaml",
                "classpath:casc/settings-home-page.yaml",
            )
        }
        asAdmin {
            run(
                """
                {
                    casc {
                        locations
                    }
                }
            """
            ).let { data ->
                val locations = data.path("casc").path("locations").values().map { it.asText() }
                assertEquals(
                    listOf(
                        "classpath:casc/settings-security.yaml",
                        "classpath:casc/settings-home-page.yaml",
                    ),
                    locations
                )
            }
        }
    }

    @Test
    fun `YAML rendering`() {
        asAdmin {
            withSettings<SampleSettings> {
                settingsManagerService.saveSettings(
                    SampleSettings(
                        maxProjects = 200,
                        enabled = true,
                    )
                )
                run(
                    """
                    {
                        casc {
                            yaml
                        }
                    }
                """
                ).let { data ->
                    val yaml = data.path("casc").path("yaml").asText()
                    assertTrue("maxProjects: 200" in yaml)
                    assertTrue("enabled: true" in yaml)
                }
            }
        }
    }

    @Test
    fun `Rendering as JSON`() {
        asAdmin {
            withSettings<SampleSettings> {
                settingsManagerService.saveSettings(
                    SampleSettings(
                        maxProjects = 200,
                        enabled = true,
                    )
                )
                run(
                    """
                    {
                        casc {
                            json
                        }
                    }
                """
                ).let { data ->
                    val json = data.path("casc").path("json")
                    assertEquals(
                        200,
                        json.path("ontrack").path("config").path("settings").path("sample").path("maxProjects").asInt()
                    )
                    assertEquals(
                        true,
                        json.path("ontrack").path("config").path("settings").path("sample").path("enabled").asBoolean()
                    )
                }
            }
        }
    }

    @Test
    fun `Reloading the configuration as code`() {
        asAdmin {
            withSettings<SampleSettings> {
                // Configuration
                cascConfigurationProperties.locations = listOf(
                    "classpath:casc/settings-sample.yaml",
                )
                // Initial values
                settingsManagerService.saveSettings(
                    SampleSettings(
                        maxProjects = 0,
                        enabled = false,
                    )
                )
                // Reloading
                val data = run(
                    """
                        mutation {
                            reloadCasc {
                                errors {
                                    message
                                }
                            }
                        }
                    """
                )
                // Checks there are no errors
                assertNoUserError(data, "reloadCasc")
                // Checks the values have been updated
                val settings = cachedSettingsService.getCachedSettings(SampleSettings::class.java)
                assertEquals(200, settings.maxProjects)
                assertEquals(true, settings.enabled)
            }
        }
    }
}