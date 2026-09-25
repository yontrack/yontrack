package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@AsAdminTest
class SCMCommitTemplatingSourceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var templatingService: TemplatingService

    @Test
    fun `Rendering the SCM commit of a build`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()
                    build {
                        val commit = withRepositoryCommit("Commit to promote")

                        val text = templatingService.render(
                            template = "Deploying ${'$'}{build.scmCommit}",
                            context = mapOf("build" to this),
                            renderer = PlainEventRenderer.INSTANCE,
                        )

                        assertEquals("Deploying $commit", text)
                    }
                }
            }
        }
    }

    @Test
    fun `Rendering the SCM commit of a build without a commit`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()
                    build {
                        val text = templatingService.render(
                            template = "Deploying ${'$'}{build.scmCommit}",
                            context = mapOf("build" to this),
                            renderer = PlainEventRenderer.INSTANCE,
                        )

                        assertEquals("Deploying ", text)
                    }
                }
            }
        }
    }

}
