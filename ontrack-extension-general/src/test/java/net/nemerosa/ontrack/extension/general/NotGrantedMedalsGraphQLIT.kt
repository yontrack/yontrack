package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotGrantedMedalsGraphQLIT : AbstractQLKTITSupport() {

    /**
     * Read with the view of the project only: no `GlobalSettings`, which is why this field exists.
     */
    private fun Branch.notGrantedMedals(): Boolean =
        asUserWithView(this).call {
            run("""{ branches(id: $id) { notGrantedMedals } }""")
        }.path("branches").path(0).path("notGrantedMedals").booleanValue()

    private fun setting(enabled: Boolean) {
        settingsManagerService.saveSettings(NotGrantedMedalsSettings(enabled = enabled))
    }

    @Test
    fun `Off when the global settings have them off, and nothing else is set`() {
        asAdmin {
            withSettings<NotGrantedMedalsSettings> {
                setting(false)
                project {
                    branch {
                        assertEquals(false, notGrantedMedals())
                    }
                }
            }
        }
    }

    @Test
    fun `The global settings are read without being granted them`() {
        asAdmin {
            withSettings<NotGrantedMedalsSettings> {
                setting(true)
                project {
                    branch {
                        assertEquals(true, notGrantedMedals())
                    }
                }
            }
        }
    }

    @Test
    fun `The project overrides the global settings, and the branch the project`() {
        asAdmin {
            withSettings<NotGrantedMedalsSettings> {
                setting(false)
                project {
                    setProperty(this, NotGrantedMedalsPropertyType::class.java, NotGrantedMedalsProperty(enabled = true))
                    branch {
                        assertEquals(true, notGrantedMedals())
                        setProperty(this, NotGrantedMedalsPropertyType::class.java, NotGrantedMedalsProperty(enabled = false))
                        assertEquals(false, notGrantedMedals())
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting the property returns to inheriting from the next level`() {
        asAdmin {
            withSettings<NotGrantedMedalsSettings> {
                setting(false)
                project {
                    setProperty(this, NotGrantedMedalsPropertyType::class.java, NotGrantedMedalsProperty(enabled = true))
                    branch {
                        setProperty(this, NotGrantedMedalsPropertyType::class.java, NotGrantedMedalsProperty(enabled = false))
                        assertEquals(false, notGrantedMedals())
                        deleteProperty(this, NotGrantedMedalsPropertyType::class.java)
                        assertEquals(true, notGrantedMedals())
                    }
                }
            }
        }
    }

    @Test
    fun `Setting the property through its mutation`() {
        asAdmin {
            project {
                branch {
                    run(
                        """
                            mutation {
                                setBranchNotGrantedMedalsPropertyById(input: {id: $id, enabled: true}) {
                                    errors {
                                        message
                                    }
                                }
                            }
                        """
                    ) { data ->
                        assertNoUserError(data, "setBranchNotGrantedMedalsPropertyById")
                    }
                    assertEquals(
                        NotGrantedMedalsProperty(enabled = true),
                        getProperty(this, NotGrantedMedalsPropertyType::class.java),
                    )
                }
            }
        }
    }

}
