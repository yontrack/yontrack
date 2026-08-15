package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.withMockScmRepository
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import org.junit.jupiter.api.Test

class ACCAutoVersioningYAML : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Auto versioning in YAML using a regular expression in a field`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("requirements.yaml") {
                    """
                        apiVersion: apps/v1
                        kind: Deployment
                        metadata:
                          name: dependency
                        spec:
                          template:
                            spec:
                              containers:
                                - name: dependency
                                  image: nemerosa/dependency:0.1.1
                                  command: ['run']
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
                                    targetPath = "requirements.yaml",
                                    targetProperty = "#root.^[kind == 'Deployment' and metadata.name == 'dependency'].spec.template.spec.containers.^[name == 'dependency'].image",
                                    targetPropertyRegex = """^nemerosa\/dependency:(.*)$""",
                                    targetPropertyType = "yaml",
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "0.1.2") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        assertThatMockScmRepository {
                            hasPR(
                                from = "feature/auto-upgrade-${dependency.project.name}-0.1.2-*",
                                to = "main"
                            )
                            fileContains("requirements.yaml") {
                                """
                                    apiVersion: "apps/v1"
                                    kind: "Deployment"
                                    metadata:
                                      name: "dependency"
                                    spec:
                                      template:
                                        spec:
                                          containers:
                                          - name: "dependency"
                                            image: "nemerosa/dependency:0.1.2"
                                            command:
                                            - "run"
                                """.trimIndent()
                            }
                        }

                    }
                }
            }
        }
    }

    @Test
    fun `Auto versioning in YAML using a direct version`() {
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("requirements.yaml") {
                    """
                        apiVersion: argoproj.io/v1alpha1
                        kind: Application
                        metadata:
                          name: dependency
                        spec:
                          source:
                            chart: dependency
                            targetRevision: 0.1.1
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
                                    targetPath = "requirements.yaml",
                                    targetProperty = "[0].spec.source.targetRevision",
                                    targetPropertyType = "yaml",
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "0.1.2") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        assertThatMockScmRepository {
                            hasPR(
                                from = "feature/auto-upgrade-${dependency.project.name}-0.1.2-*",
                                to = "main"
                            )
                            fileContains("requirements.yaml") {
                                """
                                    apiVersion: "argoproj.io/v1alpha1"
                                    kind: "Application"
                                    metadata:
                                      name: "dependency"
                                    spec:
                                      source:
                                        chart: "dependency"
                                        targetRevision: "0.1.2"
                                """.trimIndent()
                            }
                        }

                    }
                }
            }
        }
    }

}