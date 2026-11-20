package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningController
import net.nemerosa.ontrack.extension.av.validation.AutoVersioningValidationService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Checking the auto-versioning of a build needs `ValidationRunCreate` on the project of the build,
 * checked before anything else.
 */
class AutoVersioningCheckPermissionIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var validationService: AutoVersioningValidationService

    @Autowired
    private lateinit var controller: AutoVersioningController

    /**
     * A build on a branch without any auto-versioning configuration: there is nothing to validate,
     * so the check would succeed but for the permission.
     */
    private fun withBuild(code: (build: Build) -> Unit) {
        asAdmin {
            project {
                branch {
                    code(build())
                }
            }
        }
    }

    private fun assertRefused(code: () -> Unit) {
        val ex = assertFailsWith<AccessDeniedException> { code() }
        assertEquals("Project function 'ValidationRunCreate' is not granted", ex.message)
    }

    @Test
    fun `Check allowed to a controller`() {
        withBuild { build ->
            asAccountWithGlobalRole(Roles.GLOBAL_CONTROLLER) {
                assertTrue(validationService.checkAndValidate(build).isEmpty())
            }
        }
    }

    @Test
    fun `Check allowed to a validation manager of the project`() {
        withBuild { build ->
            build.asAccountWithProjectRole(Roles.PROJECT_VALIDATION_MANAGER) {
                assertTrue(validationService.checkAndValidate(build).isEmpty())
            }
        }
    }

    @Test
    fun `Check allowed to an owner of the project`() {
        withBuild { build ->
            build.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertTrue(validationService.checkAndValidate(build).isEmpty())
            }
        }
    }

    @Test
    fun `Check refused to a participant of the project, even without any configuration`() {
        withBuild { build ->
            build.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                assertRefused { validationService.checkAndValidate(build) }
            }
        }
    }

    @Test
    fun `Check refused to an owner of another project`() {
        withBuild { build ->
            withGrantViewToAll {
                val other = asAdmin { project() }
                other.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                    assertRefused { validationService.checkAndValidate(build) }
                }
            }
        }
    }

    @Test
    fun `REST check allowed to a controller`() {
        withBuild { build ->
            asAccountWithGlobalRole(Roles.GLOBAL_CONTROLLER) {
                assertTrue(controller.autoVersioningCheck(build.id()).isEmpty())
            }
        }
    }

    @Test
    fun `REST check refused to a participant of the project`() {
        withBuild { build ->
            build.asAccountWithProjectRole(Roles.PROJECT_PARTICIPANT) {
                assertRefused { controller.autoVersioningCheck(build.id()) }
            }
        }
    }

}
