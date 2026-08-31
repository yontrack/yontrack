package net.nemerosa.ontrack.extension.config

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.config.ci.CIConfigurationService
import net.nemerosa.ontrack.extension.config.model.CIEnv
import net.nemerosa.ontrack.extension.config.model.EffectiveConfiguration
import net.nemerosa.ontrack.graphql.GraphQLTestSupport
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertNotNull

/**
 * The `projectName` of the helpers below defaults to a value unique to the call rather than to a
 * shared constant, so two tests - or two modules of the same CI shard - never configure the same
 * project row. Pass it explicitly when the test needs the name, for instance to register a mock SCM
 * repository under it. See #1657.
 */
@Component
class ConfigTestSupport(
    private val structureService: StructureService,
    private val graphQLTestSupport: GraphQLTestSupport,
    private val ciConfigurationService: CIConfigurationService,
) {

    fun configureProject(
        yaml: String = DEFAULT_CONFIG,
        ci: String? = null,
        scm: String? = null,
        env: Map<String, String> = emptyMap(),
    ) = ciConfigurationService.configureProject(
        config = yaml,
        ci = ci,
        scm = scm,
        env = env.map { (name, value) -> CIEnv(name, value) },
    )

    fun configureBranch(
        yaml: String = DEFAULT_CONFIG,
        ci: String? = null,
        scm: String? = null,
        env: Map<String, String> = emptyMap(),
    ) = ciConfigurationService.configureBranch(
        config = yaml,
        ci = ci,
        scm = scm,
        env = env.map { (name, value) -> CIEnv(name, value) },
    )

    fun configureBuild(
        yaml: String = DEFAULT_CONFIG,
        ci: String? = null,
        scm: String? = null,
        env: Map<String, String> = emptyMap(),
    ) = ciConfigurationService.configureBuild(
        config = yaml,
        ci = ci,
        scm = scm,
        env = env.map { (name, value) -> CIEnv(name, value) },
    )

    fun withConfig(
        yaml: String,
        ci: String? = DEFAULT_CI,
        scm: String? = DEFAULT_SCM,
        scmBranch: String = "release/5.1",
        projectName: String = uid("cfg-"),
        extraEnv: Map<String, String> = emptyMap(),
        env: Map<String, String> = EnvFixtures.generic(projectName, scmBranch, extraEnv),
        code: (payload: JsonNode) -> Unit,
    ) {
        graphQLTestSupport.run(
            """
                mutation ConfigureBuild(
                    ${'$'}config: String!,
                    ${'$'}ci: String,
                    ${'$'}scm: String,
                    ${'$'}env: [CIEnv!]!,
                ) {
                    configureBuild(input: {
                        config: ${'$'}config,
                        ci: ${'$'}ci,
                        scm: ${'$'}scm,
                        env: ${'$'}env,
                    }) {
                        errors {
                            message
                            exception
                        }
                        build {
                            id
                        }
                    }
                }
            """,
            mapOf(
                "config" to yaml,
                "ci" to ci,
                "scm" to scm,
                "env" to env.map { mapOf("name" to it.key, "value" to it.value) },
            )
        ) { data ->
            graphQLTestSupport.checkGraphQLUserErrors(data, "configureBuild") { payload ->
                code(payload)
            }
        }
    }

    fun withBranchConfig(
        yaml: String,
        ci: String? = DEFAULT_CI,
        scm: String? = DEFAULT_SCM,
        scmBranch: String = "release/5.1",
        projectName: String = uid("cfg-"),
        extraEnv: Map<String, String> = emptyMap(),
        env: Map<String, String> = EnvFixtures.generic(projectName, scmBranch, extraEnv),
        code: (payload: JsonNode) -> Unit,
    ) {
        graphQLTestSupport.run(
            $$"""
                mutation ConfigureBranch(
                    $config: String!,
                    $ci: String,
                    $scm: String,
                    $env: [CIEnv!]!,
                ) {
                    configureBranch(input: {
                        config: $config,
                        ci: $ci,
                        scm: $scm,
                        env: $env,
                    }) {
                        errors {
                            message
                            exception
                        }
                        branch {
                            id
                        }
                    }
                }
            """,
            mapOf(
                "config" to yaml,
                "ci" to ci,
                "scm" to scm,
                "env" to env.map { mapOf("name" to it.key, "value" to it.value) },
            )
        ) { data ->
            graphQLTestSupport.checkGraphQLUserErrors(data, "configureBranch") { payload ->
                code(payload)
            }
        }
    }

    fun withConfigAndProject(
        yaml: String = """
            version: v1
            configuration: {}
        """.trimIndent(),
        ci: String? = DEFAULT_CI,
        scm: String? = DEFAULT_SCM,
        scmBranch: String = "release/5.1",
        projectName: String = uid("cfg-"),
        expectedProjectName: String = projectName,
        extraEnv: Map<String, String> = emptyMap(),
        env: Map<String, String> = EnvFixtures.generic(projectName, scmBranch, extraEnv),
        code: (project: Project, payload: JsonNode) -> Unit,
    ) =
        withConfig(yaml, ci = ci, scm = scm, scmBranch = scmBranch, env = env, extraEnv = extraEnv) { payload ->
            assertNotNull(
                structureService.findProjectByName(expectedProjectName).getOrNull(),
                "Project has been created"
            ) { project ->
                code(project, payload)
            }
        }

    fun withConfigAndBranch(
        yaml: String,
        ci: String? = DEFAULT_CI,
        scm: String? = DEFAULT_SCM,
        scmBranch: String = "release/5.1",
        projectName: String = uid("cfg-"),
        extraEnv: Map<String, String> = emptyMap(),
        env: Map<String, String> = EnvFixtures.generic(projectName, scmBranch, extraEnv),
        branch: String = NameDescription.escapeName(scmBranch),
        expectedProjectName: String = projectName,
        code: (branch: Branch, payload: JsonNode) -> Unit,
    ) =
        withConfigAndProject(
            yaml,
            ci = ci,
            scm = scm,
            scmBranch = scmBranch,
            extraEnv = extraEnv,
            env = env,
            expectedProjectName = expectedProjectName,
        ) { project, payload ->
            // Branch
            assertNotNull(
                structureService.findBranchByName(project.name, branch).getOrNull(),
                "Branch has been created"
            ) { branch ->
                code(branch, payload)
            }
        }

    fun withConfigAndBuild(
        yaml: String,
        ci: String? = DEFAULT_CI,
        scm: String? = DEFAULT_SCM,
        scmBranch: String = "release/5.1",
        projectName: String = uid("cfg-"),
        extraEnv: Map<String, String> = emptyMap(),
        env: Map<String, String> = EnvFixtures.generic(projectName, scmBranch, extraEnv),
        expectedProjectName: String = projectName,
        code: (build: Build, payload: JsonNode) -> Unit,
    ) =
        withConfigAndBranch(
            yaml,
            ci = ci,
            scm = scm,
            scmBranch = scmBranch,
            extraEnv = extraEnv,
            env = env,
            expectedProjectName = expectedProjectName,
        ) { branch, payload ->
            assertNotNull(
                structureService.getLastBuild(branch.id).getOrNull(),
                "Build has been created"
            ) { build ->
                code(build, payload)
            }
        }

    fun graphQLEffectiveConfiguration(
        yaml: String = DEFAULT_CONFIG,
        ci: String? = null,
        scm: String? = null,
        env: Map<String, String> = emptyMap(),
        code: (EffectiveConfiguration) -> Unit,
    ) {
        graphQLTestSupport.run(
            """
                query EffectiveCIConfiguration(
                    ${'$'}config: String!,
                    ${'$'}ci: String,
                    ${'$'}scm: String,
                    ${'$'}env: [CIEnv!]!,
                ) {
                    effectiveCIConfiguration(input: {
                        config: ${'$'}config,
                        ci: ${'$'}ci,
                        scm: ${'$'}scm,
                        env: ${'$'}env,
                    })
                }
            """,
            mapOf(
                "config" to yaml,
                "ci" to ci,
                "scm" to scm,
                "env" to env.map { mapOf("name" to it.key, "value" to it.value) },
            )
        ) { data ->
            val json = data.path("effectiveCIConfiguration")
            code(json.parse())
        }
    }

    companion object {
        const val DEFAULT_CI = "generic"
        const val DEFAULT_SCM = "mock"
        val DEFAULT_CONFIG = """
            version: v1
            configuration: {}
        """.trimIndent()
    }

}