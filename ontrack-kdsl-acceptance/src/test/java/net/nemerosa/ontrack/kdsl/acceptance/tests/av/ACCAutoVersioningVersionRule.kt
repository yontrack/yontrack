package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.withMockScmRepository
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.getAutoVersioningConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Acceptance tests for the opt-in version rules, which stop the re-promotion of an old build
 * from rolling the target files backwards.
 */
class ACCAutoVersioningVersionRule : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `A downgrade is rejected and the target file is left alone`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    """
                        some-version = 2.0.0
                    """.trimIndent()
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
                                    versionRule = "semver",
                                )
                            )
                        )

                        // Re-promotion of an older build
                        dependency.apply {
                            build(name = "1.0.0") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        assertThatMockScmRepository {
                            hasNoPR(to = "main")
                            fileContains("gradle.properties") {
                                """
                                    some-version = 2.0.0
                                """.trimIndent()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `An upgrade still goes through with the rule set`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    """
                        some-version = 1.0.0
                    """.trimIndent()
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
                                    versionRule = "semver",
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
                            hasPR(
                                from = "feature/auto-upgrade-${dependency.project.name}-2.0.0-*",
                                to = "main"
                            )
                            fileContains("gradle.properties") {
                                """
                                    some-version = 2.0.0
                                """.trimIndent()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `The version rule is saved and read back`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
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
                                    versionRule = "semver",
                                    versionRuleConfig = mapOf("onUnparseable" to "ACCEPT").asJson(),
                                )
                            )
                        )

                        val config = getAutoVersioningConfig().first()
                        assertEquals("semver", config.versionRule)
                        assertEquals(
                            "ACCEPT",
                            config.versionRuleConfig?.path("onUnparseable")?.asText(),
                        )
                    }
                }
            }
        }
    }

}
