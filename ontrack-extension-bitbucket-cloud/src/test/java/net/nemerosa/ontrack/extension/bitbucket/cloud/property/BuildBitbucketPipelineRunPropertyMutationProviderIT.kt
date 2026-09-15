package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BuildBitbucketPipelineRunPropertyMutationProviderIT : AbstractBitbucketCloudTestSupport() {

    @Test
    fun `Setting the Bitbucket Pipelines run on a build`() {
        asAdmin {
            project {
                branch {
                    build {
                        run(
                            """
                                mutation {
                                    setBuildBitbucketPipelineRunPropertyById(input: {
                                        id: $id,
                                        workspace: "my-workspace",
                                        repository: "my-repository",
                                        buildNumber: 42,
                                        uuid: "{abc}"
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
                            val node = assertNoUserError(data, "setBuildBitbucketPipelineRunPropertyById")
                            assertEquals(id(), node.path("build").path("id").asInt())
                            assertNotNull(
                                getProperty(this, BuildBitbucketPipelineRunPropertyType::class.java)
                            ) { property ->
                                assertEquals(
                                    BuildBitbucketPipelineRunProperty(
                                        workspace = "my-workspace",
                                        repository = "my-repository",
                                        buildNumber = 42,
                                        uuid = "{abc}",
                                        url = "https://bitbucket.org/my-workspace/my-repository/pipelines/results/42",
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
