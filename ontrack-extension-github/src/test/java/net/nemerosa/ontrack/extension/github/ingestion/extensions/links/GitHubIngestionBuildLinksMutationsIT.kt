package net.nemerosa.ontrack.extension.github.ingestion.extensions.links

import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRun
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunProperty
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunPropertyType
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.BuildLinkForm
import net.nemerosa.ontrack.model.structure.BuildLinkFormItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GitHubIngestionBuildLinksMutationsIT : AbstractIngestionTestSupport() {

    @Test
    fun `Automation users can set the build links`() {
        basicTest { code ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                code()
            }
        }
    }

    @Test
    fun `Build by ID`() {
        basicTest()
    }

    @Test
    fun `Build by ID not found`() {
        basicTest(
            runIdProperty = 10,
            runIdQuery = 11,
            expectedLinks = emptyMap(),
        )
    }

    @Test
    fun `Build by name`() {
        basicTest(buildIdentification = BuildIdentification.BUILD_NAME)
    }

    @Test
    fun `Build by label`() {
        basicTest(buildIdentification = BuildIdentification.BUILD_LABEL)
    }

    @Test
    fun `Links replaced by default`() {
        basicTest(
            existingLinks = setOf("three"),
            expectedLinks = mapOf(
                "one" to "build-one",
                "two" to "build-two",
            ),
        )
    }

    @Test
    fun `Links replaced explicitly`() {
        basicTest(
            existingLinks = setOf("three"),
            expectedLinks = mapOf(
                "one" to "build-one",
                "two" to "build-two",
            ),
            addOnly = false,
        )
    }

    @Test
    fun `Links added only`() {
        basicTest(
            existingLinks = setOf("three"),
            expectedLinks = mapOf(
                "one" to "build-one",
                "two" to "build-two",
                "three" to "build-three",
            ),
            addOnly = true,
        )
    }

    private fun basicTest(
        buildIdentification: BuildIdentification = BuildIdentification.RUN_ID,
        runIdProperty: Long = 10,
        runIdQuery: Long = 10,
        buildName: String = "my-build-name",
        buildLabel: String = "0.0.1",
        addOnly: Boolean? = null,
        existingLinks: Set<String> = emptySet(),
        expectedLinks: Map<String, String> = mapOf(
            "one" to "build-one",
            "two" to "build-two",
        ),
        asAuth: (code: () -> Unit) -> Unit = { code -> asAdmin { code() } },
    ) {

        val mutationName = when (buildIdentification) {
            BuildIdentification.RUN_ID -> "gitHubIngestionBuildLinksByRunId"
            BuildIdentification.BUILD_NAME -> "gitHubIngestionBuildLinksByBuildName"
            BuildIdentification.BUILD_LABEL -> "gitHubIngestionBuildLinksByBuildLabel"
        }

        val mutationParams = when (buildIdentification) {
            BuildIdentification.RUN_ID -> "runId: $runIdQuery,"
            BuildIdentification.BUILD_NAME -> """buildName: "$buildName","""
            BuildIdentification.BUILD_LABEL -> """buildLabel: "$buildLabel","""
        }

        asAdmin {
            withGitHubIngestionSettings {
                val targets = mapOf(
                    "one" to project {
                        branch {
                            build(name = "build-one")
                        }
                    },
                    "two" to project {
                        branch {
                            build(name = "build-two") {
                                setProperty(
                                    this,
                                    ReleasePropertyType::class.java,
                                    ReleaseProperty("2.0.0")
                                )
                            }
                        }
                    },
                    "three" to project {
                        branch {
                            build(name = "build-three") {
                                setProperty(
                                    this,
                                    ReleasePropertyType::class.java,
                                    ReleaseProperty("3.0.0")
                                )
                            }
                        }
                    },
                )
                project {
                    branch {
                        build(buildName) {
                            setProperty(
                                this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = runIdProperty,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            if (buildIdentification == BuildIdentification.BUILD_LABEL) {
                                setProperty(
                                    this,
                                    ReleasePropertyType::class.java,
                                    ReleaseProperty(buildLabel)
                                )
                            }
                            if (existingLinks.isNotEmpty()) {
                                structureService.editBuildLinks(this, BuildLinkForm(
                                    addOnly = false,
                                    links = existingLinks.map {
                                        BuildLinkFormItem(
                                            project = targets[it]?.name ?: error("Cannot find project $it"),
                                            build = "build-$it",
                                            qualifier = "",
                                        )
                                    }
                                ))
                            }
                            asAuth {
                                run(
                                    """
                                    mutation IngestLinks(${'$'}addOnly: Boolean) {
                                        $mutationName(input: {
                                            owner: "nemerosa",
                                            repository: "${project.name}",
                                            $mutationParams
                                            buildLinks: [
                                            {
                                                project: "${targets["one"]?.name}",
                                                buildRef: "build-one"
                                            },
                                            {
                                                project: "${targets["two"]?.name}",
                                                buildRef: "#2.0.0"
                                            }
                                            ],
                                            addOnly: ${'$'}addOnly,
                                        }) {
                                            payload {
                                                uuid
                                            }
                                            errors {
                                                message
                                                exception
                                                location
                                            }
                                        }
                                    }
                                """, mapOf("addOnly" to addOnly)
                                ) { data ->
                                    checkGraphQLUserErrors(data, mutationName) { node ->
                                        val uuid = node.path("payload").getRequiredTextField("uuid")
                                        assertTrue(uuid.isNotBlank(), "UUID has been returned")
                                    }
                                    asAdmin {
                                        val links = structureService.getBuildDependencyLinks(this).pageItems.associate {
                                            it.build.project.name to it.build.name
                                        }
                                        assertEquals(
                                            expectedLinks.mapKeys { (key, _) ->
                                                targets[key]?.name ?: ""
                                            },
                                            links
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    enum class BuildIdentification {
        RUN_ID,
        BUILD_NAME,
        BUILD_LABEL,
    }

}