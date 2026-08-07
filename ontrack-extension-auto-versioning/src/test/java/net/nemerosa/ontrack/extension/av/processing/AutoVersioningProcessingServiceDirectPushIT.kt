package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Testing the direct push mode of the auto-versioning, and in particular the cleanup
 * of the intermediate upgrade branch.
 */
class AutoVersioningProcessingServiceDirectPushIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    @Test
    fun `Upgrade branch is deleted after a direct push`() {
        asAdmin {
            project {
                val source = this
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            repositoryFile(
                                path = "gradle.properties",
                                content = "version = 1.0.0",
                            )

                            val order = createOrder(
                                sourceProject = source.name,
                                targetVersion = "2.0.0",
                                upgradeBranchPattern = "feature/version-<version>",
                                pushMode = AutoVersioningPushMode.PUSH,
                            )

                            val outcome = autoVersioningProcessingService.process(order)
                            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                            assertEquals(
                                "version = 2.0.0",
                                getRepositoryFile(path = "gradle.properties"),
                                "Version has been pushed to the target branch"
                            )

                            assertNull(
                                getRepositoryBranch("feature/version-2.0.0-*"),
                                "Upgrade branch has been deleted after the push"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Upgrade branch is kept in PR mode`() {
        asAdmin {
            project {
                val source = this
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            repositoryFile(
                                path = "gradle.properties",
                                content = "version = 1.0.0",
                            )

                            val order = createOrder(
                                sourceProject = source.name,
                                targetVersion = "2.0.0",
                                upgradeBranchPattern = "feature/version-<version>",
                                pushMode = AutoVersioningPushMode.PR,
                            )

                            val outcome = autoVersioningProcessingService.process(order)
                            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                            assertNotNull(
                                getRepositoryBranch("feature/version-2.0.0-*"),
                                "Upgrade branch is kept as the head of the pull request"
                            )
                        }
                    }
                }
            }
        }
    }

}
