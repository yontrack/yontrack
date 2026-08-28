package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.preferences.PreferencesService
import net.nemerosa.ontrack.model.preferences.ThemeMode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class PreferencesMutationsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var preferencesService: PreferencesService

    @Test
    fun `Getting the default preferences`() {
        asUser {
            val preferences = getPreferences()
            assertEquals(false, preferences.branchViewVsNames)
            assertEquals(false, preferences.branchViewVsGroups)
            assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        }
    }

    @Test
    fun `Setting the theme mode`() {
        asUser {
            run(
                """
                mutation {
                    setPreferences(input: {
                        themeMode: DARK,
                    }) {
                        preferences {
                            themeMode
                        }
                    }
                }
            """
            ) { data ->
                val preferences = data["setPreferences"]["preferences"]
                assertEquals("DARK", preferences.getTextField("themeMode"))
                assertEquals(ThemeMode.DARK, getPreferences().themeMode)
            }
        }
    }

    @Test
    fun `Setting another preference leaves the theme mode alone`() {
        asUser {
            run(
                """
                mutation {
                    setPreferences(input: {
                        themeMode: LIGHT,
                    }) {
                        preferences {
                            themeMode
                        }
                    }
                }
            """
            )
            run(
                """
                mutation {
                    setPreferences(input: {
                        branchViewVsNames: true,
                    }) {
                        preferences {
                            themeMode
                        }
                    }
                }
            """
            ) { data ->
                val preferences = data["setPreferences"]["preferences"]
                assertEquals("LIGHT", preferences.getTextField("themeMode"))
                assertEquals(ThemeMode.LIGHT, getPreferences().themeMode)
            }
        }
    }

    @Test
    fun `Setting all the preferences`() {
        asUser {
            run(
                """
                mutation {
                    setPreferences(input: {
                        branchViewVsNames: true,
                        branchViewVsGroups: true,
                    }) {
                        preferences {
                            branchViewVsNames
                            branchViewVsGroups
                        }
                    }
                }
            """
            ) { data ->
                val preferences = data["setPreferences"]["preferences"]
                assertEquals(true, preferences.getBooleanField("branchViewVsNames"))
                assertEquals(true, preferences.getBooleanField("branchViewVsGroups"))
                getPreferences().let {
                    assertEquals(true, it.branchViewVsNames)
                    assertEquals(true, it.branchViewVsGroups)
                }
            }
        }
    }

    @Test
    fun `Setting only one preference`() {
        asUser {
            run(
                """
                mutation {
                    setPreferences(input: {
                        branchViewVsNames: true,
                    }) {
                        preferences {
                            branchViewVsNames
                            branchViewVsGroups
                        }
                    }
                }
            """
            ) { data ->
                val preferences = data["setPreferences"]["preferences"]
                assertEquals(true, preferences.getBooleanField("branchViewVsNames"))
                assertEquals(false, preferences.getBooleanField("branchViewVsGroups"))
                getPreferences().let {
                    assertEquals(true, it.branchViewVsNames)
                    assertEquals(false, it.branchViewVsGroups)
                }
            }
        }
    }

    private fun getPreferences() = preferencesService.getPreferences(
        securityService.currentUser?.account
            ?: error("Authentication is required")
    )

}