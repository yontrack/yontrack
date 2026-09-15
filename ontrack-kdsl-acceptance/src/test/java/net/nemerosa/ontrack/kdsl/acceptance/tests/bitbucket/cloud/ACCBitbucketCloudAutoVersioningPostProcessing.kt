package net.nemerosa.ontrack.kdsl.acceptance.tests.bitbucket.cloud

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.av.AbstractACCAutoVersioningTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.assertThatMockScmRepository
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.bitbucketCloud
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.mock.mock
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `bitbucket-cloud` post-processing, through its `mock-bitbucket-cloud` twin: same configuration, settings and
 * variables, but the pipeline is only recorded.
 */
class ACCBitbucketCloudAutoVersioningPostProcessing : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Auto-versioning post-processing through a Bitbucket pipeline`() {
        val config = uid("bbc_")
        ontrack.configurations.bitbucketCloud.create(
            BitbucketCloudConfiguration(
                name = config,
                authType = BitbucketCloudConfiguration.ACCESS_TOKEN,
                token = "any",
            )
        )
        val repository = uid("repo_")

        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "RELEASE")
                project {
                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "RELEASE",
                                    targetPath = "gradle.properties",
                                    targetProperty = "some-version",
                                    postProcessing = "mock-bitbucket-cloud",
                                    postProcessingConfig = mapOf(
                                        "dockerImage" to "eclipse-temurin:21",
                                        "dockerCommand" to "./gradlew dependencies --write-locks",
                                        "commitMessage" to "Locks for ${'$'}{PROMOTION}",
                                        "config" to config,
                                        "workspace" to "ws",
                                        "repository" to repository,
                                        "pipeline" to "yontrack-auto-versioning",
                                        "branch" to "develop",
                                    ).asJson(),
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "2.0.0") {
                                promote("RELEASE")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        assertThatMockScmRepository {
                            fileContains("gradle.properties") {
                                "some-version = 2.0.0"
                            }
                        }

                        val run = ontrack.bitbucketCloud.mock.pipelineRuns(config, "ws", repository).single()
                        assertEquals("develop", run.branch)
                        assertEquals("yontrack-auto-versioning", run.pipeline)
                        assertEquals("eclipse-temurin:21", run.variables["DOCKER_IMAGE"])
                        assertEquals("./gradlew dependencies --write-locks", run.variables["DOCKER_COMMAND"])
                        assertEquals("Locks for RELEASE", run.variables["COMMIT_MESSAGE"])
                        assertEquals("2.0.0", run.variables["VERSION"])
                        assertTrue(run.variables["UPGRADE_BRANCH"].isNullOrBlank().not(), "Upgrade branch is sent")
                        assertTrue(run.variables["REPOSITORY"].isNullOrBlank().not(), "Repository is sent")
                    }
                }
            }
        }
    }

}
