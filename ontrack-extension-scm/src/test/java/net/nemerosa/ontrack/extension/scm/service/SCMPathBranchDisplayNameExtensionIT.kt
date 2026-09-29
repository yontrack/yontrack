package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.BranchDisplayNameService
import net.nemerosa.ontrack.model.structure.BranchNamePolicy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The SCM branch of a branch is its display name.
 */
@AsAdminTest
class SCMPathBranchDisplayNameExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var branchDisplayNameService: BranchDisplayNameService

    @Test
    fun `Using the SCM branch as display name`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch("release-1.0") {
                    configureMockSCMBranch("release/1.0")
                    assertEquals(
                        "release/1.0",
                        branchDisplayNameService.getBranchDisplayName(this, BranchNamePolicy.DISPLAY_NAME_OR_NAME)
                    )
                }
            }
        }
    }

    @Test
    fun `Branch name when no SCM`() {
        project {
            branch("release-1.0") {
                assertEquals(
                    "release-1.0",
                    branchDisplayNameService.getBranchDisplayName(this, BranchNamePolicy.DISPLAY_NAME_OR_NAME)
                )
            }
        }
    }

}
