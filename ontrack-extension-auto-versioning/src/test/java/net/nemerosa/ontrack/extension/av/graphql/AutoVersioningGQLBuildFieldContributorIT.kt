package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoVersioningGQLBuildFieldContributorIT : AbstractAutoVersioningTestSupport() {

    @Test
    fun `Get AV status between two builds`() {
        asAdmin {
            val dependencyBuild = project<Build> {
                branch<Build>("release/1.1") {
                    build("1")
                }
            }
            val parentBuild = project<Build> {
                branch<Build> {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependencyBuild.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                    build("parent-1")
                }
            }
            run(
                """
                {
                    build(id: ${dependencyBuild.id}) {
                        autoVersioning(buildId: ${parentBuild.id}) {
                            config {
                                sourceProject
                                sourceBranch
                            }
                        }
                    }
                }
            """
            ) { data ->
                assertJsonNotNull(
                    data.path("build").path("autoVersioning"),
                    "AV config found"
                ) {
                    assertEquals(dependencyBuild.project.name, path("config").path("sourceProject").asText())
                    assertEquals("release/1.1", path("config").path("sourceBranch").asText())
                }
            }
        }
    }

    @Test
    fun `Get AV status between one build and a branch`() {
        asAdmin {
            val dependencyBuild = project<Build> {
                branch<Build>("release/1.1") {
                    build("1")
                }
            }
            val parentBuild = project<Build> {
                branch<Build> {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependencyBuild.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                    build("parent-1")
                }
            }
            run(
                """
                {
                    build(id: ${dependencyBuild.id}) {
                        autoVersioning(branchId: ${parentBuild.branch.id}) {
                            config {
                                sourceProject
                                sourceBranch
                            }
                        }
                    }
                }
            """
            ) { data ->
                assertJsonNotNull(
                    data.path("build").path("autoVersioning"),
                    "AV config found"
                ) {
                    assertEquals(dependencyBuild.project.name, path("config").path("sourceProject").asText())
                    assertEquals("release/1.1", path("config").path("sourceBranch").asText())
                }
            }
        }
    }

    @Test
    fun `Get AV status between two builds using the UP direction`() {
        asAdmin {
            val dependencyBuild = project<Build> {
                branch<Build>("release/1.1") {
                    build("1")
                }
            }
            val parentBuild = project<Build> {
                branch<Build> {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependencyBuild.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                    build("parent-1")
                }
            }
            run(
                """
                {
                    build(id: ${parentBuild.id}) {
                        autoVersioning(buildId: ${dependencyBuild.id}, direction: UP) {
                            config {
                                sourceProject
                                sourceBranch
                            }
                        }
                    }
                }
            """
            ) { data ->
                assertJsonNotNull(
                    data.path("build").path("autoVersioning"),
                    "AV config found"
                ) {
                    assertEquals(dependencyBuild.project.name, path("config").path("sourceProject").asText())
                    assertEquals("release/1.1", path("config").path("sourceBranch").asText())
                }
            }
        }
    }

    @Test
    fun `Get AV status between one build and a branch using the UP direction`() {
        asAdmin {
            val dependencyBuild = project<Build> {
                branch<Build>("release/1.1") {
                    build("1")
                }
            }
            val parentBuild = project<Build> {
                branch<Build> {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependencyBuild.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                    build("parent-1")
                }
            }
            run(
                """
                {
                    build(id: ${parentBuild.id}) {
                        autoVersioning(branchId: ${dependencyBuild.branch.id}, direction: UP) {
                            config {
                                sourceProject
                                sourceBranch
                            }
                        }
                    }
                }
            """
            ) { data ->
                assertJsonNotNull(
                    data.path("build").path("autoVersioning"),
                    "AV config found"
                ) {
                    assertEquals(dependencyBuild.project.name, path("config").path("sourceProject").asText())
                    assertEquals("release/1.1", path("config").path("sourceBranch").asText())
                }
            }
        }
    }

    @Test
    fun `The reference is the consumer when asking for the CONSUMER role`() {
        withAutoVersioning { dependency, consumer ->
            assertConfigFound(
                """{ build(id: ${dependency.id}) { autoVersioning(buildId: ${consumer.id}, refRole: CONSUMER) { $CONFIG } } }""",
                dependency,
            )
            assertConfigFound(
                """{ build(id: ${dependency.id}) { autoVersioning(branchId: ${consumer.branch.id}, refRole: CONSUMER) { $CONFIG } } }""",
                dependency,
            )
        }
    }

    @Test
    fun `The reference is the dependency when asking for the DEPENDENCY role`() {
        withAutoVersioning { dependency, consumer ->
            assertConfigFound(
                """{ build(id: ${consumer.id}) { autoVersioning(buildId: ${dependency.id}, refRole: DEPENDENCY) { $CONFIG } } }""",
                dependency,
            )
            assertConfigFound(
                """{ build(id: ${consumer.id}) { autoVersioning(branchId: ${dependency.branch.id}, refRole: DEPENDENCY) { $CONFIG } } }""",
                dependency,
            )
        }
    }

    @Test
    fun `The role must match what the reference is`() {
        withAutoVersioning { dependency, consumer ->
            assertNoConfig(
                """{ build(id: ${dependency.id}) { autoVersioning(buildId: ${consumer.id}, refRole: DEPENDENCY) { $CONFIG } } }"""
            )
            assertNoConfig(
                """{ build(id: ${consumer.id}) { autoVersioning(buildId: ${dependency.id}, refRole: CONSUMER) { $CONFIG } } }"""
            )
        }
    }

    @Test
    fun `The role is available on a branch`() {
        withAutoVersioning { dependency, consumer ->
            val data = run(
                """{ branch(id: ${consumer.branch.id}) { autoVersioning(branchId: ${dependency.branch.id}, refRole: DEPENDENCY) { $CONFIG } } }"""
            )
            assertEquals(
                dependency.project.name,
                data.path("branch").path("autoVersioning").path("config").path("sourceProject").asText(),
            )
        }
    }

    @Test
    fun `The direction still works, and means the role it maps to`() {
        withAutoVersioning { dependency, consumer ->
            assertConfigFound(
                """{ build(id: ${dependency.id}) { autoVersioning(buildId: ${consumer.id}, direction: DOWN) { $CONFIG } } }""",
                dependency,
            )
            assertNoConfig(
                """{ build(id: ${dependency.id}) { autoVersioning(buildId: ${consumer.id}, direction: UP) { $CONFIG } } }"""
            )
        }
    }

    @Test
    fun `The role takes precedence on the direction`() {
        withAutoVersioning { dependency, consumer ->
            assertConfigFound(
                """{ build(id: ${dependency.id}) { autoVersioning(buildId: ${consumer.id}, refRole: CONSUMER, direction: UP) { $CONFIG } } }""",
                dependency,
            )
        }
    }

    private fun withAutoVersioning(code: (dependency: Build, consumer: Build) -> Unit) {
        asAdmin {
            val dependency = project<Build> {
                branch<Build>("release/1.1") {
                    build("1")
                }
            }
            val consumer = project<Build> {
                branch<Build> {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                    build("consumer-1")
                }
            }
            code(dependency, consumer)
        }
    }

    private fun assertConfigFound(query: String, dependency: Build) {
        run(query) { data ->
            assertJsonNotNull(
                data.path("build").path("autoVersioning"),
                "AV config found"
            ) {
                assertEquals(dependency.project.name, path("config").path("sourceProject").asText())
                assertEquals("release/1.1", path("config").path("sourceBranch").asText())
            }
        }
    }

    private fun assertNoConfig(query: String) {
        run(query) { data ->
            assertTrue(data.path("build").path("autoVersioning").isNull, "No AV config this way")
        }
    }

    companion object {
        private const val CONFIG = "config { sourceProject sourceBranch }"
    }

}
