package net.nemerosa.ontrack.extension.config.graphql

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.general.MessagePropertyType
import net.nemerosa.ontrack.extension.general.NotGrantedMedalsProperty
import net.nemerosa.ontrack.extension.general.NotGrantedMedalsPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The not-granted medals in the CI configuration, by the alias and by the class name of the property,
 * each time next to another property, since a level accepts several of them.
 */
class CIConfigurationNotGrantedMedalsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    private val configuredProjectName = uid("cfg-")

    @BeforeEach
    fun init() {
        mockSCMTester.registerRepository(configuredProjectName)
    }

    private fun yaml(level: String, property: String) = """
        version: v1
        configuration:
          defaults:
            $level:
              properties:
                $property
                net.nemerosa.ontrack.extension.general.MessagePropertyType:
                  type: INFO
                  text: A message
    """.trimIndent()

    private fun assertConfigured(entity: ProjectEntity, enabled: Boolean) {
        assertEquals(
            NotGrantedMedalsProperty(enabled = enabled),
            propertyService.getPropertyValue(entity, NotGrantedMedalsPropertyType::class.java),
        )
        assertNotNull(propertyService.getPropertyValue(entity, MessagePropertyType::class.java), "The other property is set too")
    }

    private fun checkProject(property: String, enabled: Boolean) {
        configTestSupport.withConfigAndProject(
            projectName = configuredProjectName,
            yaml = yaml("project", property),
        ) { project, _ ->
            assertConfigured(project, enabled)
        }
    }

    private fun checkBranch(property: String, enabled: Boolean) {
        configTestSupport.withConfigAndBranch(
            projectName = configuredProjectName,
            yaml = yaml("branch", property),
        ) { branch, _ ->
            assertConfigured(branch, enabled)
        }
    }

    @Test
    @AsAdminTest
    fun `On the project with the alias`() {
        checkProject("notGrantedMedals: true", enabled = true)
    }

    @Test
    @AsAdminTest
    fun `On the branch with the alias`() {
        checkBranch("notGrantedMedals: false", enabled = false)
    }

    @Test
    @AsAdminTest
    fun `On the project with the class name`() {
        checkProject("net.nemerosa.ontrack.extension.general.NotGrantedMedalsPropertyType: {enabled: true}", enabled = true)
    }

    @Test
    @AsAdminTest
    fun `On the branch with the class name`() {
        checkBranch("net.nemerosa.ontrack.extension.general.NotGrantedMedalsPropertyType: {enabled: false}", enabled = false)
    }

}
