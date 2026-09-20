package net.nemerosa.ontrack.kdsl.acceptance.tests.gitlab

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.av.AbstractACCAutoVersioningTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.assertThatMockScmRepository
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLab
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.mock.mock
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `gitlab` post-processing, through its `mock-gitlab` twin: same configuration, settings and variables,
 * but the pipeline is only recorded.
 */
class ACCGitLabAutoVersioningPostProcessing : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Auto-versioning post-processing through a GitLab pipeline`() {
        val config = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = config,
                url = "https://gitlab.com",
                token = TOKEN,
            )
        )
        val gitLabProject = "group/${uid("p_")}"

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
                                    postProcessing = "mock-gitlab",
                                    postProcessingConfig = mapOf(
                                        "dockerImage" to "eclipse-temurin:21",
                                        "dockerCommand" to "./gradlew dependencies --write-locks",
                                        "commitMessage" to "Locks for ${'$'}{PROMOTION}",
                                        "config" to config,
                                        "project" to gitLabProject,
                                        "ref" to "develop",
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

                        val run = ontrack.gitLab.mock.pipelineRuns(config, gitLabProject).single()
                        assertEquals(gitLabProject, run.project)
                        assertEquals("develop", run.ref)
                        assertEquals("eclipse-temurin:21", run.variables["DOCKER_IMAGE"])
                        assertEquals("./gradlew dependencies --write-locks", run.variables["DOCKER_COMMAND"])
                        assertEquals("Locks for RELEASE", run.variables["COMMIT_MESSAGE"])
                        assertEquals("2.0.0", run.variables["VERSION"])
                        assertTrue(run.variables["UPGRADE_BRANCH"].isNullOrBlank().not(), "Upgrade branch is sent")
                        assertTrue(run.variables["REPOSITORY"].isNullOrBlank().not(), "Repository is sent")
                        assertTrue(
                            run.variables.values.none { it.contains(TOKEN) },
                            "The GitLab token never reaches a pipeline variable"
                        )
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Distinctive enough that its absence from the pipeline variables means something.
         */
        private const val TOKEN = "glpat-acc-must-not-leak"
    }

}
