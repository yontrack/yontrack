package net.nemerosa.ontrack.extension.gitlab.property

import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BuildGitLabPipelineRunPropertyMutationProviderIT : AbstractGitLabTestSupport() {

    @Test
    fun `Setting the GitLab pipeline on a build`() {
        asAdmin {
            project {
                branch {
                    build {
                        run(
                            """
                                mutation {
                                    setBuildGitLabPipelineRunPropertyById(input: {
                                        id: $id,
                                        projectPath: "nemerosa/tools/yontrack",
                                        pipelineId: 2000000000,
                                        pipelineIid: 42,
                                        url: "https://gitlab.com/nemerosa/tools/yontrack/-/pipelines/2000000000"
                                    }) {
                                        build {
                                            id
                                        }
                                        errors {
                                            message
                                        }
                                    }
                                }
                            """
                        ).let { data ->
                            val node = assertNoUserError(data, "setBuildGitLabPipelineRunPropertyById")
                            assertEquals(id(), node.path("build").path("id").asInt())
                            assertNotNull(
                                getProperty(this, BuildGitLabPipelineRunPropertyType::class.java)
                            ) { property ->
                                assertEquals(
                                    BuildGitLabPipelineRunProperty(
                                        projectPath = "nemerosa/tools/yontrack",
                                        pipelineId = 2_000_000_000L,
                                        pipelineIid = 42,
                                        url = "https://gitlab.com/nemerosa/tools/yontrack/-/pipelines/2000000000",
                                    ),
                                    property
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
