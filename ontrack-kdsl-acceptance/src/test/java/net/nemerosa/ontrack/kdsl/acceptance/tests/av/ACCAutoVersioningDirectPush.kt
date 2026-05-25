package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.withMockScmRepository
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningPushMode
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import org.junit.jupiter.api.Test

class ACCAutoVersioningDirectPush : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Auto versioning on promotion with direct push`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "IRON")
                project {
                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "IRON",
                                    targetPath = "gradle.properties",
                                    targetProperty = "some-version",
                                    pushMode = AutoVersioningPushMode.PUSH,
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "2.0.0") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        assertThatMockScmRepository {
                            hasNoBranch("feature/auto-upgrade-${dependency.project.name}-2.0.0-*")
                            hasNoPR(to = "main")
                            fileContains("gradle.properties", branch = "main") {
                                "some-version = 2.0.0"
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Auto versioning on promotion with direct push and post-processing`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "RELEASE")
                val postProcessingStamp = uid("ps_")
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
                                    postProcessing = "mock",
                                    postProcessingConfig = mapOf(
                                        "postProcessingStamp" to postProcessingStamp,
                                    ).asJson(),
                                    pushMode = AutoVersioningPushMode.PUSH,
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
                            hasNoPR(to = "main")
                            fileContains("gradle.properties", branch = "main") {
                                "some-version = 2.0.0"
                            }
                            fileContains("post-processing.properties", branch = "main") {
                                "postProcessingStamp = $postProcessingStamp"
                            }
                        }
                    }
                }
            }
        }
    }

}
