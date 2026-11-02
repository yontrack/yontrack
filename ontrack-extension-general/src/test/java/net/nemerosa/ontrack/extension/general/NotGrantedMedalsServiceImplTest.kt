package net.nemerosa.ontrack.extension.general

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotGrantedMedalsServiceImplTest {

    private val project = Project.of(nd("P", "")).withId(ID.of(1))
    private val branch = Branch.of(project, nd("B", "")).withId(ID.of(1))

    private lateinit var propertyService: PropertyService
    private lateinit var cachedSettingsService: CachedSettingsService
    private lateinit var service: NotGrantedMedalsServiceImpl

    @BeforeEach
    fun setup() {
        propertyService = mockk()
        every {
            propertyService.getPropertyValue(any(), NotGrantedMedalsPropertyType::class.java)
        } returns null
        cachedSettingsService = mockk()
        settings(false)
        service = NotGrantedMedalsServiceImpl(propertyService, cachedSettingsService)
    }

    private fun settings(enabled: Boolean) {
        every {
            cachedSettingsService.getCachedSettings(NotGrantedMedalsSettings::class.java)
        } returns NotGrantedMedalsSettings(enabled = enabled)
    }

    private fun property(entity: ProjectEntity, enabled: Boolean) {
        every {
            propertyService.getPropertyValue(entity, NotGrantedMedalsPropertyType::class.java)
        } returns NotGrantedMedalsProperty(enabled = enabled)
    }

    private fun resolve() = service.isEnabled(branch)

    @Test
    fun `Off when nothing is set anywhere`() {
        assertEquals(false, resolve())
    }

    @Test
    fun `The global settings turn the medals on`() {
        settings(true)
        assertEquals(true, resolve())
    }

    @Test
    fun `The project turns the medals on when the global settings have them off`() {
        property(project, true)
        assertEquals(true, resolve())
    }

    @Test
    fun `The project turns the medals off when the global settings have them on`() {
        settings(true)
        property(project, false)
        assertEquals(false, resolve())
    }

    @Test
    fun `The branch turns the medals on when the project has them off`() {
        property(project, false)
        property(branch, true)
        assertEquals(true, resolve())
    }

    @Test
    fun `The branch turns the medals off when the project has them on`() {
        property(project, true)
        property(branch, false)
        assertEquals(false, resolve())
    }

    @Test
    fun `The branch turns the medals off when the global settings have them on`() {
        settings(true)
        property(branch, false)
        assertEquals(false, resolve())
    }

}
