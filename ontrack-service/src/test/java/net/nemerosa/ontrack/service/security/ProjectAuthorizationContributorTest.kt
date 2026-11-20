package net.nemerosa.ontrack.service.security

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.security.ProjectAuthorisationMgt
import net.nemerosa.ontrack.model.security.ProjectFunction
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectAuthorizationContributorTest {

    /**
     * The project permissions page (#2164) is reached from a command shown only to the
     * holders of `ProjectAuthorisationMgt` on the project.
     */
    @Test
    fun `Managing the permissions of the project`() {
        listOf(true, false).forEach { granted ->
            val securityService = mockk<SecurityService>()
            every {
                securityService.isProjectFunctionGranted(any<ProjectEntity>(), any<Class<out ProjectFunction>>())
            } returns false
            every {
                securityService.isProjectFunctionGranted(any<ProjectEntity>(), ProjectAuthorisationMgt::class.java)
            } returns granted
            val contributor = ProjectAuthorizationContributor(securityService)

            val authorizations = contributor.getAuthorizations(mockk(), ProjectFixtures.testProject())

            assertNotNull(authorizations.firstOrNull { it.name == "project" && it.action == "permissions" }) {
                assertEquals(granted, it.authorized)
            }
        }
    }

}
