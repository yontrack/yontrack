package net.nemerosa.ontrack.extension.config.graphql

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.general.NotGrantedMedalsProperty
import net.nemerosa.ontrack.extension.general.NotGrantedMedalsPropertyType
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * A property set to `null` in the CI configuration is deleted, and a property with no key is left alone.
 */
class CIConfigurationPropertyDeletionIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    private val configuredProjectName = uid("cfg-")

    private val medalsClassName = NotGrantedMedalsPropertyType::class.java.name
    private val releaseClassName = ReleasePropertyType::class.java.name

    @BeforeEach
    fun init() {
        mockSCMTester.registerRepository(configuredProjectName)
    }

    /**
     * The `VERSION` is left out, since it sets the release of the build at the end of each run.
     */
    private val envWithoutVersion = EnvFixtures.generic(configuredProjectName, BRANCH) - "VERSION"

    /**
     * One CI run, on the entity of the given level. The name of the build is stable, so that a second run finds it.
     */
    private fun ci(
        level: String,
        propertyLine: String,
        env: Map<String, String> = envWithoutVersion,
        code: (ProjectEntity) -> Unit = {},
    ) {
        val yaml = """
            version: v1
            configuration:
              defaults:
                $level:
                  ${if (level == "build") "buildName: stable" else ""}
                  properties:
                    $propertyLine
        """.trimIndent()
        when (level) {
            "project" -> configTestSupport.withConfigAndProject(projectName = configuredProjectName, env = env, yaml = yaml) { project, _ ->
                code(project)
            }

            "branch" -> configTestSupport.withConfigAndBranch(projectName = configuredProjectName, scmBranch = BRANCH, env = env, yaml = yaml) { branch, _ ->
                code(branch)
            }

            "build" -> configTestSupport.withConfigAndBuild(projectName = configuredProjectName, scmBranch = BRANCH, env = env, yaml = yaml) { build, _ ->
                code(build)
            }

            else -> fail("Unknown level: $level")
        }
    }

    private fun <T> checkDeletion(level: String, value: String, deletion: String, type: Class<out PropertyType<T>>) {
        ci(level, value) { entity ->
            assertNotNull(propertyService.getPropertyValue(entity, type), "The property is set")
        }
        ci(level, deletion) { entity ->
            assertNull(propertyService.getPropertyValue(entity, type), "The property is deleted")
        }
    }

    private fun deleteEvents(entity: ProjectEntity) =
        eventQueryService.getEvents(entity.projectEntityType, entity.id, EventFactory.PROPERTY_DELETE, 0, 10)

    @Test
    @AsAdminTest
    fun `Deleting on the project with the alias`() {
        checkDeletion("project", "notGrantedMedals: true", "notGrantedMedals: null", NotGrantedMedalsPropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Deleting on the project with the class name`() {
        checkDeletion("project", "$medalsClassName: {enabled: true}", "$medalsClassName: null", NotGrantedMedalsPropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Deleting on the branch with the alias`() {
        checkDeletion("branch", "notGrantedMedals: false", "notGrantedMedals: null", NotGrantedMedalsPropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Deleting on the branch with the class name`() {
        checkDeletion("branch", "$medalsClassName: {enabled: false}", "$medalsClassName: null", NotGrantedMedalsPropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Deleting on the build with the alias`() {
        checkDeletion("build", "release: 1.0.0", "release: null", ReleasePropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Deleting on the build with the class name`() {
        checkDeletion("build", "$releaseClassName: {name: 1.0.0}", "$releaseClassName: null", ReleasePropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `A null on an absent property does nothing and posts no event`() {
        ci("branch", "notGrantedMedals: null") { branch ->
            assertNull(propertyService.getPropertyValue(branch, NotGrantedMedalsPropertyType::class.java))
            assertEquals(emptyList(), deleteEvents(branch))
        }
    }

    @Test
    @AsAdminTest
    fun `A null left in the YAML posts one event, on the run which actually deletes`() {
        ci("branch", "notGrantedMedals: true")
        ci("branch", "notGrantedMedals: null") { branch ->
            assertEquals(1, deleteEvents(branch).size)
        }
        ci("branch", "notGrantedMedals: null") { branch ->
            assertEquals(1, deleteEvents(branch).size)
        }
    }

    @Test
    @AsAdminTest
    fun `With no key, a value edited through the API survives a CI run`() {
        val unrelatedProperty = "useLabel: true"
        ci("project", unrelatedProperty) { project ->
            propertyService.editProperty(
                project,
                NotGrantedMedalsPropertyType::class.java,
                NotGrantedMedalsProperty(enabled = true),
            )
        }
        ci("project", unrelatedProperty) { project ->
            assertEquals(
                NotGrantedMedalsProperty(enabled = true),
                propertyService.getPropertyValue(project, NotGrantedMedalsPropertyType::class.java),
            )
        }
    }

    @Test
    @AsAdminTest
    fun `With a VERSION, the release of a build is set again after its deletion`() {
        val env = EnvFixtures.generic(configuredProjectName, BRANCH)
        ci("build", "release: 1.0.0", env)
        ci("build", "release: null", env) { build ->
            assertEquals(
                env.getValue("VERSION"),
                propertyService.getPropertyValue(build, ReleasePropertyType::class.java)?.name,
            )
            assertEquals(1, deleteEvents(build).size, "The release was deleted first")
        }
    }

    private fun yamlWithCustom(default: String, custom: String) = """
        version: v1
        configuration:
          defaults:
            branch:
              properties:
                notGrantedMedals: $default
          custom:
            configs:
              - conditions:
                  - name: branch
                    config: release.*
                branch:
                  properties:
                    notGrantedMedals: $custom
    """.trimIndent()

    private fun ciWithCustom(default: String, custom: String, code: (ProjectEntity) -> Unit) {
        configTestSupport.withConfigAndBranch(
            projectName = configuredProjectName,
            scmBranch = BRANCH,
            env = envWithoutVersion,
            yaml = yamlWithCustom(default = default, custom = custom),
        ) { branch, _ ->
            code(branch)
        }
    }

    @Test
    @AsAdminTest
    fun `A custom null cancels a default value and deletes the property`() {
        ci("branch", "notGrantedMedals: true")
        ciWithCustom(default = "true", custom = "null") { branch ->
            assertNull(propertyService.getPropertyValue(branch, NotGrantedMedalsPropertyType::class.java))
        }
    }

    @Test
    @AsAdminTest
    fun `A custom null cancels a default value which is never written`() {
        ciWithCustom(default = "true", custom = "null") { branch ->
            assertNull(propertyService.getPropertyValue(branch, NotGrantedMedalsPropertyType::class.java))
            assertEquals(emptyList(), deleteEvents(branch), "The default value was not set, then deleted")
        }
    }

    @Test
    @AsAdminTest
    fun `A custom value overrides a default null, which never deletes`() {
        ci("branch", "notGrantedMedals: false")
        ciWithCustom(default = "null", custom = "true") { branch ->
            assertEquals(
                NotGrantedMedalsProperty(enabled = true),
                propertyService.getPropertyValue(branch, NotGrantedMedalsPropertyType::class.java),
            )
            assertEquals(emptyList(), deleteEvents(branch), "The value was not deleted, then set")
        }
    }

    companion object {
        private const val BRANCH = "release/5.1"
    }

}
