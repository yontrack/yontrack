package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.general.releaseProperty
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class AutoVersioningTemplatingServiceIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningTemplatingService: AutoVersioningTemplatingService

    @BeforeEach
    fun init() {
        ontrackConfigProperties.templating.errors = OntrackConfigProperties.TemplatingErrors.LOGGING_STACK
    }

    @AfterEach
    fun tearDown() {
        ontrackConfigProperties.templating.errors = OntrackConfigProperties.TemplatingErrors.IGNORE
    }

    @Test
    fun `Generating a default PR title and body`() {
        asAdmin {
            project {
                val source = this
                project {
                    branch {
                        val order = createOrder(
                            sourceProject = source.name,
                            targetVersion = "2.0.0",
                            prTitleTemplate = null,
                            prBodyTemplate = null,
                        )

                        val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                            order = order,
                            currentVersions = mapOf("gradle.properties" to "2.0.0")
                        )

                        val (title, body) = autoVersioningTemplatingService.generatePRInfo(
                            order = order,
                            avRenderer = avRenderer,
                        )

                        assertEquals(
                            "[auto-versioning] Upgrade of ${source.project.name} to version 2.0.0",
                            title
                        )

                        assertEquals(
                            "[auto-versioning] Upgrade of ${source.project.name} to version 2.0.0",
                            body
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a custom PR title and body`() {
        asAdmin {
            project {
                branch {
                    build {
                        releaseProperty(this, "2.0.0")
                        val source = this
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = source.project.name,
                                    sourceBuildId = source.id(),
                                    targetVersion = "2.0.0",
                                    prTitleTemplate = "Version of \${sourceProject} upgraded to \${VERSION}",
                                    prBodyTemplate = "The version of \${sourceProject} in \${PATH} has been upgraded to \${VERSION}.",
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (title, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    "Version of ${source.project.name} upgraded to 2.0.0",
                                    title
                                )

                                assertEquals(
                                    "The version of ${source.project.name} in gradle.properties has been upgraded to 2.0.0.",
                                    body
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a custom PR title from the promotion run description`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    build {
                        releaseProperty(this, "2.0.0")
                        val source = this
                        val sourceRun = promote(pl, description = "Custom description")
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = source.project.name,
                                    sourceBuildId = source.id(),
                                    sourcePromotionRunId = sourceRun.id(),
                                    targetVersion = "2.0.0",
                                    prTitleTemplate = "Description: \${sourcePromotionRun.description}",
                                    prBodyTemplate = "The version of \${sourceProject} in \${PATH} has been upgraded to \${VERSION}.",
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (title, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    "Description: Custom description",
                                    title
                                )

                                assertEquals(
                                    "The version of ${source.project.name} in gradle.properties has been upgraded to 2.0.0.",
                                    body
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a custom PR title and body using HTML`() {
        asAdmin {
            project {
                branch {
                    build {
                        releaseProperty(this, "2.0.0")
                        val source = this
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = source.project.name,
                                    sourceBuildId = source.id(),
                                    targetVersion = "2.0.0",
                                    prTitleTemplate = "Version of \${sourceProject} upgraded to \${VERSION}",
                                    prBodyTemplate = "The version of \${sourceProject} in \${PATH} has been upgraded to \${VERSION}.",
                                    prBodyTemplateFormat = "html",
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (title, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    "Version of ${source.project.name} upgraded to 2.0.0",
                                    title
                                )

                                assertEquals(
                                    """The version of <a href="http://localhost:3000/project/${source.project.id}">${source.project.name}</a> in gradle.properties has been upgraded to 2.0.0.""",
                                    body
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a PR body with a change log`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()

                        build {}
                        /* val from = */ build {
                            // Links this build to the expected current version
                            releaseProperty(this, "1.0.0")
                            // Mock termination commit
                            repositoryIssue("ISS-20", "Last issue before the change log", type = "defect")
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build {
                            repositoryIssue("ISS-21", "Some new feature", type = "feature")
                            withRepositoryCommit("ISS-21 Some commits for a feature", property = false)
                            withRepositoryCommit("ISS-21 Some fixes for a feature")
                        }
                        build {
                            repositoryIssue("ISS-22", "Some fixes are needed", type = "defect")
                            withRepositoryCommit("ISS-22 Fixing some bugs")
                        }
                        val to = build {
                            releaseProperty(this, "2.0.0")
                            repositoryIssue("ISS-23", "Some nicer UI", type = "enhancement")
                            withRepositoryCommit("ISS-23 Fixing some CSS")
                        }

                        // Target of the auto-versioning
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = to.project.name,
                                    sourceBuildId = to.id(),
                                    targetVersion = "2.0.0",
                                    prTitleTemplate = "Version of \${sourceProject} upgraded to \${VERSION}",
                                    prBodyTemplate = """
                                        The version of ${"$"}{sourceProject} in ${"$"}{PATH} has been upgraded to ${"$"}{VERSION}.
                                        
                                        ${'$'}{av.changelog}
                                    """.trimIndent(),
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (title, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    "Version of ${to.project.name} upgraded to 2.0.0",
                                    title
                                )

                                assertEquals(
                                    """
                                        The version of ${to.project.name} in gradle.properties has been upgraded to 2.0.0.
                                        
                                        * ISS-21 Some new feature
                                        * ISS-22 Some fixes are needed
                                        * ISS-23 Some nicer UI
                                    """.trimIndent(),
                                    body
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a PR body with a change log when there is no source promotion`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()

                        build {}
                        /* val from = */ build {
                            // Links this build to the expected current version
                            releaseProperty(this, "1.0.0")
                            // Mock termination commit
                            repositoryIssue("ISS-20", "Last issue before the change log", type = "defect")
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build {
                            repositoryIssue("ISS-21", "Some new feature", type = "feature")
                            withRepositoryCommit("ISS-21 Some commits for a feature")
                        }
                        val to = build {
                            releaseProperty(this, "2.0.0")
                            repositoryIssue("ISS-22", "Some fixes are needed", type = "defect")
                            withRepositoryCommit("ISS-22 Fixing some bugs")
                        }

                        // Target of the auto-versioning
                        project {
                            branch {
                                // No source promotion: this is the order a deployment workflow node creates
                                val order = createOrder(
                                    sourceProject = to.project.name,
                                    sourceBuildId = to.id(),
                                    sourcePromotion = null,
                                    targetVersion = "2.0.0",
                                    prBodyTemplate = "${'$'}{av.changelog}",
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (_, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    """
                                        * ISS-21 Some new feature
                                        * ISS-22 Some fixes are needed
                                    """.trimIndent(),
                                    body.trim()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a PR body with a semantic change log`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()

                        build {}
                        /* val from = */ build {
                            releaseProperty(this, "1.0.0")
                            repositoryIssue("ISS-20", "Last issue before the change log", type = "defect")
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build {
                            repositoryIssue("ISS-21", "Some new feature", type = "feature")
                            withRepositoryCommit("feat(UI): Some commits for a feature ISS-21")
                        }
                        val to = build {
                            releaseProperty(this, "2.0.0")
                            repositoryIssue("ISS-22", "Some fixes are needed", type = "defect")
                            withRepositoryCommit("fix: Fixing some bugs ISS-22")
                        }

                        // Target of the auto-versioning
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = to.project.name,
                                    sourceBuildId = to.id(),
                                    sourcePromotion = null,
                                    targetVersion = "2.0.0",
                                    prBodyTemplate = "${'$'}{av.semanticChangelog?issues=true&emojis=true}",
                                )

                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0")
                                )

                                val (_, body) = autoVersioningTemplatingService.generatePRInfo(
                                    order = order,
                                    avRenderer = avRenderer,
                                )

                                assertEquals(
                                    """
                                        📋 Issues:

                                        * ISS-21 Some new feature
                                        * ISS-22 Some fixes are needed

                                        ✨ Features:

                                        * UI - Some commits for a feature ISS-21

                                        🐛 Fixes:

                                        * Fixing some bugs ISS-22
                                    """.trimIndent(),
                                    body.trim()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
