package net.nemerosa.ontrack.extension.av.config

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures
import net.nemerosa.ontrack.test.assertJsonNotNull
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class AutoVersioningGQLBranchFieldContributorIT : AbstractAutoVersioningTestSupport() {

    @Test
    fun `Getting the auto versioning configuration for a non configured branch`() {
        asAdmin {
            project {
                branch {
                    run(
                        """{
                        branches(id: $id) {
                            autoVersioningConfig {
                                configurations {
                                    sourceProject
                                }
                            }
                        }
                    }"""
                    ) { data ->
                        val autoVersioning = data.path("branches").path(0).path("autoVersioningConfig")
                        assertJsonNull(autoVersioning, "No auto versioning returned")
                    }
                }
            }
        }
    }

    @Test
    fun `Getting the auto versioning configuration for a configured branch`() {
        asAdmin {
            project {
                branch {
                    val config = AutoVersioningTestFixtures.sampleConfig()
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)
                    run(
                        """{
                            branches(id: $id) {
                                autoVersioningConfig {
                                    configurations {
                                        sourceProject
                                    }
                                }
                            }
                        }"""
                    ) { data ->
                        val autoVersioning = data.path("branches").path(0).path("autoVersioningConfig")
                        assertJsonNotNull(autoVersioning, "No auto versioning returned") {
                            assertEquals(
                                config.configurations.map { it.sourceProject },
                                path("configurations").values().map {
                                    it.path("sourceProject").asText()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}